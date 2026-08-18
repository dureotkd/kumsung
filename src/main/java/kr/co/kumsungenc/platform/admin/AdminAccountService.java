package kr.co.kumsungenc.platform.admin;

import kr.co.kumsungenc.platform.notification.EmailOutboxService;
import kr.co.kumsungenc.platform.security.AppUser;
import kr.co.kumsungenc.platform.security.AppUserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Service
public class AdminAccountService {
    private static final Set<String> ADMIN_ROLES=Set.of("SUPER_ADMIN","ADMIN","SHOP_ADMIN");
    private final JdbcTemplate jdbc;
    private final AppUserRepository users;
    private final PasswordEncoder encoder;
    private final EmailOutboxService outbox;
    private final SessionRegistry sessions;
    private final String baseUrl;
    private final SecureRandom random=new SecureRandom();

    public AdminAccountService(JdbcTemplate jdbc,AppUserRepository users,PasswordEncoder encoder,
            EmailOutboxService outbox,SessionRegistry sessions,
            @Value("${app.base-url:http://localhost:8080}") String baseUrl){
        this.jdbc=jdbc;this.users=users;this.encoder=encoder;this.outbox=outbox;
        this.sessions=sessions;
        this.baseUrl=baseUrl.replaceAll("/$","");
    }

    public AppUser requireSuperAdmin(String email){
        AppUser actor=users.findByEmailIgnoreCase(email)
            .orElseThrow(()->new IllegalArgumentException("관리자 계정을 찾을 수 없습니다."));
        if(!"ADMIN".equals(actor.getRole())||!"SUPER_ADMIN".equals(actor.getAdminRole())||!actor.isEnabled())
            throw new SecurityException("최고관리자만 사용할 수 있는 기능입니다.");
        return actor;
    }

    public List<Map<String,Object>> admins(){
        return jdbc.queryForList("""
            select id,email,name,admin_role,enabled,created_at,verified_at
            from app_users
            where role='ADMIN'
            order by case admin_role when 'SUPER_ADMIN' then 0 when 'ADMIN' then 1 else 2 end,created_at
            """);
    }

    public List<Map<String,Object>> pendingInvitations(){
        return jdbc.queryForList("""
            select id,email,name,admin_role,invited_by,expires_at,created_at
            from admin_account_tokens
            where purpose='INVITE' and used_at is null and revoked_at is null
              and expires_at>current_timestamp
            order by created_at desc
            """);
    }

    public List<Map<String,Object>> auditLogs(){
        return jdbc.queryForList("""
            select actor_email,action,target_type,target_id,details,ip_address,created_at
            from audit_logs
            where action like 'ADMIN_%'
            order by created_at desc
            limit 100
            """);
    }

    @Transactional
    public void invite(String email,String name,String adminRole,String actorEmail,String ip){
        String normalized=validEmail(email);String validName=required(name,"관리자 이름",60);
        String role=validRole(adminRole);
        requireSuperAdmin(actorEmail);
        if(users.existsByEmailIgnoreCase(normalized))
            throw new IllegalArgumentException("이미 가입된 이메일입니다. 고객 계정과 관리자 계정은 이메일을 공유할 수 없습니다.");
        jdbc.update("""
            update admin_account_tokens set revoked_at=current_timestamp
            where purpose='INVITE' and lower(email)=lower(?) and used_at is null and revoked_at is null
            """,normalized);
        Token token=token();
        jdbc.update("""
            insert into admin_account_tokens(purpose,email,name,admin_role,token_hash,invited_by,expires_at)
            values ('INVITE',?,?,?,?,?,?)
            """,normalized,validName,role,token.hash(),actorEmail,LocalDateTime.now().plusHours(48));
        String link=baseUrl+"/admin-invite.html?token="+token.raw();
        outbox.enqueue(null,normalized,"[(주)금성이엔씨] 관리자 계정 초대",
            validName+"님이 (주)금성이엔씨 관리자 계정으로 초대되었습니다.\n\n"
            +"아래 주소에서 비밀번호를 설정해 주세요.\n"+link
            +"\n\n초대 주소는 48시간 동안 유효합니다.");
        audit(actorEmail,"ADMIN_INVITE","ADMIN_ACCOUNT",normalized,
            "권한="+role,ip);
    }

