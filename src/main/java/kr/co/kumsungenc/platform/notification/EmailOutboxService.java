package kr.co.kumsungenc.platform.notification;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class EmailOutboxService {
    private final JdbcTemplate jdbc;
    public EmailOutboxService(JdbcTemplate jdbc){this.jdbc=jdbc;}
    public void enqueue(Long quoteId,String recipient,String subject,String body){
        jdbc.update("""
            insert into email_outbox(quote_request_id,recipient,subject,body)
            values (?,?,?,?)
            """,quoteId,recipient,subject,body);
    }

    public void enqueue(String referenceType,Long referenceId,String recipient,String subject,String body){
        jdbc.update("""
            insert into email_outbox(reference_type,reference_id,recipient,subject,body)
            values (?,?,?,?,?)
            """,referenceType,referenceId,recipient,subject,body);
    }
}
