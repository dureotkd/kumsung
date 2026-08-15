package kr.co.kumsungenc.platform.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api/auth/password")
public class PasswordResetController {
    private final PasswordResetService service;private final ClientIpResolver clientIpResolver;
    public PasswordResetController(PasswordResetService service,ClientIpResolver clientIpResolver){this.service=service;this.clientIpResolver=clientIpResolver;}
    public record Forgot(@NotBlank @Size(max=120) String email){}
    public record Reset(@NotBlank @Size(max=200) String token,@NotBlank @Size(min=12,max=72) String password){}
    @PostMapping("/forgot") public Map<String,String> forgot(@Valid @RequestBody Forgot request,HttpServletRequest http){
        service.request(request.email(),clientIpResolver.resolve(http));
        return Map.of("message","가입된 계정이면 비밀번호 재설정 이메일을 발송합니다.");
    }
    @PostMapping("/reset") public Map<String,String> reset(@Valid @RequestBody Reset request,HttpServletRequest http){
        service.reset(request.token(),request.password(),clientIpResolver.resolve(http));
        return Map.of("message","비밀번호가 변경되었습니다. 새 비밀번호로 로그인해 주세요.");
    }
}
