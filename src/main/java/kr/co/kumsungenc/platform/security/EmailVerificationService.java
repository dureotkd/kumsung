package kr.co.kumsungenc.platform.security;

import kr.co.kumsungenc.platform.notification.EmailOutboxService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.*;
import java.util.*;

@Service
public class EmailVerificationService {
    private final JdbcTemplate jdbc;private final EmailOutboxService outbox;private final String baseUrl;
    private final SecureRandom random=new SecureRandom();
    public EmailVerificationService(JdbcTemplate jdbc,EmailOutboxService outbox,
        @Value("${app.base-url:http://localhost:8080}") String baseUrl){
        this.jdbc=jdbc;this.outbox=outbox;this.baseUrl=baseUrl.replaceAll("/$","");
    }
    @Transactional
    public void issue(AppUser user){
        jdbc.query("select id from app_users where id=? for update",rs->{},user.getId());
        jdbc.update("delete from email_verification_tokens where user_id=? and used_at is null",user.getId());
        byte[] bytes=new byte[32];random.nextBytes(bytes);
        String token=Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        jdbc.update("insert into email_verification_tokens(user_id,token_hash,expires_at) values (?,?,?)",
            user.getId(),hash(token),LocalDateTime.now().plusHours(24));
        // 토큰은 URL fragment에 두어 프록시 접근 로그와 Referer 헤더에 남지 않게 한다.
        // 링크를 여는 GET 요청은 인증 상태를 변경하지 않으며, 확인 화면의 CSRF 보호 POST만 토큰을 소비한다.
        String link=baseUrl+"/verify-email.html#token="+token;
        outbox.enqueue(null,user.getEmail(),"[(주)금성이엔씨] 이메일 인증",
            "(주)금성이엔씨 스마트 플랫폼 가입을 완료하려면 아래 주소를 열어주세요.\n\n"+link+"\n\n인증 주소는 24시간 동안 유효합니다.");
    }
    @Transactional
    public boolean verify(String token){
        List<Long> ids=jdbc.query("""
            select user_id from email_verification_tokens
            where token_hash=? and used_at is null and expires_at>current_timestamp
            for update
            """,(rs,n)->rs.getLong(1),hash(token));
        if(ids.isEmpty())return false;
        long userId=ids.getFirst();
        jdbc.update("update email_verification_tokens set used_at=current_timestamp where user_id=? and token_hash=?",userId,hash(token));
        jdbc.update("update app_users set email_verified=true,verified_at=current_timestamp where id=?",userId);
        jdbc.update("""
            update quote_requests q set owner_user_id=?
            from app_users u
            where u.id=? and q.owner_user_id is null and lower(q.email)=lower(u.email)
            """,userId,userId);
        return true;
    }
    private String hash(String token){
        try{
            byte[] digest=MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        }catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);}
    }
}
