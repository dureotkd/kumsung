package kr.co.kumsungenc.platform.privacy;

import org.springframework.beans.factory.annotation.Value;
import kr.co.kumsungenc.platform.file.FileStorageService;
import kr.co.kumsungenc.platform.file.StorageKeys;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.io.IOException;
import java.util.List;

@Component
public class PrivacyRetentionService {
    private final JdbcTemplate jdbc;private final int unverifiedDays,emailLogDays,inquiryDays,sheetOutboxDays;
    private final FileStorageService storage;
    public PrivacyRetentionService(JdbcTemplate jdbc,
        @Value("${app.privacy.unverified-retention-days:7}") int unverifiedDays,
        @Value("${app.privacy.email-log-retention-days:1095}") int emailLogDays,
        @Value("${app.privacy.inquiry-retention-days:1095}") int inquiryDays,
        @Value("${app.privacy.sheet-outbox-retention-days:1095}") int sheetOutboxDays,
        FileStorageService storage){
        this.jdbc=jdbc;this.unverifiedDays=positive(unverifiedDays);this.emailLogDays=positive(emailLogDays);
        this.inquiryDays=positive(inquiryDays);this.sheetOutboxDays=positive(sheetOutboxDays);
        this.storage=storage;
    }
    @Scheduled(cron="${app.privacy.cleanup-cron:0 30 3 * * *}")
    @Transactional public void cleanup(){
        List<String> expiredShopFiles=jdbc.query("""
            select s.receipt_number,a.stored_name from shop_inquiry_attachments a
            join shop_inquiries s on s.id=a.shop_inquiry_id
            where s.created_at<current_timestamp-(?*interval '1 day')
            """,(rs,n)->StorageKeys.shopAttachment(rs.getString(1),rs.getString(2)),inquiryDays);
        jdbc.update("delete from email_verification_tokens where expires_at<current_timestamp-interval '7 days'");
        jdbc.update("delete from password_reset_tokens where expires_at<current_timestamp-interval '7 days'");
        jdbc.update("delete from app_users where role='CUSTOMER' and email_verified=false and created_at<current_timestamp-(?*interval '1 day')",unverifiedDays);
        jdbc.update("delete from email_logs where sent_at<current_timestamp-(?*interval '1 day')",emailLogDays);
        jdbc.update("delete from email_outbox where status in ('SENT','FAILED') and created_at<current_timestamp-(?*interval '1 day')",emailLogDays);
        jdbc.update("""
            delete from privacy_consents p where
            (p.subject_type='SUPPORT_INQUIRY' and exists(select 1 from support_inquiries s where s.id=p.subject_id and s.created_at<current_timestamp-(?*interval '1 day')))
            or (p.subject_type='SHOP_INQUIRY' and exists(select 1 from shop_inquiries s where s.id=p.subject_id and s.created_at<current_timestamp-(?*interval '1 day')))
            """,inquiryDays,inquiryDays);
        jdbc.update("delete from sheet_outbox where created_at<current_timestamp-(?*interval '1 day')",sheetOutboxDays);
        jdbc.update("delete from support_inquiries where created_at<current_timestamp-(?*interval '1 day')",inquiryDays);
        jdbc.update("delete from shop_inquiries where created_at<current_timestamp-(?*interval '1 day')",inquiryDays);
        if(TransactionSynchronizationManager.isSynchronizationActive())
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization(){
                @Override public void afterCommit(){expiredShopFiles.forEach(PrivacyRetentionService.this::deleteFile);}
            });
    }
    private void deleteFile(String key){try{storage.delete(key);}catch(IOException ignored){}}
    private int positive(int value){return Math.max(1,value);}
}
