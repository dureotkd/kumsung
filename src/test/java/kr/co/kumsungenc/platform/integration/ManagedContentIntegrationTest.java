package kr.co.kumsungenc.platform.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import kr.co.kumsungenc.platform.content.ManagedContentService;
import kr.co.kumsungenc.platform.shop.ShopAdminAccessService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={
    "app.email-outbox.enabled=false","app.google-sheets.enabled=false",
    "app.malware.enabled=false","app.malware.required=false",
    "app.admin-email=","app.admin-password=","app.storage.local.root=target/test-content-uploads"
})
@AutoConfigureMockMvc
@EnabledIfSystemProperty(named="integration",matches="true")
class ManagedContentIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired ManagedContentService service;
    Long innovationId;
    Long postId;

    @AfterEach void cleanup(){
        if(innovationId!=null)try{service.deleteInnovation(innovationId);}catch(Exception ignored){}
        if(postId!=null)try{service.deletePost(postId);}catch(Exception ignored){}
    }

    @Test
    void publicShopUsesFixedCatalogWhileAdminKeepsManagedProducts() throws Exception {
        mvc.perform(get("/api/public/shop/products"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(12))
            .andExpect(jsonPath("$[0].code").value("DRY_PD"))
            .andExpect(jsonPath("$[4].code").value("SITE_GANGNAM"))
            .andExpect(jsonPath("$[0].imageUrl").isNotEmpty());
        mvc.perform(get("/api/shop-admin/products").param("limit","30")
                .sessionAttr(ShopAdminAccessService.VERIFIED_AT,System.currentTimeMillis())
                .with(user("shop@example.com").authorities(new SimpleGrantedAuthority("ADMIN_SCOPE_SHOP_ADMIN"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(greaterThanOrEqualTo(25)));
    }

    @Test
    void administratorUploadsProtectedInnovationResourceAndCustomerDownloadsWithPassword() throws Exception {
        MockMultipartFile image=png("image","technology.png");
        MockMultipartFile file=new MockMultipartFile("file","technology.pdf","application/pdf",
            "%PDF-1.4\nprotected technical resource\n%%EOF".getBytes(StandardCharsets.US_ASCII));
        String response=mvc.perform(multipart("/api/admin/content/innovation").file(image).file(file)
                .param("title","기술혁신 통합 테스트").param("description","이미지 기반 기술자료")
                .param("category","PATENT_CERT")
                .param("displayOrder","1").param("published","true").param("password","download-1234")
                .with(user("admin@example.com").roles("ADMIN")).with(csrf()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.password_hash").doesNotExist())
            .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        innovationId=mapper.readTree(response).get("id").asLong();

        mvc.perform(get("/api/public/content/innovation"))
            .andExpect(status().isOk()).andExpect(jsonPath("$[?(@.id == "+innovationId+")].title").value(hasItem("기술혁신 통합 테스트")));
        mvc.perform(get("/api/public/content/innovation/{id}/image",innovationId))
            .andExpect(status().isOk()).andExpect(content().contentType("image/png"));
        mvc.perform(post("/api/public/content/innovation/{id}/download",innovationId).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"password\":\"wrong\"}"))
            .andExpect(status().isForbidden()).andExpect(jsonPath("$.message").value("다운로드 비밀번호가 올바르지 않습니다."));
        mvc.perform(post("/api/public/content/innovation/{id}/download",innovationId).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"password\":\"download-1234\"}"))
            .andExpect(status().isOk()).andExpect(header().string("Content-Disposition",containsString("technology.pdf")))
            .andExpect(content().bytes(file.getBytes()));
    }

    @Test
    void administratorUploadsCompanyNewsImageAndCustomerCanViewIt() throws Exception {
        MockMultipartFile image=png("image","company-news.png");
        String response=mvc.perform(multipart("/api/admin/content/posts").file(image)
                .param("type","COMPANY_NEWS").param("title","새 회사소식")
                .param("content","고객센터 이미지 게시물입니다.").param("published","true").param("pinned","true")
                .with(user("admin@example.com").roles("ADMIN")).with(csrf()))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        postId=mapper.readTree(response).get("id").asLong();

        mvc.perform(get("/api/public/content/posts").param("type","COMPANY_NEWS"))
            .andExpect(status().isOk()).andExpect(jsonPath("$[?(@.id == "+postId+")].title").value(hasItem("새 회사소식")));
        mvc.perform(get("/api/public/content/posts/{id}/image",postId))
            .andExpect(status().isOk()).andExpect(content().contentType("image/png"));
    }

    private MockMultipartFile png(String part,String name){
        return new MockMultipartFile(part,name,"image/png",new byte[]{(byte)0x89,0x50,0x4e,0x47,0x0d,0x0a,0x1a,0x0a,1,2,3});
    }
}
