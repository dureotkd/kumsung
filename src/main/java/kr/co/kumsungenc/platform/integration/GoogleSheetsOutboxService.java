package kr.co.kumsungenc.platform.integration;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;
import java.util.Map;

@Service
public class GoogleSheetsOutboxService {
    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final boolean enabled;

    public GoogleSheetsOutboxService(JdbcTemplate jdbc,ObjectMapper objectMapper,
        @Value("${app.google-sheets.enabled:false}") boolean enabled){
        this.jdbc=jdbc;this.objectMapper=objectMapper;this.enabled=enabled;
    }

    public void enqueue(String eventType,String referenceType,long referenceId,Map<String,Object> payload){
        if(!enabled)return;
        try{
            jdbc.update("insert into sheet_outbox(event_type,reference_type,reference_id,payload) values (?,?,?,?::jsonb)",
                eventType,referenceType,referenceId,objectMapper.writeValueAsString(payload));
        }catch(JsonProcessingException e){
            throw new IllegalStateException("Google Sheets 전송 데이터 생성에 실패했습니다.",e);
        }
    }
}
