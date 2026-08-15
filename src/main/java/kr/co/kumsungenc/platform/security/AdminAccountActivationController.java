package kr.co.kumsungenc.platform.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import kr.co.kumsungenc.platform.admin.AdminAccountService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth/admin-account")
public class AdminAccountActivationController {
    private final AdminAccountService accounts;
    public AdminAccountActivationController(AdminAccountService accounts){this.accounts=accounts;}

    @GetMapping
    public Map<String,Object> info(@RequestParam String token){
        return accounts.tokenInfo(token);
    }

    public record Acceptance(
        @NotBlank String token,
        @NotBlank @Size(min=12,max=72) String password){}

    @PostMapping
    public Map<String,String> accept(@Valid @RequestBody Acceptance body,HttpServletRequest request){
        return Map.of("message",accounts.accept(body.token(),body.password(),ip(request)));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<Map<String,String>> bad(IllegalArgumentException error){
        return ResponseEntity.badRequest().body(Map.of("message",error.getMessage()));
    }
    private String ip(HttpServletRequest request){
        String forwarded=request.getHeader("X-Forwarded-For");
        return forwarded==null?request.getRemoteAddr():forwarded.split(",")[0].trim();
    }
}
