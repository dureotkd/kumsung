package kr.co.kumsungenc.platform.security;

import org.springframework.core.env.Environment;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.*;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.*;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
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
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.http.HttpStatus;
import kr.co.kumsungenc.platform.shop.ShopAdminAccessService;

@Configuration
public class SecurityConfig {
    @Bean PasswordEncoder passwordEncoder(){ return new BCryptPasswordEncoder(); }

    @Bean UserDetailsService userDetailsService(AppUserRepository users){
        return username -> users.findByEmailIgnoreCase(username)
            .map(u -> {
                var authorities=new java.util.ArrayList<SimpleGrantedAuthority>();
                authorities.add(new SimpleGrantedAuthority("ROLE_"+u.getRole()));
                if("ADMIN".equals(u.getRole())&&u.getAdminRole()!=null)
                    authorities.add(new SimpleGrantedAuthority("ADMIN_SCOPE_"+u.getAdminRole()));
                return User.withUsername(u.getEmail()).password(u.getPasswordHash())
                    .authorities(authorities).disabled(!u.isEnabled()||!u.isEmailVerified()).build();
            })
            .orElseThrow(() -> new UsernameNotFoundException("사용자를 찾을 수 없습니다."));
    }

    @Bean SessionRegistry sessionRegistry(){return new SessionRegistryImpl();}
    @Bean HttpSessionEventPublisher httpSessionEventPublisher(){return new HttpSessionEventPublisher();}

    @Bean SecurityFilterChain security(HttpSecurity http,RequestRateLimitFilter rateLimit,
        SessionRegistry sessionRegistry,ObjectProvider<ClientRegistrationRepository> clientRegistrations,
        NaverOAuth2UserService naverUsers) throws Exception {
        var csrf = CookieCsrfTokenRepository.withHttpOnlyFalse();
        csrf.setCookiePath("/");
        RequestMatcher apiRequest=request -> request.getRequestURI().startsWith(request.getContextPath()+"/api/");
        http
          .csrf(c -> c.csrfTokenRepository(csrf))
          .authorizeHttpRequests(a -> a
            .requestMatchers("/","/index.html","/quote.html","/shop.html","/shop-payment-success.html","/shop-payment-fail.html","/projects.html","/support.html","/login.html","/naver-login.html","/verify-email.html","/reset-password.html","/privacy.html","/css/**","/js/**","/images/**","/api/quotes",
                "/api/auth/register","/api/auth/resend","/api/auth/verify","/api/auth/csrf","/api/auth/providers","/api/auth/password/**","/api/public/**",
                "/oauth2/authorization/**","/auth/callback/**",
                "/admin-invite.html","/api/auth/admin-account","/api/auth/admin-account/**",
                "/actuator/health","/actuator/health/**","/error").permitAll()
            .requestMatchers("/shop-admin-entry.html","/api/shop-admin/access")
                .hasAnyAuthority("ADMIN_SCOPE_SHOP_ADMIN","ADMIN_SCOPE_SUPER_ADMIN")
            .requestMatchers("/shop-admin.html")
                .hasAnyAuthority("ADMIN_SCOPE_SHOP_ADMIN","ADMIN_SCOPE_SUPER_ADMIN")
            .requestMatchers("/api/shop-admin/**").access((authentication,context) -> {
                var authorities=authentication.get().getAuthorities();
                boolean allowedRole=authorities.stream().anyMatch(authority ->
                    "ADMIN_SCOPE_SHOP_ADMIN".equals(authority.getAuthority())
                    ||"ADMIN_SCOPE_SUPER_ADMIN".equals(authority.getAuthority()));
                return new org.springframework.security.authorization.AuthorizationDecision(
                    allowedRole&&ShopAdminAccessService.isRecentlyVerified(context.getRequest()));
            })
            .requestMatchers("/admin.html","/api/admin/**").access((authentication,context) -> {
                var authorities=authentication.get().getAuthorities();
                boolean admin=authorities.stream().anyMatch(authority->"ROLE_ADMIN".equals(authority.getAuthority()));
                boolean shopOnly=authorities.stream().anyMatch(authority->"ADMIN_SCOPE_SHOP_ADMIN".equals(authority.getAuthority()));
                return new org.springframework.security.authorization.AuthorizationDecision(admin&&!shopOnly);
            })
            .anyRequest().authenticated())
          .formLogin(f -> f.loginPage("/login.html").loginProcessingUrl("/login")
            .successHandler((request,response,authentication) -> {
                boolean shopAdmin=authentication.getAuthorities().stream()
                    .anyMatch(authority -> "ADMIN_SCOPE_SHOP_ADMIN".equals(authority.getAuthority()));
                boolean admin=authentication.getAuthorities().stream()
                    .anyMatch(authority -> "ROLE_ADMIN".equals(authority.getAuthority()));
                response.setStatus(HttpStatus.FOUND.value());
                response.setHeader("Location",shopAdmin ? "/shop-admin-entry.html" : admin ? "/admin.html" : "/portal.html");
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
        if(clientRegistrations.getIfAvailable()!=null){
            http.oauth2Login(o -> o.loginPage("/login.html")
                .redirectionEndpoint(redirection -> redirection.baseUri("/auth/callback/*"))
                .userInfoEndpoint(userInfo -> userInfo.userService(naverUsers))
                .successHandler((request,response,authentication) -> {
                    response.setStatus(HttpStatus.FOUND.value());
                    response.setHeader("Location","/portal.html");
                })
                .failureHandler((request,response,exception) -> {
                    response.setStatus(HttpStatus.FOUND.value());
                    response.setHeader("Location","/login.html?oauthError=true");
                }));
        }
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
