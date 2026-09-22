package kr.co.kumsungenc.platform.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

@Component
public class CustomerLoginSuccessHandler implements AuthenticationSuccessHandler {
    private final AppUserRepository users;
    public CustomerLoginSuccessHandler(AppUserRepository users){this.users=users;}

    @Override public void onAuthenticationSuccess(HttpServletRequest request,HttpServletResponse response,Authentication authentication){
        boolean complete=users.findByEmailIgnoreCase(authentication.getName())
            .map(AppUser::hasCompleteProfile).orElse(false);
        response.setStatus(HttpServletResponse.SC_FOUND);
        response.setHeader("Location",complete?"/portal.html":"/profile.html");
    }
}
