package kr.co.kumsungenc.platform.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.util.*;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={
    "app.email-outbox.poll-ms=600000",
    "app.email-outbox.enabled=false",
    "app.malware.enabled=false",
    "app.malware.required=false",
    "app.admin-email=",
    "app.admin-password=",
    "app.quote-recipient=operations@example.com"
})
@AutoConfigureMockMvc
@EnabledIfSystemProperty(named="integration",matches="true")
class CustomerMenuIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper mapper;

    @Test
    void customerMenuFlowPersistsOwnershipHistoryDecisionsAndOutbox() throws Exception {
        String customer="menu-"+UUID.randomUUID()+"@example.com";
        String stranger="other-"+UUID.randomUUID()+"@example.com";
        long customerId=createVerifiedUser(customer);
        createVerifiedUser(stranger);
        String receipt=submitQuote(customer);
        long quoteId=Objects.requireNonNull(jdbc.queryForObject(
            "select id from quote_requests where receipt_number=?",Long.class,receipt));

        mvc.perform(put("/api/portal/quotes/{receipt}/webhard",receipt)
                .with(user(customer).roles("CUSTOMER")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsBytes(Map.of("url","https://webhard.example/customer-folder"))))
            .andExpect(status().isOk());

        MockMultipartFile extra=new MockMultipartFile("files","extra.pdf","application/pdf",
            "%PDF-1.4\n%%EOF".getBytes(StandardCharsets.US_ASCII));
        mvc.perform(multipart("/api/portal/quotes/{receipt}/files",receipt).file(extra)
                .with(user(customer).roles("CUSTOMER")).with(csrf()))
            .andExpect(status().isOk());

        MockMultipartFile estimate=new MockMultipartFile("file","estimate.pdf","application/pdf",
            "%PDF-1.4\n%%EOF".getBytes(StandardCharsets.US_ASCII));
        mvc.perform(multipart("/api/admin/quotes/{id}/documents",quoteId).file(estimate)
                .param("documentType","ESTIMATE").param("title","정식 견적서")
                .with(user("admin@example.com").roles("ADMIN")).with(csrf()))
            .andExpect(status().isOk());
        long estimateId=Objects.requireNonNull(jdbc.queryForObject("""
            select id from quote_documents where quote_request_id=? and document_type='ESTIMATE'
            order by id desc limit 1
            """,Long.class,quoteId));

        MockMultipartFile contract=new MockMultipartFile("file","contract.pdf","application/pdf",
            "%PDF-1.4\n%%EOF".getBytes(StandardCharsets.US_ASCII));
        mvc.perform(multipart("/api/admin/quotes/{id}/documents",quoteId).file(contract)
                .param("documentType","CONTRACT").param("title","계약서")
                .with(user("admin@example.com").roles("ADMIN")).with(csrf()))
            .andExpect(status().isOk());
        long contractId=Objects.requireNonNull(jdbc.queryForObject("""
            select id from quote_documents where quote_request_id=? and document_type='CONTRACT'
            order by id desc limit 1
            """,Long.class,quoteId));

        mvc.perform(post("/api/portal/quotes/{receipt}/documents/{id}/approve",receipt,estimateId)
                .with(user(customer).roles("CUSTOMER")).with(csrf()))
            .andExpect(status().isOk());
        mvc.perform(post("/api/portal/quotes/{receipt}/documents/{id}/approve",receipt,estimateId)
                .with(user(customer).roles("CUSTOMER")).with(csrf()))
            .andExpect(status().isBadRequest());
        mvc.perform(post("/api/portal/quotes/{receipt}/documents/{id}/approve",receipt,contractId)
                .with(user(customer).roles("CUSTOMER")).with(csrf()))
            .andExpect(status().isBadRequest());

        mvc.perform(post("/api/portal/quotes/{receipt}/documents/{id}/contract-decision",receipt,contractId)
                .with(user(customer).roles("CUSTOMER")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsBytes(Map.of("decision","ACCEPTED","note","계약 내용을 확인했습니다."))))
            .andExpect(status().isOk());

        mvc.perform(post("/api/admin/projects")
                .with(user("admin@example.com").roles("ADMIN")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsBytes(Map.of("customerEmail",customer,"name","소유권 프로젝트","progress",10))))
            .andExpect(status().isOk());
        mvc.perform(get("/api/portal/projects").with(user(customer).roles("CUSTOMER")))
            .andExpect(status().isOk()).andExpect(jsonPath("$[0].name").value("소유권 프로젝트"));
        mvc.perform(get("/api/portal/projects").with(user(stranger).roles("CUSTOMER")))
            .andExpect(status().isOk()).andExpect(content().json("[]"));
        long projectId=Objects.requireNonNull(jdbc.queryForObject(
            "select id from projects where customer_user_id=? order by id desc limit 1",Long.class,customerId));
        for(int index=1;index<=30;index++)jdbc.update("""
            insert into projects(customer_email,customer_user_id,name,status,progress)
            values (?,?,?,'PLANNING',0)
            """,customer,customerId,"페이지 테스트 프로젝트 "+index);
        mvc.perform(get("/api/portal/projects").param("limit","26").param("offset","0")
                .with(user(customer).roles("CUSTOMER")))
            .andExpect(status().isOk()).andExpect(jsonPath("$",hasSize(26)));
        mvc.perform(get("/api/portal/projects").param("limit","26").param("offset","25")
                .with(user(customer).roles("CUSTOMER")))
            .andExpect(status().isOk()).andExpect(jsonPath("$",hasSize(6)));
        jdbc.update("""
            insert into projects(customer_email,customer_user_id,name,status,progress)
            select ?,?,'대량 목록 제한 '||n,'PLANNING',0 from generate_series(1,220) n
            """,customer,customerId);
        mvc.perform(get("/api/portal/projects").param("limit","10000")
                .with(user(customer).roles("CUSTOMER")))
            .andExpect(status().isOk()).andExpect(jsonPath("$",hasSize(200)));
        mvc.perform(post("/api/portal/service-requests")
                .with(user(customer).roles("CUSTOMER")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsBytes(Map.of(
                    "projectId",projectId,"title","프로젝트 연결 A/S","details","프로젝트를 선택한 A/S 요청"))))
            .andExpect(status().isOk());
        assertEquals(projectId,jdbc.queryForObject(
            "select project_id from service_requests where customer_user_id=? order by id desc limit 1",Long.class,customerId));

        mvc.perform(post("/api/portal/support").with(user(customer).roles("CUSTOMER")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsBytes(Map.of("subject","통합 문의","message","답변 알림 테스트"))))
            .andExpect(status().isOk());
        long supportId=Objects.requireNonNull(jdbc.queryForObject(
            "select id from support_inquiries where customer_user_id=? order by id desc limit 1",Long.class,customerId));
        mvc.perform(put("/api/admin/support/{id}/answer",supportId)
                .with(user("admin@example.com").roles("ADMIN")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsBytes(Map.of("answer","확인 후 회신드립니다."))))
            .andExpect(status().isOk());

        assertEquals("https://webhard.example/customer-folder",jdbc.queryForObject(
            "select customer_webhard_url from quote_requests where id=?",String.class,quoteId));
        assertTrue(Objects.requireNonNull(jdbc.queryForObject(
            "select count(*) from quote_status_history where quote_request_id=? and status='QUOTED'",Integer.class,quoteId))>0);
        assertEquals("ACCEPTED",jdbc.queryForObject(
            "select contract_decision from quote_documents where id=?",String.class,contractId));
        assertEquals("APPROVED",jdbc.queryForObject(
            "select approval_status from quote_documents where id=?",String.class,estimateId));
        assertEquals(1,jdbc.queryForObject("""
            select count(*) from email_outbox where quote_request_id=? and recipient='operations@example.com'
              and subject like '%전자승인 완료%'
            """,Integer.class,quoteId));
        assertTrue(Objects.requireNonNull(jdbc.queryForObject("""
            select count(*) from email_outbox where lower(recipient)=lower(?)
              and (subject like '%새 문서 등록%' or subject like '%고객센터 답변%')
            """,Integer.class,customer))>=2);
    }

    private long createVerifiedUser(String email) throws Exception {
        Map<String,Object> request=Map.of("email",email,"password","TestPass123!","name","테스트 고객",
            "companyName","통합테스트 주식회사","phone","010-1234-5678","privacyAgreed",true);
        mvc.perform(post("/api/auth/register").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsBytes(request)))
            .andExpect(status().isCreated());
        String body=jdbc.queryForObject("select body from email_outbox where lower(recipient)=lower(?) order by id desc limit 1",
            String.class,email);
        String token=body.substring(body.indexOf("#token=")+7).split("\\s")[0];
        mvc.perform(post("/api/auth/verify").with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsBytes(Map.of("token",token))))
            .andExpect(status().isOk());
        return Objects.requireNonNull(jdbc.queryForObject("select id from app_users where lower(email)=lower(?)",Long.class,email));
    }

    private String submitQuote(String email) throws Exception {
        Map<String,Object> request=new LinkedHashMap<>();
        request.put("companyName","통합테스트 주식회사");request.put("businessNumber","123-45-67890");
        request.put("contactName","테스트 고객");request.put("email",email);request.put("phone","010-1234-5678");
        request.put("siteName","테스트 현장");request.put("siteAddress","서울");
        request.put("productType","산업설비 제작");request.put("subject","고객 메뉴 통합 테스트");
        request.put("details","전체 고객 메뉴 흐름 테스트");request.put("webhardUrl",null);
        request.put("desiredDate",null);request.put("privacyAgreed",true);
        MockMultipartFile json=new MockMultipartFile("request","","application/json",mapper.writeValueAsBytes(request));
        MockMultipartFile pdf=new MockMultipartFile("files","drawing.pdf","application/pdf",
            "%PDF-1.4\n%%EOF".getBytes(StandardCharsets.US_ASCII));
        String body=mvc.perform(multipart("/api/quotes").file(json).file(pdf).with(csrf()))
            .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return mapper.readTree(body).get("receiptNumber").asText();
    }
}
