package kr.co.kumsungenc.platform.notification;

import org.slf4j.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.*;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.*;

@Component
@ConditionalOnProperty(name="app.email-outbox.enabled",havingValue="true",matchIfMissing=true)
public class EmailOutboxWorker {
    private static final Logger log=LoggerFactory.getLogger(EmailOutboxWorker.class);
    private final JdbcTemplate jdbc; private final JavaMailSender sender; private final String from;
    private final TransactionTemplate transactions; private final int batchSize;
    private final String workerId=UUID.randomUUID().toString();
    public EmailOutboxWorker(JdbcTemplate jdbc,JavaMailSender sender,TransactionTemplate transactions,
        @Value("${app.mail-from:${spring.mail.username:system@localhost}}") String from,
        @Value("${app.email-outbox.batch-size:10}") int batchSize){
        this.jdbc=jdbc;this.sender=sender;this.transactions=transactions;this.from=from;
        this.batchSize=Math.max(1,Math.min(batchSize,100));
    }
    @Scheduled(fixedDelayString="${app.email-outbox.poll-ms:1000}")
    public void processBatch(){
        List<Map<String,Object>> rows=transactions.execute(status -> jdbc.queryForList("""
            with candidates as (
              select id from email_outbox
              where (
                status in ('PENDING','RETRY') and next_attempt_at<=current_timestamp
              ) or (
                status='PROCESSING' and claimed_at<current_timestamp-interval '10 minutes'
              )
              order by id
              for update skip locked
              limit ?
            )
            update email_outbox o
            set status='PROCESSING',claimed_at=current_timestamp,claimed_by=?
            from candidates c
            where o.id=c.id
            returning o.id,o.quote_request_id,o.recipient,o.subject,o.body,o.attempts
            """,batchSize,workerId));
        if(rows==null||rows.isEmpty())return;
        for(Map<String,Object> row:rows)send(row);
    }
    private void send(Map<String,Object> row){
        long id=((Number)row.get("id")).longValue();
        Long quoteId=row.get("quote_request_id")==null?null:((Number)row.get("quote_request_id")).longValue();
        String recipient=(String)row.get("recipient"),subject=(String)row.get("subject"),body=(String)row.get("body");
        int attempts=((Number)row.get("attempts")).intValue()+1;
        SimpleMailMessage mail=new SimpleMailMessage();mail.setFrom(from);mail.setTo(recipient);mail.setSubject(subject);mail.setText(body);
        try{
            sender.send(mail);
            transactions.executeWithoutResult(status -> {
                int changed=jdbc.update("""
                    update email_outbox set status='SENT',attempts=?,sent_at=current_timestamp,
                    last_error=null,claimed_at=null,claimed_by=null
                    where id=? and status='PROCESSING' and claimed_by=?
                    """,attempts,id,workerId);
                if(changed==1)jdbc.update("insert into email_logs(quote_request_id,recipient,subject,status) values (?,?,?,'SENT')",quoteId,recipient,subject);
            });
        }catch(MailException e){
            String error=safe(e);String status=attempts>=5?"FAILED":"RETRY";
            transactions.executeWithoutResult(tx -> {
                int changed=jdbc.update("""
                    update email_outbox set status=?,attempts=?,last_error=?,
                    next_attempt_at=current_timestamp+(power(2,?)*interval '1 minute'),
                    claimed_at=null,claimed_by=null
                    where id=? and status='PROCESSING' and claimed_by=?
                    """,status,attempts,error,Math.min(attempts,6),id,workerId);
                if(changed==1&&"FAILED".equals(status))
                    jdbc.update("insert into email_logs(quote_request_id,recipient,subject,status,error_message) values (?,?,?,'FAILED',?)",quoteId,recipient,subject,error);
            });
            log.warn("Outbox 이메일 발송 실패: id={}, attempts={}",id,attempts);
        }
    }
    private String safe(Exception e){String s=e.getMessage();if(s==null)return "메일 서버 연결 실패";return s.substring(0,Math.min(s.length(),2000));}
}
