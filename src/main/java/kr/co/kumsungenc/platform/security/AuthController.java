package kr.co.kumsungenc.platform.security;

import kr.co.kumsungenc.platform.privacy.PrivacyConsentService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import org.springframework.transaction.annotation.Transactional;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AppUserRepository users; private final PasswordEncoder encoder;
    private final EmailVerificationService verification;private final PrivacyConsentService privacy;
    private final ClientIpResolver clientIpResolver;
    private final boolean naverLoginEnabled;
    public AuthController(AppUserRepository users,PasswordEncoder encoder,
        EmailVerificationService verification,PrivacyConsentService privacy,ClientIpResolver clientIpResolver,
        @Value("${app.naver.enabled:false}") boolean naverLoginEnabled){
        this.users=users;this.encoder=encoder;this.verification=verification;this.privacy=privacy;
        this.clientIpResolver=clientIpResolver;this.naverLoginEnabled=naverLoginEnabled;
    }

    public record Registration(@NotBlank @Email String email,@NotBlank @Size(min=12,max=72) String password,
        @NotBlank @Size(max=60) String name,@NotBlank @Size(max=150) String companyName,
        @NotBlank @Size(max=30) String phone,
        @AssertTrue(message="개인정보 수집 및 이용에 동의해 주세요.") boolean privacyAgreed){}

    @GetMapping("/csrf") public Map<String,String> csrf(CsrfToken token){
        return Map.of("headerName",token.getHeaderName(),"token",token.getToken());
    }
    @GetMapping("/providers") public Map<String,Boolean> providers(){
        return Map.of("naver",naverLoginEnabled);
    }
    @GetMapping("/me") public Map<String,String> me(Authentication auth){
        AppUser u=users.findByEmailIgnoreCase(auth.getName()).orElseThrow();
        Map<String,String> result=new java.util.LinkedHashMap<>();
        result.put("email",u.getEmail());result.put("name",u.getName());
        result.put("companyName",u.getCompanyName()==null?"":u.getCompanyName());
        result.put("role",u.getRole());
        result.put("adminRole",u.getAdminRole()==null?"":u.getAdminRole());
        return result;
    }
    @PostMapping("/register") @Transactional
    public ResponseEntity<Map<String,String>> register(@Valid @RequestBody Registration r,HttpServletRequest request){
        if(users.existsByEmailIgnoreCase(r.email()))
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("message","이미 가입된 이메일입니다."));
        AppUser u=new AppUser();u.setEmail(r.email().toLowerCase());u.setPasswordHash(encoder.encode(r.password()));
        u.setName(r.name());u.setCompanyName(r.companyName());u.setPhone(r.phone());users.saveAndFlush(u);
        privacy.record("USER",u.getId(),u.getEmail(),clientIpResolver.resolve(request),request.getHeader("User-Agent"));
        verification.issue(u);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("message","인증 이메일을 발송했습니다. 이메일 인증 후 로그인해 주세요."));
    }
    public record Resend(@NotBlank @Email String email){}
    @PostMapping("/resend") @Transactional
    public Map<String,String> resend(@Valid @RequestBody Resend r){
        users.findByEmailIgnoreCase(r.email()).filter(u->!u.isEmailVerified()).ifPresent(verification::issue);
        return Map.of("message","가입 정보가 있으면 인증 이메일을 다시 발송합니다.");
    }
    public record Verification(@NotBlank @Size(max=200) String token){}
    @PostMapping("/verify") @Transactional
    public ResponseEntity<Map<String,String>> verify(@Valid @RequestBody Verification request){
        if(!verification.verify(request.token()))
            return ResponseEntity.badRequest().body(Map.of("message","인증 주소가 만료되었거나 올바르지 않습니다."));
        return ResponseEntity.ok(Map.of("message","이메일 인증이 완료되었습니다."));
    }
}
