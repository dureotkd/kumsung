package kr.co.kumsungenc.platform.integration;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.RestClient;
import java.net.URI;
import java.util.*;

@Component
@ConditionalOnProperty(name="app.google-sheets.enabled",havingValue="true")
public class GoogleSheetsOutboxWorker {
    private static final Logger log=LoggerFactory.getLogger(GoogleSheetsOutboxWorker.class);
    private final JdbcTemplate jdbc;private final TransactionTemplate transactions;private final RestClient restClient;
    private final ObjectMapper objectMapper;private final String webhookUrl;private final String secret;private final int batchSize;
    private final String workerId=UUID.randomUUID().toString();

    public GoogleSheetsOutboxWorker(JdbcTemplate jdbc,TransactionTemplate transactions,RestClient.Builder builder,
        ObjectMapper objectMapper,@Value("${app.google-sheets.webhook-url:}") String webhookUrl,
        @Value("${app.google-sheets.webhook-secret:}") String secret,
        @Value("${app.google-sheets.batch-size:10}") int batchSize){
        this.jdbc=jdbc;this.transactions=transactions;this.restClient=builder.build();this.objectMapper=objectMapper;
        this.webhookUrl=webhookUrl.trim();this.secret=secret;this.batchSize=Math.max(1,Math.min(batchSize,100));
        URI target;
        try{target=URI.create(this.webhookUrl);}catch(IllegalArgumentException e){throw new IllegalStateException("Google Sheets webhook URL이 올바르지 않습니다.",e);}
        if(!"https".equalsIgnoreCase(target.getScheme())||target.getHost()==null)
            throw new IllegalStateException("Google Sheets webhook은 유효한 HTTPS URL이어야 합니다.");
        if(this.secret.length()<32)throw new IllegalStateException("Google Sheets webhook 비밀값은 32자 이상이어야 합니다.");
    }

    @Scheduled(fixedDelayString="${app.google-sheets.poll-ms:3000}")
    public void processBatch(){
        List<Map<String,Object>> rows=transactions.execute(status -> jdbc.queryForList("""
            with candidates as (
              select id from sheet_outbox
              where (status in ('PENDING','RETRY') and next_attempt_at<=current_timestamp)
                 or (status='PROCESSING' and claimed_at<current_timestamp-interval '10 minutes')
              order by id for update skip locked limit ?
            )
            update sheet_outbox o set status='PROCESSING',claimed_at=current_timestamp,claimed_by=?
            from candidates c where o.id=c.id
            returning o.id,o.event_type,o.reference_type,o.reference_id,o.payload::text as payload,o.attempts
            """,batchSize,workerId));
        if(rows==null)return;
        rows.forEach(this::send);
    }

    private void send(Map<String,Object> row){
        long id=((Number)row.get("id")).longValue();int attempts=((Number)row.get("attempts")).intValue()+1;
        try{
            Map<String,Object> body=objectMapper.readValue((String)row.get("payload"),new TypeReference<>(){});
            body=new LinkedHashMap<>(body);body.put("outboxId",id);body.put("eventType",row.get("event_type"));body.put("secret",secret);
            restClient.post().uri(webhookUrl).contentType(MediaType.APPLICATION_JSON).body(body).retrieve().toBodilessEntity();
            transactions.executeWithoutResult(status -> jdbc.update("""
                update sheet_outbox set status='SENT',attempts=?,sent_at=current_timestamp,last_error=null,
                claimed_at=null,claimed_by=null where id=? and status='PROCESSING' and claimed_by=?
                """,attempts,id,workerId));
        }catch(Exception e){
            String state=attempts>=8?"FAILED":"RETRY";String error=safe(e);
            transactions.executeWithoutResult(status -> jdbc.update("""
                update sheet_outbox set status=?,attempts=?,last_error=?,
                next_attempt_at=current_timestamp+(power(2,?)*interval '1 minute'),claimed_at=null,claimed_by=null
                where id=? and status='PROCESSING' and claimed_by=?
                """,state,attempts,error,Math.min(attempts,8),id,workerId));
            log.warn("Google Sheets Outbox 전송 실패: id={}, attempts={}",id,attempts);
        }
    }

    private String safe(Exception e){String value=Objects.toString(e.getMessage(),"Google Sheets 연결 실패");return value.substring(0,Math.min(2000,value.length()));}
}
