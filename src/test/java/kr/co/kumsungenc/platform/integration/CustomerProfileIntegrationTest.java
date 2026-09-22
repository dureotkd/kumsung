package kr.co.kumsungenc.platform.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import kr.co.kumsungenc.platform.file.FileStorageService;
import kr.co.kumsungenc.platform.shop.TossPaymentsClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"app.email-outbox.enabled=false","app.google-sheets.enabled=false",
    "app.malware.enabled=false","app.malware.required=false","app.admin-email=","app.admin-password=",
    "app.storage.local.root=target/test-profile-uploads"})
@AutoConfigureMockMvc
@Transactional
@EnabledIfSystemProperty(named="integration",matches="true")
class CustomerProfileIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper mapper;
    @Autowired EntityManager entityManager;
    @MockitoBean TossPaymentsClient toss;
    @MockitoBean FileStorageService storage;

    @Test void anonymousPagesKeepLoginOnPublicHostWhileApisRemainUnauthorized() throws Exception {
        for(String page:List.of("/profile.html","/portal.html")){
            mvc.perform(get(page).header("Host","origin.kumsungenc.co.kr").accept(MediaType.TEXT_HTML))
                .andExpect(status().isFound()).andExpect(header().string("Location","/login.html"));
        }
        mvc.perform(get("/api/auth/me").accept(MediaType.TEXT_HTML))
            .andExpect(status().isUnauthorized()).andExpect(header().doesNotExist("Location"));
    }

    @Test void naverProfileAppearsInAdminAndQuoteBelongsToSessionNotContactEmail() throws Exception {
        String email="naver-"+UUID.randomUUID()+"@example.com";
        long id=createCustomer(email,false);
        String otherEmail="other-"+UUID.randomUUID()+"@example.com";
        createCustomer(otherEmail,true);
        mvc.perform(get("/api/auth/me").with(naver(email)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.phone").value(""))
            .andExpect(jsonPath("$.profileComplete").value("false"));
        mvc.perform(multipart("/api/quotes").file(quote(otherEmail)).file(drawing()).with(naver(email)).with(csrf()))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message",containsString("회원 기본정보")));
        assertEquals(0,jdbc.queryForObject("select count(*) from quote_requests where owner_user_id=?",Integer.class,id));

        mvc.perform(put("/api/auth/profile").with(naver(email)).with(csrf())
            .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsBytes(profile())))
            .andExpect(status().isOk()).andExpect(jsonPath("$.profileComplete").value("true"))
            .andExpect(jsonPath("$.email").value(email)).andExpect(jsonPath("$.phone").value("010-1234-5678"));
        mvc.perform(get("/api/admin/customers").with(user("admin@example.com").roles("ADMIN")))
            .andExpect(status().isOk()).andExpect(jsonPath("$[?(@.email == '"+email+"')].company_name",hasItem("테스트 회사")))
            .andExpect(jsonPath("$[?(@.email == '"+email+"')].name",hasItem("실제 담당자")))
            .andExpect(jsonPath("$[?(@.email == '"+email+"')].phone",hasItem("010-1234-5678")));
        assertEquals(1,jdbc.queryForObject("select count(*) from privacy_consents where subject_type='USER' and subject_id=?",Integer.class,id));

        String body=mvc.perform(multipart("/api/quotes").file(quote(otherEmail)).file(drawing()).with(naver(email)).with(csrf()))
            .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String receipt=mapper.readTree(body).get("receiptNumber").asText();
        assertEquals(id,jdbc.queryForObject("select owner_user_id from quote_requests where receipt_number=?",Long.class,receipt));
        assertEquals(otherEmail,jdbc.queryForObject("select email from quote_requests where receipt_number=?",String.class,receipt));
        mvc.perform(get("/api/portal/quotes/{receipt}",receipt).with(naver(email))).andExpect(status().isOk());
        mvc.perform(get("/api/portal/quotes/{receipt}",receipt).with(user(otherEmail).roles("CUSTOMER"))).andExpect(status().isNotFound());
        assertEquals("테스트 회사",jdbc.queryForObject("select company_name from app_users where id=?",String.class,id));
    }

    @Test void profileValidationAndPermissionsCannotBeBypassed() throws Exception {
        String email="profile-"+UUID.randomUUID()+"@example.com";long id=createCustomer(email,false);
        byte[] valid=mapper.writeValueAsBytes(profile());
        mvc.perform(put("/api/auth/profile").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(valid))
            .andExpect(status().isUnauthorized());
        mvc.perform(put("/api/auth/profile").with(naver(email)).contentType(MediaType.APPLICATION_JSON).content(valid))
            .andExpect(status().isForbidden());
        for(Map.Entry<String,Object> invalid:Map.<String,Object>of("companyName"," ","name"," ","phone","-------","privacyAgreed",false).entrySet()){
            Map<String,Object> input=new HashMap<>(profile());input.put(invalid.getKey(),invalid.getValue());
            mvc.perform(put("/api/auth/profile").with(naver(email)).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsBytes(input))).andExpect(status().isBadRequest());
        }
        jdbc.update("update app_users set enabled=false where id=?",id);
        entityManager.clear();
        mvc.perform(put("/api/auth/profile").with(naver(email)).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(valid))
            .andExpect(status().isForbidden());
        assertNull(jdbc.queryForObject("select company_name from app_users where id=?",String.class,id));
    }

    @Test void guestQuoteDoesNotOverwriteAnExistingMembersProfile() throws Exception {
        String email="guest-"+UUID.randomUUID()+"@example.com";long id=createCustomer(email,true);
        mvc.perform(multipart("/api/quotes").file(quote(email)).file(drawing()).with(csrf())).andExpect(status().isCreated());
        assertEquals("기존 회사",jdbc.queryForObject("select company_name from app_users where id=?",String.class,id));
    }

    private Map<String,Object> profile(){return Map.of("name","실제 담당자","companyName","테스트 회사","phone","010-1234-5678","privacyAgreed",true);}
    private MockMultipartFile drawing(){return new MockMultipartFile("files","drawing.pdf","application/pdf","%PDF-1.4\n%%EOF".getBytes(java.nio.charset.StandardCharsets.UTF_8));}
    private RequestPostProcessor naver(String email){return oauth2Login().oauth2User(new DefaultOAuth2User(
        Set.of(new SimpleGrantedAuthority("ROLE_CUSTOMER")),Map.of("email",email,"name","네이버 회원","id","test-naver-id"),"email"));}
    private long createCustomer(String email,boolean complete){
        long id=jdbc.queryForObject("""
            insert into app_users(email,password_hash,name,company_name,phone,role,enabled,email_verified,verified_at)
            values (?,'unused',?,?,?,'CUSTOMER',true,true,current_timestamp) returning id
            """,Long.class,email,complete?"기존 담당자":"네이버 회원",complete?"기존 회사":null,complete?"010-9999-8888":null);
        jdbc.update("insert into oauth_identities(user_id,provider,provider_user_id) values (?,'NAVER',?)",id,UUID.randomUUID().toString());
        return id;
    }
    private MockMultipartFile quote(String email) throws Exception {
        Map<String,Object> input=Map.of("companyName","견적 현장 회사","contactName","견적 담당자","email",email,
            "phone","010-7777-6666","productType","소화전함","subject","회원 연결 테스트","details","테스트 견적입니다.","privacyAgreed",true);
        return new MockMultipartFile("request","","application/json",mapper.writeValueAsBytes(input));
    }
}
