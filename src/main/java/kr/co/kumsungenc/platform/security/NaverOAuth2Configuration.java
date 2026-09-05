package kr.co.kumsungenc.platform.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.*;
import org.springframework.security.oauth2.client.registration.*;
import org.springframework.security.oauth2.core.*;
import org.springframework.util.StringUtils;

@Configuration(proxyBeanMethods=false)
@ConditionalOnProperty(name="app.naver.enabled",havingValue="true")
public class NaverOAuth2Configuration {
    @Bean
    ClientRegistrationRepository naverClientRegistrationRepository(
        @Value("${app.naver.client-id:}") String clientId,
        @Value("${app.naver.client-secret:}") String clientSecret,
        @Value("${app.base-url}") String baseUrl){
        if(!StringUtils.hasText(clientId)||!StringUtils.hasText(clientSecret))
            throw new IllegalStateException("네이버 로그인 사용 시 NAVER_CLIENT_ID와 NAVER_CLIENT_SECRET이 필요합니다.");
        String normalizedBaseUrl=baseUrl.replaceAll("/+$","");
        ClientRegistration registration=ClientRegistration.withRegistrationId("naver")
            .clientName("네이버")
            .clientId(clientId.trim())
            .clientSecret(clientSecret.trim())
            .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_POST)
            .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
            .redirectUri(normalizedBaseUrl+"/login/oauth2/code/{registrationId}")
            .authorizationUri("https://nid.naver.com/oauth2.0/authorize")
            .tokenUri("https://nid.naver.com/oauth2.0/token")
            .userInfoUri("https://openapi.naver.com/v1/nid/me")
            .userNameAttributeName("response")
            .build();
        return new InMemoryClientRegistrationRepository(registration);
    }
}
