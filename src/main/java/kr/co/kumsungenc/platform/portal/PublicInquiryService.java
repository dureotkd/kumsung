package kr.co.kumsungenc.platform.portal;

import kr.co.kumsungenc.platform.notification.EmailOutboxService;
import kr.co.kumsungenc.platform.privacy.PrivacyConsentService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
public class PublicInquiryService {
    private final JdbcTemplate jdbc;private final EmailOutboxService emailOutbox;
    private final PrivacyConsentService privacy;private final String recipient;
    public PublicInquiryService(JdbcTemplate jdbc,EmailOutboxService emailOutbox,
        PrivacyConsentService privacy,@Value("${app.support-recipient}") String recipient){this.jdbc=jdbc;this.emailOutbox=emailOutbox;this.privacy=privacy;this.recipient=recipient;}
    public record Request(String companyName,String contactName,String phone,String email,String subject,String message,
        Boolean privacyAgreed,String submissionKey,String website){}

    @Transactional public Map<String,String> submit(Request r,String ip,String userAgent){
        rejectBot(r.website());String submissionKey=submissionKey(r.submissionKey());
        text(r.companyName(),150,"회사명");text(r.contactName(),100,"담당자");text(r.phone(),30,"연락처");text(r.email(),120,"이메일");text(r.subject(),200,"문의 제목");text(r.message(),10000,"문의 내용");
        if(!r.phone().matches("^[0-9+()\\-\\s]+$")||r.phone().replaceAll("\\D","").length()<7)throw new IllegalArgumentException("연락처 형식을 확인해 주세요.");
        if(!r.email().matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$"))throw new IllegalArgumentException("이메일 형식을 확인해 주세요.");
        if(!Boolean.TRUE.equals(r.privacyAgreed()))throw new IllegalArgumentException("개인정보 수집 및 이용에 동의해 주세요.");
        Map<String,String> duplicate=existing(submissionKey);if(duplicate!=null)return duplicate;
        String receipt="QNA-"+LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE)+"-"+UUID.randomUUID().toString().substring(0,8).toUpperCase();
        List<Long> owners=jdbc.query("select id from app_users where lower(email)=lower(?) and role='CUSTOMER' and email_verified=true and enabled=true",(rs,n)->rs.getLong(1),r.email());
        Long owner=owners.isEmpty()?null:owners.getFirst();
        Long id=jdbc.queryForObject("""
            insert into support_inquiries(customer_email,customer_user_id,company_name,contact_name,phone,receipt_number,source,subject,message,submission_key)
            values (?,?,?,?,?,?,'PUBLIC',?,?,?::uuid) returning id
            """,Long.class,r.email().trim().toLowerCase(),owner,r.companyName().trim(),r.contactName().trim(),r.phone().trim(),receipt,r.subject().trim(),r.message().trim(),submissionKey);
        if(id==null)throw new IllegalStateException("고객문의 저장에 실패했습니다.");
        privacy.record("SUPPORT_INQUIRY",id,r.email(),ip,userAgent);
        String body="고객문의가 접수되었습니다.\n접수번호: "+receipt+"\n회사명: "+r.companyName().trim()+"\n담당자: "+r.contactName().trim()+"\n연락처: "+r.phone().trim()+"\n제목: "+r.subject().trim()+"\n\n"+r.message().trim();
        emailOutbox.enqueue("SUPPORT_INQUIRY",id,recipient,"[고객문의 접수] "+receipt+" "+r.subject().trim(),body);
        emailOutbox.enqueue("SUPPORT_INQUIRY",id,r.email().trim().toLowerCase(),"[(주)금성이엔씨] 고객문의 접수 - "+receipt,body+"\n\n담당자 확인 후 연락드리겠습니다.");
        return Map.of("receiptNumber",receipt,"message","고객문의가 등록되었습니다.");
    }
    private Map<String,String> existing(String key){
        if(key==null)return null;
        jdbc.query("select pg_advisory_xact_lock(hashtextextended(?,0))",rs -> {},key);
        List<String> found=jdbc.query("select receipt_number from support_inquiries where submission_key=?::uuid",(rs,n)->rs.getString(1),key);
        return found.isEmpty()?null:Map.of("receiptNumber",found.getFirst(),"message","이미 등록된 고객문의입니다.");
    }
    private String submissionKey(String value){
        if(value==null||value.isBlank())return null;
        try{return UUID.fromString(value.trim()).toString();}catch(IllegalArgumentException e){throw new IllegalArgumentException("문의 요청 식별값이 올바르지 않습니다.");}
    }
    private void rejectBot(String website){if(website!=null&&!website.isBlank())throw new IllegalArgumentException("문의 접수를 처리할 수 없습니다.");}
    private void text(String v,int max,String label){if(v==null||v.isBlank()||v.trim().length()>max)throw new IllegalArgumentException(label+"을(를) "+max+"자 이내로 입력해 주세요.");}
}