    @Transactional
    public void requestPasswordReset(long userId,String actorEmail,String ip){
        requireSuperAdmin(actorEmail);
        AppUser target=admin(userId);
        jdbc.update("""
            update admin_account_tokens set revoked_at=current_timestamp
            where purpose='PASSWORD_RESET' and user_id=? and used_at is null and revoked_at is null
            """,userId);
        Token token=token();
        jdbc.update("""
            insert into admin_account_tokens(purpose,user_id,email,name,admin_role,token_hash,invited_by,expires_at)
            values ('PASSWORD_RESET',?,?,?,?,?,?,?)
            """,userId,target.getEmail(),target.getName(),target.getAdminRole(),token.hash(),actorEmail,
            LocalDateTime.now().plusHours(2));
        String link=baseUrl+"/admin-invite.html?token="+token.raw();
        outbox.enqueue(null,target.getEmail(),"[(주)금성이엔씨] 관리자 비밀번호 재설정",
            target.getName()+"님, 아래 주소에서 관리자 비밀번호를 재설정해 주세요.\n\n"+link
            +"\n\n재설정 주소는 2시간 동안 유효합니다.");
        audit(actorEmail,"ADMIN_PASSWORD_RESET_REQUEST","ADMIN_ACCOUNT",String.valueOf(userId),
            "대상="+target.getEmail(),ip);
    }

    @Transactional
    public void changeRole(long userId,String adminRole,String actorEmail,String ip){
        lockSuperAdminChanges();
        AppUser actor=requireSuperAdmin(actorEmail);AppUser target=admin(userId);
        String role=validRole(adminRole);
        if(Objects.equals(actor.getId(),target.getId()))
            throw new IllegalArgumentException("자기 자신의 최고관리자 권한은 변경할 수 없습니다.");
        if("SUPER_ADMIN".equals(target.getAdminRole())&&!"SUPER_ADMIN".equals(role))
            ensureAnotherEnabledSuperAdmin(target.getId());
        String before=target.getAdminRole();target.setAdminRole(role);users.save(target);
        revokeSessions(target.getEmail());
        audit(actorEmail,"ADMIN_ROLE_CHANGE","ADMIN_ACCOUNT",String.valueOf(userId),
            before+" -> "+role,ip);
    }

    @Transactional
    public void changeEnabled(long userId,boolean enabled,String actorEmail,String ip){
        lockSuperAdminChanges();
        AppUser actor=requireSuperAdmin(actorEmail);AppUser target=admin(userId);
        if(Objects.equals(actor.getId(),target.getId()))
            throw new IllegalArgumentException("현재 로그인한 자기 계정은 비활성화할 수 없습니다.");
        if(!enabled&&"SUPER_ADMIN".equals(target.getAdminRole()))
            ensureAnotherEnabledSuperAdmin(target.getId());
        target.setEnabled(enabled);users.save(target);
        if(!enabled)revokeSessions(target.getEmail());
        audit(actorEmail,"ADMIN_STATUS_CHANGE","ADMIN_ACCOUNT",String.valueOf(userId),
            enabled?"활성화":"비활성화",ip);
    }

    @Transactional
    public void revokeInvitation(long tokenId,String actorEmail,String ip){
        requireSuperAdmin(actorEmail);
        int changed=jdbc.update("""
            update admin_account_tokens set revoked_at=current_timestamp
            where id=? and purpose='INVITE' and used_at is null and revoked_at is null
            """,tokenId);
        if(changed==0)throw new IllegalArgumentException("유효한 초대를 찾을 수 없습니다.");
        audit(actorEmail,"ADMIN_INVITE_REVOKE","ADMIN_INVITATION",String.valueOf(tokenId),
            "관리자 초대 취소",ip);
    }

    public Map<String,Object> tokenInfo(String rawToken){
        List<Map<String,Object>> rows=jdbc.queryForList("""
            select purpose,email,name,admin_role,expires_at
            from admin_account_tokens
            where token_hash=? and used_at is null and revoked_at is null
              and expires_at>current_timestamp
            """,hash(required(rawToken,"초대 토큰",200)));
        if(rows.isEmpty())throw new IllegalArgumentException("초대 또는 재설정 주소가 만료되었거나 올바르지 않습니다.");
        return rows.getFirst();
    }

