package kr.co.kumsungenc.platform.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.client.registration.*;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;

import static org.junit.jupiter.api.Assertions.*;

class NaverOAuth2ConfigurationTest {
    @Test void buildsNaverRegistrationFromExternalConfiguration(){
        ClientRegistrationRepository repository=new NaverOAuth2Configuration()
            .naverClientRegistrationRepository("client-id","client-secret","https://example.com/");
        ClientRegistration registration=((InMemoryClientRegistrationRepository)repository)
            .findByRegistrationId("naver");

        assertNotNull(registration);
        assertEquals(ClientAuthenticationMethod.CLIENT_SECRET_POST,registration.getClientAuthenticationMethod());
        assertEquals("https://example.com/login/oauth2/code/{registrationId}",registration.getRedirectUri());
        assertEquals("https://nid.naver.com/oauth2.0/authorize",
            registration.getProviderDetails().getAuthorizationUri());
        assertEquals("https://openapi.naver.com/v1/nid/me",
            registration.getProviderDetails().getUserInfoEndpoint().getUri());
    }

    @Test void rejectsEnabledLoginWithoutCredentials(){
        IllegalStateException error=assertThrows(IllegalStateException.class,()->new NaverOAuth2Configuration()
            .naverClientRegistrationRepository("","","https://example.com"));
        assertTrue(error.getMessage().contains("NAVER_CLIENT_ID"));
    }
}
