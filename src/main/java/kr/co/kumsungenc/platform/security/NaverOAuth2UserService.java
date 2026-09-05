package kr.co.kumsungenc.platform.security;

import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.client.userinfo.*;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.core.user.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.*;

@Service
public class NaverOAuth2UserService implements OAuth2UserService<OAuth2UserRequest,OAuth2User> {
    private static final String PROVIDER="NAVER";
    private final OAuth2UserService<OAuth2UserRequest,OAuth2User> delegate;
    private final OAuthIdentityRepository identities;
    private final AppUserRepository users;
    private final PasswordEncoder passwordEncoder;

    @Autowired
    public NaverOAuth2UserService(OAuthIdentityRepository identities,AppUserRepository users,
        PasswordEncoder passwordEncoder){
        this(new DefaultOAuth2UserService(),identities,users,passwordEncoder);
    }

    NaverOAuth2UserService(OAuth2UserService<OAuth2UserRequest,OAuth2User> delegate,
        OAuthIdentityRepository identities,AppUserRepository users,PasswordEncoder passwordEncoder){
        this.delegate=delegate;this.identities=identities;this.users=users;this.passwordEncoder=passwordEncoder;
    }

    @Override @Transactional
    public OAuth2User loadUser(OAuth2UserRequest request) throws OAuth2AuthenticationException {
        if(!"naver".equals(request.getClientRegistration().getRegistrationId()))
            throw failure("unsupported_provider","지원하지 않는 소셜 로그인입니다.");
        OAuth2User remote=delegate.loadUser(request);
        Map<String,Object> profile=profile(remote.getAttributes());
        String providerUserId=required(profile,"id","네이버 사용자 식별 정보를 확인할 수 없습니다.");
        String providedEmail=required(profile,"email","네이버 계정의 이메일 제공 동의가 필요합니다.").toLowerCase(Locale.ROOT);
        if(providerUserId.length()>100)
            throw failure("invalid_id","네이버 사용자 식별 정보가 올바르지 않습니다.");
        if(providedEmail.length()>120||!providedEmail.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$"))
            throw failure("invalid_email","네이버 계정의 이메일 정보가 올바르지 않습니다.");

        Optional<OAuthIdentity> linkedIdentity=identities.findByProviderAndProviderUserId(PROVIDER,providerUserId);
        AppUser user=linkedIdentity.map(OAuthIdentity::getUser)
            .orElseGet(() -> linkOrCreate(profile,providedEmail,providerUserId));
        if(!user.isEnabled())throw failure("account_disabled","비활성화된 계정입니다. 관리자에게 문의해 주세요.");
        if(!"CUSTOMER".equals(user.getRole()))
            throw failure("admin_social_login_blocked","관리자 계정은 이메일과 비밀번호로 로그인해 주세요.");
        linkedIdentity.ifPresent(identity -> {
            identity.setLastLoginAt(LocalDateTime.now());identities.save(identity);
        });

        Map<String,Object> attributes=new LinkedHashMap<>();
        attributes.put("id",providerUserId);attributes.put("email",user.getEmail());attributes.put("name",user.getName());
        return new DefaultOAuth2User(Set.of(new SimpleGrantedAuthority("ROLE_CUSTOMER")),attributes,"email");
    }

    private AppUser linkOrCreate(Map<String,Object> profile,String email,String providerUserId){
        AppUser user=users.findByEmailIgnoreCase(email).orElseGet(() -> {
            AppUser created=new AppUser();created.setEmail(email);
            created.setPasswordHash(passwordEncoder.encode(UUID.randomUUID()+":"+UUID.randomUUID()));
            created.setName(displayName(profile));created.setPhone(optional(profile,"mobile",30));
            created.setRole("CUSTOMER");created.setEnabled(true);created.setEmailVerified(true);
            created.setVerifiedAt(LocalDateTime.now());return users.save(created);
        });
        if(!"CUSTOMER".equals(user.getRole()))
            throw failure("admin_social_login_blocked","관리자 계정은 이메일과 비밀번호로 로그인해 주세요.");
        if(!user.isEmailVerified()){
            user.setEmailVerified(true);user.setVerifiedAt(LocalDateTime.now());users.save(user);
        }
        OAuthIdentity identity=new OAuthIdentity();identity.setUser(user);identity.setProvider(PROVIDER);
        identity.setProviderUserId(providerUserId);identities.save(identity);
        return user;
    }

    private Map<String,Object> profile(Map<String,Object> attributes){
        Object response=attributes.get("response");
        if(!(response instanceof Map<?,?> values))throw failure("invalid_profile","네이버 회원 정보를 확인할 수 없습니다.");
        Map<String,Object> result=new LinkedHashMap<>();values.forEach((key,value)->result.put(String.valueOf(key),value));return result;
    }
    private String displayName(Map<String,Object> profile){
        String name=optional(profile,"name",60);if(name!=null)return name;
        String nickname=optional(profile,"nickname",60);return nickname==null?"네이버 회원":nickname;
    }
    private String required(Map<String,Object> profile,String key,String message){
        Object raw=profile.get(key);String value=raw==null?null:String.valueOf(raw).trim();
        if(value==null||value.isEmpty())throw failure("missing_"+key,message);return value;
    }
    private String optional(Map<String,Object> profile,String key,int maxLength){
        Object raw=profile.get(key);if(raw==null)return null;String value=String.valueOf(raw).trim();
        if(value.isEmpty())return null;return value.substring(0,Math.min(maxLength,value.length()));
    }
    private OAuth2AuthenticationException failure(String code,String message){
        return new OAuth2AuthenticationException(new OAuth2Error(code),message);
    }
}
