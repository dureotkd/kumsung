package kr.co.kumsungenc.platform.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.util.*;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={
    "app.email-outbox.poll-ms=600000",
    "app.email-outbox.enabled=false",
    "app.malware.enabled=false",
    "app.malware.required=false",
    "app.admin-email=",
    "app.admin-password="
})
@AutoConfigureMockMvc
@EnabledIfSystemProperty(named="integration",matches="true")
class OwnershipIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper mapper;

    @Test
    void verifiedOwnerCanClaimExistingQuoteButAnotherCustomerCannot() throws Exception {
        String owner="owner-"+UUID.randomUUID()+"@example.com";
        String stranger="stranger-"+UUID.randomUUID()+"@example.com";
        String receipt=submitAnonymousQuote(owner);
        register(owner);

        mvc.perform(formLogin().user(owner).password("TestPass123!"))
            .andExpect(redirectedUrl("/login.html?error"));

        verifyLatestToken(owner);
        mvc.perform(formLogin().user(owner).password("TestPass123!"))
            .andExpect(redirectedUrl("/portal.html"));

        mvc.perform(get("/api/portal/quotes").with(user(owner).roles("CUSTOMER")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].receipt_number").value(receipt));

        register(stranger);verifyLatestToken(stranger);
        mvc.perform(get("/api/portal/quotes").with(user(stranger).roles("CUSTOMER")))
            .andExpect(status().isOk())
            .andExpect(content().json("[]"));

        long quoteId=Objects.requireNonNull(jdbc.queryForObject(
            "select id from quote_requests where receipt_number=?",Long.class,receipt));
        long documentId=Objects.requireNonNull(jdbc.queryForObject("""
            insert into quote_documents(quote_request_id,document_type,title,original_name,stored_name,content_type,file_size)
            values (?,'ESTIMATE','소유권 검증 견적서','estimate.pdf','estimate.pdf','application/pdf',12)
            returning id
            """,Long.class,quoteId));

        mvc.perform(get("/api/portal/quotes/{receipt}",receipt)
                .with(user(stranger).roles("CUSTOMER")))
            .andExpect(status().isNotFound());

        mvc.perform(get("/api/portal/quotes/{receipt}/documents/{documentId}",receipt,documentId)
                .with(user(stranger).roles("CUSTOMER")))
            .andExpect(status().isNotFound());

        Long ownerId=jdbc.queryForObject("select owner_user_id from quote_requests where receipt_number=?",Long.class,receipt);
        assertNotNull(ownerId);
        Integer consents=jdbc.queryForObject("select count(*) from privacy_consents where lower(email)=lower(?)",Integer.class,owner);
        assertEquals(2,consents);
    }

    @Test
    void relationshipIdsCannotCrossCustomersAndAdminCanDownloadDrawing() throws Exception {
        String owner="relation-owner-"+UUID.randomUUID()+"@example.com";
        String stranger="relation-stranger-"+UUID.randomUUID()+"@example.com";
        register(owner);verifyLatestToken(owner);
        register(stranger);verifyLatestToken(stranger);
        long ownerId=Objects.requireNonNull(jdbc.queryForObject(
            "select id from app_users where lower(email)=lower(?)",Long.class,owner));
        long projectId=Objects.requireNonNull(jdbc.queryForObject("""
            insert into projects(customer_email,customer_user_id,name,status)
            values (?,?,?,'PLANNING') returning id
            """,Long.class,owner,ownerId,"고객 소유권 검증 프로젝트"));

        mvc.perform(post("/api/portal/service-requests")
                .with(user(stranger).roles("CUSTOMER")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsBytes(Map.of(
                    "projectId",projectId,"title","타 고객 프로젝트 접근","details","차단되어야 합니다."))))
            .andExpect(status().isBadRequest());

        mvc.perform(post("/api/admin/contracts")
                .with(user("admin@example.com").roles("ADMIN")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsBytes(Map.of(
                    "customerEmail",stranger,"projectId",projectId,
                    "contractNumber","CROSS-"+UUID.randomUUID(),"title","교차 소유권 계약"))))
            .andExpect(status().isBadRequest());

        String receipt=submitAnonymousQuote(owner);
        long quoteId=Objects.requireNonNull(jdbc.queryForObject(
            "select id from quote_requests where receipt_number=?",Long.class,receipt));
        long fileId=Objects.requireNonNull(jdbc.queryForObject(
            "select id from quote_attachments where quote_request_id=? order by id limit 1",Long.class,quoteId));

        mvc.perform(get("/api/admin/quotes/{quoteId}/files/{fileId}",quoteId,fileId)
                .with(user("admin@example.com").roles("ADMIN")))
            .andExpect(status().isOk())
            .andExpect(header().string("Cache-Control",containsString("no-store")))
            .andExpect(header().string("Content-Disposition",containsString("drawing.pdf")));

        mvc.perform(put("/api/admin/projects/{id}",projectId)
                .with(user("admin@example.com").roles("ADMIN")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsBytes(Map.of("status","<script>alert(1)</script>","progress",20))))
            .andExpect(status().isBadRequest());
    }

    @Test
    void administratorEmailIsNeverAssignedAsCustomerQuoteOwner() throws Exception {
        String admin="quote-admin-"+UUID.randomUUID()+"@example.com";
        jdbc.update("""
            insert into app_users(email,password_hash,name,company_name,role,admin_role,enabled,email_verified,verified_at)
            values (?,'not-used','관리자','(주)금성이엔씨','ADMIN','ADMIN',true,true,current_timestamp)
            """,admin);
        String receipt=submitAnonymousQuote(admin);
        Long ownerId=jdbc.queryForObject("select owner_user_id from quote_requests where receipt_number=?",Long.class,receipt);
        assertNull(ownerId);
    }

    private String submitAnonymousQuote(String email) throws Exception {
        Map<String,Object> request=new LinkedHashMap<>();
        request.put("companyName","통합테스트 주식회사");request.put("businessNumber","123-45-67890");
        request.put("contactName","테스트 고객");request.put("email",email);request.put("phone","010-1234-5678");
        request.put("siteName","테스트 현장");request.put("siteAddress","서울");
        request.put("productType","산업설비 제작");request.put("subject","소유권 통합 테스트");
        request.put("details","이메일 인증 전 견적 소유권 테스트");request.put("desiredDate",null);request.put("privacyAgreed",true);
        MockMultipartFile json=new MockMultipartFile("request","","application/json",mapper.writeValueAsBytes(request));
        MockMultipartFile pdf=new MockMultipartFile("files","drawing.pdf","application/pdf","%PDF-1.4\n%%EOF".getBytes());
        String body=mvc.perform(multipart("/api/quotes").file(json).file(pdf).with(csrf()))
            .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return mapper.readTree(body).get("receiptNumber").asText();
    }

    private void register(String email) throws Exception {
        Map<String,Object> request=Map.of("email",email,"password","TestPass123!","name","테스트 고객",
            "companyName","통합테스트 주식회사","phone","010-1234-5678","privacyAgreed",true);
        mvc.perform(post("/api/auth/register").with(csrf()).contentType(MediaType.APPLICATION_JSON)
            .content(mapper.writeValueAsBytes(request))).andExpect(status().isCreated());
    }

    private void verifyLatestToken(String email) throws Exception {
        String body=jdbc.queryForObject("select body from email_outbox where lower(recipient)=lower(?) order by id desc limit 1",String.class,email);
        String token=body.substring(body.indexOf("#token=")+7).split("\\s")[0];
        mvc.perform(get("/api/auth/verify").param("token",token))
            .andExpect(status().isMethodNotAllowed());
        assertFalse(Boolean.TRUE.equals(jdbc.queryForObject(
            "select email_verified from app_users where lower(email)=lower(?)",Boolean.class,email)));
        mvc.perform(post("/api/auth/verify").with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsBytes(Map.of("token",token))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.message").value("이메일 인증이 완료되었습니다."));
        assertTrue(Boolean.TRUE.equals(jdbc.queryForObject(
            "select email_verified from app_users where lower(email)=lower(?)",Boolean.class,email)));
        mvc.perform(post("/api/auth/verify").with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsBytes(Map.of("token",token))))
            .andExpect(status().isBadRequest());
    }
}
