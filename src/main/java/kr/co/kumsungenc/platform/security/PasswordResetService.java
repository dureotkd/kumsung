package kr.co.kumsungenc.platform.security;

import kr.co.kumsungenc.platform.notification.EmailOutboxService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.LocalDateTime;
import java.util.*;

@Service
public class PasswordResetService {
    private final JdbcTemplate jdbc;private final AppUserRepository users;private final PasswordEncoder encoder;
    private final EmailOutboxService outbox;private final SessionRegistry sessions;private final String baseUrl;
    private final SecureRandom random=new SecureRandom();

    public PasswordResetService(JdbcTemplate jdbc,AppUserRepository users,PasswordEncoder encoder,
        EmailOutboxService outbox,SessionRegistry sessions,@Value("${app.base-url:http://localhost:8080}") String baseUrl){
        this.jdbc=jdbc;this.users=users;this.encoder=encoder;this.outbox=outbox;this.sessions=sessions;
        this.baseUrl=baseUrl.replaceAll("/$","");
    }

    @Transactional
    public void request(String email,String ip){
        String normalized=normalize(email);
        if(normalized==null)return;
        Optional<AppUser> found=users.findByEmailIgnoreCase(normalized)
            .filter(u->u.isEnabled()&&u.isEmailVerified());
        if(found.isEmpty())return;
        AppUser user=found.get();
        jdbc.query("select id from app_users where id=? for update",rs->{},user.getId());
        jdbc.update("update password_reset_tokens set revoked_at=current_timestamp where user_id=? and used_at is null and revoked_at is null",user.getId());
        Token token=token();
        jdbc.update("insert into password_reset_tokens(user_id,token_hash,expires_at) values (?,?,?)",
            user.getId(),token.hash(),LocalDateTime.now().plusHours(1));
        String link=baseUrl+"/reset-password.html#token="+token.raw();
        outbox.enqueue(null,user.getEmail(),"[(주)금성이엔씨] 비밀번호 재설정",
            user.getName()+"님, 아래 주소에서 비밀번호를 재설정해 주세요.\n\n"+link
            +"\n\n주소는 1시간 동안 한 번만 사용할 수 있습니다. 요청하지 않았다면 이 메일을 무시해 주세요.");
        audit(user.getEmail(),"PASSWORD_RESET_REQUEST",Long.toString(user.getId()),ip);
    }

    @Transactional
    public void reset(String rawToken,String password,String ip){
        if(rawToken==null||rawToken.isBlank()||rawToken.length()>200)
            throw new IllegalArgumentException("재설정 주소가 만료되었거나 올바르지 않습니다.");
        if(password==null||password.length()<12||password.length()>72)
            throw new IllegalArgumentException("비밀번호는 12자 이상 72자 이하로 입력해 주세요.");
        List<Map<String,Object>> rows=jdbc.queryForList("""
            select t.id,t.user_id,u.email from password_reset_tokens t
            join app_users u on u.id=t.user_id
            where t.token_hash=? and t.used_at is null and t.revoked_at is null
              and t.expires_at>current_timestamp and u.enabled=true and u.email_verified=true
            for update of t,u
            """,hash(rawToken));
        if(rows.isEmpty())throw new IllegalArgumentException("재설정 주소가 만료되었거나 올바르지 않습니다.");
        Map<String,Object> row=rows.getFirst();long userId=((Number)row.get("user_id")).longValue();String email=(String)row.get("email");
        jdbc.update("update app_users set password_hash=? where id=?",encoder.encode(password),userId);
        jdbc.update("update password_reset_tokens set used_at=current_timestamp where id=?",row.get("id"));
        jdbc.update("update password_reset_tokens set revoked_at=current_timestamp where user_id=? and id<>? and used_at is null and revoked_at is null",userId,row.get("id"));
        revokeSessions(email);audit(email,"PASSWORD_RESET","USER",Long.toString(userId),ip);
    }

    private String normalize(String email){
        if(email==null)return null;String value=email.trim().toLowerCase(Locale.ROOT);
        return value.length()<=120&&value.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")?value:null;
    }
    private Token token(){byte[] bytes=new byte[32];random.nextBytes(bytes);String raw=Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);return new Token(raw,hash(raw));}
    private String hash(String value){
        try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));}
        catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);}
    }
    private void revokeSessions(String email){
        for(Object principal:sessions.getAllPrincipals())if(principal instanceof UserDetails details&&details.getUsername().equalsIgnoreCase(email))
            sessions.getAllSessions(principal,false).forEach(session->session.expireNow());
    }
    private void audit(String actor,String action,String targetId,String ip){audit(actor,action,"USER",targetId,ip);}
    private void audit(String actor,String action,String targetType,String targetId,String ip){
        jdbc.update("insert into audit_logs(actor_email,action,target_type,target_id,details,ip_address) values (?,?,?,?,?,?)",
            actor,action,targetType,targetId,"셀프서비스 비밀번호 재설정",ip);
    }
    private record Token(String raw,String hash){}
}
