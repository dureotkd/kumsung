package kr.co.kumsungenc.platform.security;

import org.springframework.core.env.Environment;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.*;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.session.SessionRegistryImpl;
import org.springframework.security.web.session.HttpSessionEventPublisher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.http.HttpStatus;

@Configuration
public class SecurityConfig {
    @Bean PasswordEncoder passwordEncoder(){ return new BCryptPasswordEncoder(); }

    @Bean UserDetailsService userDetailsService(AppUserRepository users){
        return username -> users.findByEmailIgnoreCase(username)
            .map(u -> User.withUsername(u.getEmail()).password(u.getPasswordHash())
                .roles(u.getRole()).disabled(!u.isEnabled()||!u.isEmailVerified()).build())
            .orElseThrow(() -> new UsernameNotFoundException("사용자를 찾을 수 없습니다."));
    }

    @Bean SessionRegistry sessionRegistry(){return new SessionRegistryImpl();}
    @Bean HttpSessionEventPublisher httpSessionEventPublisher(){return new HttpSessionEventPublisher();}

    @Bean SecurityFilterChain security(HttpSecurity http,RequestRateLimitFilter rateLimit,
        SessionRegistry sessionRegistry) throws Exception {
        var csrf = CookieCsrfTokenRepository.withHttpOnlyFalse();
        csrf.setCookiePath("/");
        RequestMatcher apiRequest=request -> request.getRequestURI().startsWith(request.getContextPath()+"/api/");
        http
          .csrf(c -> c.csrfTokenRepository(csrf))
          .authorizeHttpRequests(a -> a
            .requestMatchers("/","/index.html","/quote.html","/shop.html","/projects.html","/support.html","/login.html","/verify-email.html","/reset-password.html","/privacy.html","/css/**","/js/**","/images/**","/api/quotes",
                "/api/auth/register","/api/auth/resend","/api/auth/verify","/api/auth/csrf","/api/auth/password/**","/api/public/**",
                "/admin-invite.html","/api/auth/admin-account","/api/auth/admin-account/**",
                "/actuator/health","/actuator/health/**","/error").permitAll()
            .requestMatchers("/admin.html","/api/admin/**").hasRole("ADMIN")
            .anyRequest().authenticated())
          .formLogin(f -> f.loginPage("/login.html").loginProcessingUrl("/login")
            .successHandler((request,response,authentication) -> {
                boolean admin=authentication.getAuthorities().stream()
                    .anyMatch(authority -> "ROLE_ADMIN".equals(authority.getAuthority()));
                response.setStatus(HttpStatus.FOUND.value());
                response.setHeader("Location",admin ? "/admin.html" : "/portal.html");
            })
            .failureHandler((request,response,exception) -> {
                response.setStatus(HttpStatus.FOUND.value());
                response.setHeader("Location","/login.html?error");
            }).permitAll())
          .sessionManagement(s -> s.maximumSessions(-1).sessionRegistry(sessionRegistry))
          .exceptionHandling(e -> e
            .defaultAuthenticationEntryPointFor(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED),
                apiRequest)
            .accessDeniedHandler((request,response,denied) -> response.sendError(HttpStatus.FORBIDDEN.value())))
          .logout(l -> l.logoutSuccessUrl("/").permitAll());
        http.addFilterBefore(rateLimit,UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean CommandLineRunner provisionAdmin(AppUserRepository users, PasswordEncoder encoder,Environment environment) {
        return args -> {
            String email=environment.getProperty("app.admin-email","");
            String password=environment.getProperty("app.admin-password","");
            if(email.isBlank()&&password.isBlank())return;
            if(email.isBlank()||password.length()<12)
                throw new IllegalStateException("관리자 프로비저닝에는 ADMIN_EMAIL과 12자 이상의 ADMIN_PASSWORD가 필요합니다.");
            var existing=users.findByEmailIgnoreCase(email);
            if(existing.isPresent()){
                AppUser current=existing.get();
                if(!"ADMIN".equals(current.getRole()))
                    throw new IllegalStateException("ADMIN_EMAIL이 기존 고객 계정과 중복됩니다.");
                if(current.getAdminRole()==null){
                    current.setAdminRole("SUPER_ADMIN");
                    users.save(current);
                }
                return;
            }
            AppUser u=new AppUser();u.setEmail(email.toLowerCase());u.setPasswordHash(encoder.encode(password));
            u.setName("시스템 관리자");u.setCompanyName("(주)금성이엔씨");u.setRole("ADMIN");
            u.setAdminRole("SUPER_ADMIN");u.setEnabled(true);u.setEmailVerified(true);
            u.setVerifiedAt(java.time.LocalDateTime.now());users.save(u);
        };
    }
}
