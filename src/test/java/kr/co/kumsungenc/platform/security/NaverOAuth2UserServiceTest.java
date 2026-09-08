package kr.co.kumsungenc.platform.security;

import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.userinfo.*;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.core.user.OAuth2User;
import java.time.Instant;
import java.nio.charset.StandardCharsets;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class NaverOAuth2UserServiceTest {
    private OAuth2UserService<OAuth2UserRequest,OAuth2User> delegate;
    private OAuthIdentityRepository identities;
    private AppUserRepository users;
    private PasswordEncoder encoder;
    private NaverOAuth2UserService service;

    @BeforeEach void setUp(){
        delegate=mock(OAuth2UserService.class);identities=mock(OAuthIdentityRepository.class);
        users=mock(AppUserRepository.class);encoder=spy(new BCryptPasswordEncoder());
        service=new NaverOAuth2UserService(delegate,identities,users,encoder);
    }

    @Test void linksExistingCustomerByEmailAndUsesApplicationEmailAsPrincipal(){
        AppUser customer=new AppUser();customer.setEmail("customer@example.com");customer.setName("기존 고객");
        customer.setRole("CUSTOMER");customer.setEnabled(true);customer.setEmailVerified(true);
        OAuth2User remoteUser=remote(Map.of(
            "id","naver-user-id","email","Customer@Example.com","name","네이버 고객"));
        when(delegate.loadUser(any())).thenReturn(remoteUser);
        when(identities.findByProviderAndProviderUserId("NAVER","naver-user-id")).thenReturn(Optional.empty());
        when(users.findByEmailIgnoreCase("customer@example.com")).thenReturn(Optional.of(customer));

        OAuth2User principal=service.loadUser(request());

        assertEquals("customer@example.com",principal.getName());
        ArgumentCaptor<OAuthIdentity> identity=ArgumentCaptor.forClass(OAuthIdentity.class);
        verify(identities).save(identity.capture());
        assertSame(customer,identity.getValue().getUser());
        assertEquals("NAVER",identity.getValue().getProvider());
        assertEquals("naver-user-id",identity.getValue().getProviderUserId());
    }

    @Test void createsVerifiedCustomerForFirstNaverLogin(){
        OAuth2User remoteUser=remote(Map.of(
            "id","new-naver-id","email","new@example.com","nickname","새 회원","mobile","010-1234-5678"));
        when(delegate.loadUser(any())).thenReturn(remoteUser);
        when(identities.findByProviderAndProviderUserId("NAVER","new-naver-id")).thenReturn(Optional.empty());
        when(users.findByEmailIgnoreCase("new@example.com")).thenReturn(Optional.empty());
        when(users.save(any(AppUser.class))).thenAnswer(invocation->invocation.getArgument(0));

        OAuth2User principal=service.loadUser(request());

        assertEquals("new@example.com",principal.getName());
        ArgumentCaptor<AppUser> user=ArgumentCaptor.forClass(AppUser.class);verify(users).save(user.capture());
        assertEquals("새 회원",user.getValue().getName());
        assertEquals("010-1234-5678",user.getValue().getPhone());
        assertTrue(user.getValue().isEmailVerified());
        assertEquals("CUSTOMER",user.getValue().getRole());
        assertTrue(user.getValue().isEnabled());
        assertNotNull(user.getValue().getVerifiedAt());
        ArgumentCaptor<CharSequence> password=ArgumentCaptor.forClass(CharSequence.class);
        verify(encoder).encode(password.capture());
        String generated=password.getValue().toString();
        assertTrue(generated.getBytes(StandardCharsets.UTF_8).length<=72);
        assertNotEquals(generated,user.getValue().getPasswordHash());
        assertTrue(encoder.matches(generated,user.getValue().getPasswordHash()));
        ArgumentCaptor<OAuthIdentity> identity=ArgumentCaptor.forClass(OAuthIdentity.class);
        verify(identities).save(identity.capture());
        assertSame(user.getValue(),identity.getValue().getUser());
        assertEquals("new-naver-id",identity.getValue().getProviderUserId());
        assertTrue(principal.getAuthorities().stream().anyMatch(a->"ROLE_CUSTOMER".equals(a.getAuthority())));
    }

    @Test void generatesDifferentInternalPasswordsForNewCustomers(){
        OAuth2User first=remote(Map.of("id","first-id","email","first@example.com"));
        OAuth2User second=remote(Map.of("id","second-id","email","second@example.com"));
        when(delegate.loadUser(any())).thenReturn(first,second);
        when(users.save(any(AppUser.class))).thenAnswer(invocation->invocation.getArgument(0));

        service.loadUser(request());
        service.loadUser(request());

        ArgumentCaptor<CharSequence> passwords=ArgumentCaptor.forClass(CharSequence.class);
        verify(encoder,times(2)).encode(passwords.capture());
        assertNotEquals(passwords.getAllValues().get(0),passwords.getAllValues().get(1));
    }

    @Test void rejectsProfileWhenEmailWasNotProvided(){
        OAuth2User remoteUser=remote(Map.of("id","naver-user-id","name","이름"));
        when(delegate.loadUser(any())).thenReturn(remoteUser);

        OAuth2AuthenticationException exception=assertThrows(OAuth2AuthenticationException.class,
            ()->service.loadUser(request()));

        assertEquals("missing_email",exception.getError().getErrorCode());
        verifyNoInteractions(identities,users,encoder);
    }

    @Test void doesNotRecordLastLoginForDisabledLinkedAccount(){
        AppUser customer=new AppUser();customer.setEmail("disabled@example.com");customer.setName("비활성 고객");
        customer.setRole("CUSTOMER");customer.setEnabled(false);customer.setEmailVerified(true);
        OAuthIdentity identity=new OAuthIdentity();identity.setUser(customer);identity.setProvider("NAVER");
        identity.setProviderUserId("naver-user-id");
        OAuth2User remoteUser=remote(Map.of(
            "id","naver-user-id","email","disabled@example.com"));
        when(delegate.loadUser(any())).thenReturn(remoteUser);
        when(identities.findByProviderAndProviderUserId("NAVER","naver-user-id"))
            .thenReturn(Optional.of(identity));

        OAuth2AuthenticationException exception=assertThrows(OAuth2AuthenticationException.class,
            ()->service.loadUser(request()));

        assertEquals("account_disabled",exception.getError().getErrorCode());
        verify(identities,never()).save(any());
    }

    private OAuth2User remote(Map<String,Object> profile){
        OAuth2User user=mock(OAuth2User.class);when(user.getAttributes()).thenReturn(Map.of("response",profile));return user;
    }
    private OAuth2UserRequest request(){
        ClientRegistration registration=ClientRegistration.withRegistrationId("naver")
            .clientId("client").clientSecret("secret")
            .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_POST)
            .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
            .redirectUri("http://localhost/login/oauth2/code/naver")
            .authorizationUri("https://nid.naver.com/oauth2.0/authorize")
            .tokenUri("https://nid.naver.com/oauth2.0/token")
            .userInfoUri("https://openapi.naver.com/v1/nid/me")
            .userNameAttributeName("response").build();
        OAuth2AccessToken token=new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER,"token",
            Instant.now(),Instant.now().plusSeconds(300));
        return new OAuth2UserRequest(registration,token);
    }
}