    @Transactional
    public String accept(String rawToken,String password,String ip){
        if(password==null||password.length()<12||password.length()>72)
            throw new IllegalArgumentException("비밀번호는 12자 이상 72자 이하로 입력해 주세요.");
        List<Map<String,Object>> rows=jdbc.queryForList("""
            select id,purpose,user_id,email,name,admin_role
            from admin_account_tokens
            where token_hash=? and used_at is null and revoked_at is null
              and expires_at>current_timestamp
            for update
            """,hash(required(rawToken,"초대 토큰",200)));
        if(rows.isEmpty())throw new IllegalArgumentException("초대 또는 재설정 주소가 만료되었거나 올바르지 않습니다.");
        Map<String,Object> row=rows.getFirst();String purpose=(String)row.get("purpose");
        String email=(String)row.get("email");AppUser user;
        if("INVITE".equals(purpose)){
            if(users.existsByEmailIgnoreCase(email))
                throw new IllegalArgumentException("이미 사용 중인 이메일입니다. 최고관리자에게 새 초대를 요청해 주세요.");
            user=new AppUser();user.setEmail(email.toLowerCase(Locale.ROOT));user.setName((String)row.get("name"));
            user.setCompanyName("(주)금성이엔씨");user.setRole("ADMIN");
            user.setAdminRole((String)row.get("admin_role"));user.setEnabled(true);
            user.setEmailVerified(true);user.setVerifiedAt(LocalDateTime.now());
        }else{
            long userId=((Number)row.get("user_id")).longValue();user=admin(userId);
        }
        user.setPasswordHash(encoder.encode(password));users.saveAndFlush(user);
        if("PASSWORD_RESET".equals(purpose))revokeSessions(user.getEmail());
        jdbc.update("update admin_account_tokens set used_at=current_timestamp where id=?",row.get("id"));
        audit(email,"INVITE".equals(purpose)?"ADMIN_INVITE_ACCEPT":"ADMIN_PASSWORD_RESET",
            "ADMIN_ACCOUNT",String.valueOf(user.getId()),purpose,ip);
        return "INVITE".equals(purpose)?"관리자 계정 생성이 완료되었습니다.":"관리자 비밀번호가 변경되었습니다.";
    }

    private AppUser admin(long id){
        AppUser target=users.findById(id).orElseThrow(()->new IllegalArgumentException("관리자를 찾을 수 없습니다."));
        if(!"ADMIN".equals(target.getRole()))throw new IllegalArgumentException("관리자를 찾을 수 없습니다.");
        return target;
    }
    private void ensureAnotherEnabledSuperAdmin(long excludedId){
        Integer count=jdbc.queryForObject("""
            select count(*) from app_users
            where role='ADMIN' and admin_role='SUPER_ADMIN' and enabled=true and id<>?
            """,Integer.class,excludedId);
        if(count==null||count<1)throw new IllegalArgumentException("활성 상태의 마지막 최고관리자는 변경하거나 비활성화할 수 없습니다.");
    }
    private void lockSuperAdminChanges(){
        jdbc.query("select id from app_users where role='ADMIN' and admin_role='SUPER_ADMIN' for update",rs->{});
    }
    private String validRole(String role){
        String value=role==null?"":role.toUpperCase(Locale.ROOT);
        if(!ADMIN_ROLES.contains(value))throw new IllegalArgumentException("올바른 관리자 권한을 선택해 주세요.");
        return value;
    }
    private String validEmail(String email){
        String value=required(email,"이메일",120).toLowerCase(Locale.ROOT);
        if(!value.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$"))
            throw new IllegalArgumentException("올바른 이메일 주소를 입력해 주세요.");
        return value;
    }
    private String required(String value,String label,int max){
        if(value==null||value.isBlank()||value.trim().length()>max)
            throw new IllegalArgumentException(label+" 값을 확인해 주세요.");
        return value.trim();
    }
    private Token token(){
        byte[] bytes=new byte[32];random.nextBytes(bytes);
        String raw=Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        return new Token(raw,hash(raw));
    }
    private String hash(String token){
        try{
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(token.getBytes(StandardCharsets.UTF_8)));
        }catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);}
    }
    private void audit(String actor,String action,String targetType,String targetId,String details,String ip){
        jdbc.update("""
            insert into audit_logs(actor_email,action,target_type,target_id,details,ip_address)
            values (?,?,?,?,?,?)
            """,actor,action,targetType,targetId,details,ip);
    }
    private void revokeSessions(String email){
        for(Object principal:sessions.getAllPrincipals()){
            if(principal instanceof UserDetails details&&details.getUsername().equalsIgnoreCase(email))
                sessions.getAllSessions(principal,false).forEach(session->session.expireNow());
        }
    }
    private record Token(String raw,String hash){}
}
