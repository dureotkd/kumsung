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
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={
    "app.email-outbox.enabled=false","app.google-sheets.enabled=false",
    "app.malware.enabled=false","app.malware.required=false",
    "app.admin-email=","app.admin-password=","app.quote-recipient=operations@example.com",
    "app.support-recipient=support-operations@example.com","app.support-email=support@example.com"
})
@AutoConfigureMockMvc
@EnabledIfSystemProperty(named="integration",matches="true")
class ShopAndSheetsIntegrationTest {
    @Autowired MockMvc mvc;@Autowired JdbcTemplate jdbc;@Autowired ObjectMapper mapper;

    @Test
    void publicShopSupportAndQuotePersistDatabaseEmailAndSelectedSheetOutboxes() throws Exception {
        String email="shop-"+UUID.randomUUID()+"@example.com";long userId=createVerifiedUser(email);
        Long productId=Objects.requireNonNull(jdbc.queryForObject("select id from shop_products where code='DRY_PD'",Long.class));

        Map<String,Object> shop=new LinkedHashMap<>();shop.put("companyName","쇼핑 통합테스트");shop.put("contactName","홍길동 과장");
        shop.put("phone","010-2222-3333");shop.put("email",email);shop.put("message","납기와 제작 가능 여부를 알려주세요.");shop.put("privacyAgreed",true);
        shop.put("items",List.of(Map.of("productId",productId,"quantity",2,"specifications","폭 1200mm","attachmentIndexes",List.of(0))));
        MockMultipartFile shopJson=new MockMultipartFile("request","","application/json",mapper.writeValueAsBytes(shop));
        MockMultipartFile shopDrawing=new MockMultipartFile("files","dry-pd.pdf","application/pdf","%PDF-1.4\n%%EOF".getBytes(StandardCharsets.US_ASCII));
        String shopBody=mvc.perform(multipart("/api/public/shop/inquiries").file(shopJson).file(shopDrawing).with(csrf()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.receiptNumber").isNotEmpty()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        String shopReceipt=mapper.readTree(shopBody).get("receiptNumber").asText();
        Long shopId=jdbc.queryForObject("select id from shop_inquiries where receipt_number=?",Long.class,shopReceipt);
        assertNotNull(shopId);assertEquals(userId,jdbc.queryForObject("select customer_user_id from shop_inquiries where id=?",Long.class,shopId));
        assertEquals(1,jdbc.queryForObject("select count(*) from shop_inquiry_items where shop_inquiry_id=?",Integer.class,shopId));
        assertEquals(1,jdbc.queryForObject("select count(*) from shop_inquiry_attachments where shop_inquiry_id=?",Integer.class,shopId));
        assertEquals(0,jdbc.queryForObject("select count(*) from sheet_outbox where reference_type='SHOP_INQUIRY' and reference_id=?",Integer.class,shopId));
        assertEquals(2,jdbc.queryForObject("select count(*) from email_outbox where reference_type='SHOP_INQUIRY' and reference_id=?",Integer.class,shopId));

        Map<String,Object> support=Map.of("companyName","쇼핑 통합테스트","contactName","홍길동 과장","phone","010-2222-3333",
            "email",email,"subject","도면 작성 문의","message","견적 도면 작성 방법을 알려주세요.","privacyAgreed",true);
        String supportBody=mvc.perform(post("/api/public/support").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsBytes(support)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.receiptNumber").isNotEmpty()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        String supportReceipt=mapper.readTree(supportBody).get("receiptNumber").asText();
        Long supportId=jdbc.queryForObject("select id from support_inquiries where receipt_number=?",Long.class,supportReceipt);
        assertNotNull(supportId);assertEquals("PUBLIC",jdbc.queryForObject("select source from support_inquiries where id=?",String.class,supportId));
        assertEquals(0,jdbc.queryForObject("select count(*) from sheet_outbox where reference_type='SUPPORT_INQUIRY' and reference_id=?",Integer.class,supportId));
        assertEquals(2,jdbc.queryForObject("select count(*) from email_outbox where reference_type='SUPPORT_INQUIRY' and reference_id=?",Integer.class,supportId));

        String quoteReceipt=submitQuote(email);Long quoteId=jdbc.queryForObject("select id from quote_requests where receipt_number=?",Long.class,quoteReceipt);
        assertEquals(0,jdbc.queryForObject("select count(*) from sheet_outbox where reference_type='QUOTE' and reference_id=?",Integer.class,quoteId));

        mvc.perform(get("/api/portal/shop-inquiries").with(user(email).roles("CUSTOMER")))
            .andExpect(status().isOk()).andExpect(jsonPath("$[0].receipt_number").value(shopReceipt));
        mvc.perform(get("/api/admin/shop/inquiries").with(user("admin@example.com").roles("ADMIN")))
            .andExpect(status().isOk()).andExpect(jsonPath("$[0].receipt_number").value(shopReceipt));
        Long shopFileId=jdbc.queryForObject("select id from shop_inquiry_attachments where shop_inquiry_id=?",Long.class,shopId);
        mvc.perform(get("/api/admin/shop/inquiries/{id}",shopId).with(user("admin@example.com").roles("ADMIN")))
            .andExpect(status().isOk()).andExpect(jsonPath("$.attachments[0].original_name").value("dry-pd.pdf"));
        mvc.perform(get("/api/admin/shop/inquiries/{id}/files/{fileId}",shopId,shopFileId).with(user("admin@example.com").roles("ADMIN")))
            .andExpect(status().isOk()).andExpect(header().string("Content-Disposition",org.hamcrest.Matchers.containsString("dry-pd.pdf")));
        mvc.perform(put("/api/admin/shop/inquiries/{id}",shopId).with(user("admin@example.com").roles("ADMIN")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsBytes(Map.of("status","CONTACTED","adminNote","유선 상담 완료"))))
            .andExpect(status().isOk());
        assertEquals("CONTACTED",jdbc.queryForObject("select status from shop_inquiries where id=?",String.class,shopId));
        assertTrue(Objects.requireNonNull(jdbc.queryForObject("select count(*) from email_outbox where lower(recipient)=lower(?)",Integer.class,email))>=4);
    }

    @Test
    void publicInquiryValidationRejectsMissingProductAndConsent() throws Exception {
        Map<String,Object> invalid=new LinkedHashMap<>();invalid.put("companyName","테스트");invalid.put("contactName","담당자");invalid.put("phone","010-1234-5678");
        invalid.put("email","test@example.com");invalid.put("message","문의");invalid.put("privacyAgreed",false);invalid.put("items",List.of());
        MockMultipartFile invalidJson=new MockMultipartFile("request","","application/json",mapper.writeValueAsBytes(invalid));
        mvc.perform(multipart("/api/public/shop/inquiries").file(invalidJson).with(csrf()))
            .andExpect(status().isBadRequest());
    }

    @Test
    void supportInquiryIsIdempotentAuditedAndFailedOutboxesCanBeRetried() throws Exception {
        String key=UUID.randomUUID().toString();String email="support-"+UUID.randomUUID()+"@example.com";
        Map<String,Object> support=new LinkedHashMap<>();support.put("companyName","운영 통합테스트");support.put("contactName","김담당 과장");
        support.put("phone","010-9876-5432");support.put("email",email);support.put("subject","운영 문의");
        support.put("message","중복 접수와 관리자 처리 이력을 확인합니다.");support.put("privacyAgreed",true);support.put("submissionKey",key);support.put("website","");
        String first=mvc.perform(post("/api/public/support").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsBytes(support)))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        String second=mvc.perform(post("/api/public/support").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsBytes(support)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.message").value("이미 등록된 고객문의입니다."))
            .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        String receipt=mapper.readTree(first).get("receiptNumber").asText();
        assertEquals(receipt,mapper.readTree(second).get("receiptNumber").asText());
        assertEquals(1,jdbc.queryForObject("select count(*) from support_inquiries where submission_key=?::uuid",Integer.class,key));
        Long id=jdbc.queryForObject("select id from support_inquiries where receipt_number=?",Long.class,receipt);

        mvc.perform(put("/api/admin/support/{id}",id).with(user("operator@example.com").roles("ADMIN")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsBytes(Map.of(
                    "status","ANSWERED","answer","담당자가 확인했습니다.","internalNote","유선 연락 예정","assignedTo","김운영"))))
            .andExpect(status().isOk());
        assertEquals("operator@example.com",jdbc.queryForObject("select answered_by from support_inquiries where id=?",String.class,id));
        assertEquals(1,jdbc.queryForObject("select count(*) from support_inquiry_history where support_inquiry_id=?",Integer.class,id));
        assertEquals(1,jdbc.queryForObject("select count(*) from audit_logs where action='SUPPORT_INQUIRY_STATUS' and target_id=?",Integer.class,Long.toString(id)));
        assertEquals(3,jdbc.queryForObject("select count(*) from email_outbox where reference_type='SUPPORT_INQUIRY' and reference_id=?",Integer.class,id));
        mvc.perform(get("/api/admin/support/{id}",id).with(user("operator@example.com").roles("ADMIN")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.receipt_number").value(receipt))
            .andExpect(jsonPath("$.history[0].status").value("ANSWERED"))
            .andExpect(jsonPath("$.emails.length()").value(3));

        Long emailOutboxId=jdbc.queryForObject("insert into email_outbox(recipient,subject,body,status) values ('retry@example.com','retry','retry','FAILED') returning id",Long.class);
        mvc.perform(post("/api/admin/email/outbox/{id}/retry",emailOutboxId).with(user("operator@example.com").roles("ADMIN")).with(csrf()))
            .andExpect(status().isOk());
        assertEquals("PENDING",jdbc.queryForObject("select status from email_outbox where id=?",String.class,emailOutboxId));
        Long sheetOutboxId=jdbc.queryForObject("insert into sheet_outbox(event_type,reference_type,reference_id,payload,status) values ('TEST','TEST',1,'{}','FAILED') returning id",Long.class);
        mvc.perform(post("/api/admin/integrations/sheets/outbox/{id}/retry",sheetOutboxId).with(user("operator@example.com").roles("ADMIN")).with(csrf()))
            .andExpect(status().isOk());
        assertEquals("PENDING",jdbc.queryForObject("select status from sheet_outbox where id=?",String.class,sheetOutboxId));

        support.put("submissionKey",UUID.randomUUID().toString());support.put("website","spam.example");
        mvc.perform(post("/api/public/support").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsBytes(support)))
            .andExpect(status().isBadRequest());
    }

    @Test
    void accountDeletionExportsThenAnonymizesInquiryDataAndDeletesShopFiles() throws Exception {
        String email="delete-"+UUID.randomUUID()+"@example.com";long userId=createVerifiedUser(email);
        Long productId=Objects.requireNonNull(jdbc.queryForObject("select id from shop_products where code='DRY_PD'",Long.class));
        Map<String,Object> shop=new LinkedHashMap<>();shop.put("companyName","삭제 대상 회사");shop.put("contactName","삭제 대상 담당자");
        shop.put("phone","010-5555-6666");shop.put("email",email);shop.put("message","삭제할 고객 문의 내용");shop.put("privacyAgreed",true);
        shop.put("items",List.of(Map.of("productId",productId,"quantity",1,"specifications","삭제할 제품별 요청","attachmentIndexes",List.of(0))));
        MockMultipartFile shopJson=new MockMultipartFile("request","","application/json",mapper.writeValueAsBytes(shop));
        MockMultipartFile drawing=new MockMultipartFile("files","delete-drawing.pdf","application/pdf","%PDF-1.4\n%%EOF".getBytes(StandardCharsets.US_ASCII));
        String shopBody=mvc.perform(multipart("/api/public/shop/inquiries").file(shopJson).file(drawing).with(csrf()))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        String shopReceipt=mapper.readTree(shopBody).get("receiptNumber").asText();
        Long shopId=Objects.requireNonNull(jdbc.queryForObject("select id from shop_inquiries where receipt_number=?",Long.class,shopReceipt));
        String storedName=jdbc.queryForObject("select stored_name from shop_inquiry_attachments where shop_inquiry_id=?",String.class,shopId);
        Path storedFile=Path.of("uploads","shop",shopReceipt,Objects.requireNonNull(storedName)).toAbsolutePath().normalize();
        assertTrue(Files.isRegularFile(storedFile));

        Map<String,Object> support=Map.of("companyName","삭제 대상 회사","contactName","삭제 대상 담당자","phone","010-5555-6666",
            "email",email,"subject","삭제할 문의 제목","message","삭제할 문의 본문","privacyAgreed",true);
        String supportBody=mvc.perform(post("/api/public/support").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsBytes(support)))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        Long supportId=Objects.requireNonNull(jdbc.queryForObject("select id from support_inquiries where receipt_number=?",Long.class,
            mapper.readTree(supportBody).get("receiptNumber").asText()));

        mvc.perform(get("/api/portal/privacy/export").with(user(email).roles("CUSTOMER")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.shopInquiries[0].id").value(shopId))
            .andExpect(jsonPath("$.shopInquiryAttachments[0].original_name").value("delete-drawing.pdf"));

        mvc.perform(delete("/api/portal/privacy/account").with(user(email).roles("CUSTOMER")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsBytes(Map.of("password","TestPass123!"))))
            .andExpect(status().isOk());

        String alias="deleted-"+userId+"@invalid.local";
        assertEquals(0,jdbc.queryForObject("select count(*) from app_users where id=?",Integer.class,userId));
        assertEquals(alias,jdbc.queryForObject("select email from shop_inquiries where id=?",String.class,shopId));
        assertEquals("계정 삭제 요청에 따라 개인정보가 제거되었습니다.",jdbc.queryForObject("select message from shop_inquiries where id=?",String.class,shopId));
        assertNull(jdbc.queryForObject("select specifications from shop_inquiry_items where shop_inquiry_id=?",String.class,shopId));
        assertEquals(0,jdbc.queryForObject("select count(*) from shop_inquiry_attachments where shop_inquiry_id=?",Integer.class,shopId));
        assertEquals("탈퇴 회원",jdbc.queryForObject("select contact_name from support_inquiries where id=?",String.class,supportId));
        assertEquals(0,jdbc.queryForObject("select count(*) from email_outbox where lower(recipient)=lower(?)",Integer.class,email));
        assertEquals(0,jdbc.queryForObject("select count(*) from email_outbox where reference_type='SHOP_INQUIRY' and reference_id=?",Integer.class,shopId));
        assertEquals(0,jdbc.queryForObject("select count(*) from privacy_consents where lower(email)=lower(?)",Integer.class,email));
        assertFalse(Files.exists(storedFile));
    }

    private long createVerifiedUser(String email) throws Exception {
        Map<String,Object> request=Map.of("email",email,"password","TestPass123!","name","테스트 고객","companyName","쇼핑 통합테스트","phone","010-2222-3333","privacyAgreed",true);
        mvc.perform(post("/api/auth/register").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsBytes(request))).andExpect(status().isCreated());
        String body=jdbc.queryForObject("select body from email_outbox where lower(recipient)=lower(?) order by id desc limit 1",String.class,email);
        String token=body.substring(body.indexOf("#token=")+7).split("\\s")[0];
        mvc.perform(post("/api/auth/verify").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsBytes(Map.of("token",token)))).andExpect(status().isOk());
        return Objects.requireNonNull(jdbc.queryForObject("select id from app_users where lower(email)=lower(?)",Long.class,email));
    }

    private String submitQuote(String email) throws Exception {
        Map<String,Object> request=new LinkedHashMap<>();request.put("companyName","쇼핑 통합테스트");request.put("businessNumber",null);request.put("contactName","홍길동 과장");
        request.put("email",email);request.put("phone","010-2222-3333");request.put("siteName",null);request.put("siteAddress",null);request.put("productType","건식PD");
        request.put("subject","온라인 견적 고객문의");request.put("details","제품 제작 문의");request.put("webhardUrl",null);request.put("desiredDate",null);request.put("privacyAgreed",true);
        MockMultipartFile json=new MockMultipartFile("request","","application/json",mapper.writeValueAsBytes(request));
        MockMultipartFile pdf=new MockMultipartFile("files","drawing.pdf","application/pdf","%PDF-1.4\n%%EOF".getBytes(StandardCharsets.US_ASCII));
        String body=mvc.perform(multipart("/api/quotes").file(json).file(pdf).with(csrf())).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return mapper.readTree(body).get("receiptNumber").asText();
    }
}
