package kr.co.kumsungenc.platform.privacy;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class PrivacyConsentService {
    private final JdbcTemplate jdbc;private final String version;private final String purpose;
    public PrivacyConsentService(JdbcTemplate jdbc,
        @Value("${app.privacy.version:2026-07-28}") String version,
        @Value("${app.privacy.purpose:견적 상담 및 고객 업무 서비스 제공}") String purpose){
        this.jdbc=jdbc;this.version=version;this.purpose=purpose;
    }
    public void record(String subjectType,long subjectId,String email,String ip,String userAgent){
        jdbc.update("""
            insert into privacy_consents(subject_type,subject_id,email,consent_version,purpose,ip_address,user_agent)
            values (?,?,?,?,?,?,?)
            """,subjectType,subjectId,email,version,purpose,truncate(ip,64),truncate(userAgent,500));
    }
    public String version(){return version;}
    private String truncate(String value,int max){
        if(value==null)return null;return value.substring(0,Math.min(value.length(),max));
    }
}
