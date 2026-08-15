package kr.co.kumsungenc.platform.admin;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;
import java.util.Map;
import kr.co.kumsungenc.platform.security.ClientIpResolver;

@RestController
@RequestMapping("/api/admin/accounts")
public class AdminAccountController {
    private final AdminAccountService accounts;
    private final ClientIpResolver clientIpResolver;
    public AdminAccountController(AdminAccountService accounts,ClientIpResolver clientIpResolver){this.accounts=accounts;this.clientIpResolver=clientIpResolver;}

    @GetMapping
    public Map<String,Object> list(Principal principal){
        accounts.requireSuperAdmin(principal.getName());
        return Map.of("admins",accounts.admins(),"invitations",accounts.pendingInvitations(),
            "audits",accounts.auditLogs());
    }

    public record InvitationRequest(
        @NotBlank @Email @Size(max=120) String email,
        @NotBlank @Size(max=60) String name,
        @NotBlank @Pattern(regexp="SUPER_ADMIN|ADMIN") String adminRole){}

    @PostMapping("/invitations")
    public ResponseEntity<Map<String,String>> invite(@Valid @RequestBody InvitationRequest body,
            Principal principal,HttpServletRequest request){
        accounts.invite(body.email(),body.name(),body.adminRole(),principal.getName(),clientIpResolver.resolve(request));
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("message","관리자 초대 메일을 발송 대기열에 등록했습니다."));
    }

    public record RoleRequest(@NotBlank @Pattern(regexp="SUPER_ADMIN|ADMIN") String adminRole){}
    @PutMapping("/{id}/role")
    public Map<String,String> role(@PathVariable long id,@Valid @RequestBody RoleRequest body,
            Principal principal,HttpServletRequest request){
        accounts.changeRole(id,body.adminRole(),principal.getName(),clientIpResolver.resolve(request));
        return Map.of("message","관리자 권한이 변경되었습니다.");
    }

    public record EnabledRequest(boolean enabled){}
    @PutMapping("/{id}/enabled")
    public Map<String,String> enabled(@PathVariable long id,@RequestBody EnabledRequest body,
            Principal principal,HttpServletRequest request){
        accounts.changeEnabled(id,body.enabled(),principal.getName(),clientIpResolver.resolve(request));
        return Map.of("message","관리자 계정 상태가 변경되었습니다.");
    }

    @PostMapping("/{id}/password-reset")
    public Map<String,String> passwordReset(@PathVariable long id,Principal principal,HttpServletRequest request){
        accounts.requestPasswordReset(id,principal.getName(),clientIpResolver.resolve(request));
        return Map.of("message","비밀번호 재설정 메일을 발송 대기열에 등록했습니다.");
    }

    @DeleteMapping("/invitations/{id}")
    public Map<String,String> revoke(@PathVariable long id,Principal principal,HttpServletRequest request){
        accounts.revokeInvitation(id,principal.getName(),clientIpResolver.resolve(request));
        return Map.of("message","관리자 초대를 취소했습니다.");
    }

    @ExceptionHandler(SecurityException.class)
    ResponseEntity<Map<String,String>> forbidden(SecurityException error){
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message",error.getMessage()));
    }
    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<Map<String,String>> bad(IllegalArgumentException error){
        return ResponseEntity.badRequest().body(Map.of("message",error.getMessage()));
    }
}
