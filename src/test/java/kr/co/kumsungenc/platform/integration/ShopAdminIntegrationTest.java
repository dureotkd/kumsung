package kr.co.kumsungenc.platform.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.mock.web.MockHttpSession;
import kr.co.kumsungenc.platform.shop.ShopAdminAccessService;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.is;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={
    "app.email-outbox.enabled=false","app.google-sheets.enabled=false",
    "app.admin-email=","app.admin-password=",
    "app.shop-admin.access-password=IndependentShopPassword5678!",
    "app.toss-payments.secret-key=test_sk_shop_admin_server_key"
})
@AutoConfigureMockMvc
@EnabledIfSystemProperty(named="integration",matches="true")
@Transactional
class ShopAdminIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired ObjectMapper mapper;

    @Test
    void shopAdministratorHasIndependentLoginAndCannotOpenMainAdmin() throws Exception {
        String email="shop-admin-"+UUID.randomUUID()+"@example.com";
        jdbc.update("""
            insert into app_users(email,password_hash,name,company_name,role,admin_role,enabled,email_verified,verified_at)
            values (?,?,'SHOP 담당자','(주)금성이엔씨','ADMIN','SHOP_ADMIN',true,true,current_timestamp)
            """,email,passwordEncoder.encode("StrongShopAdmin123!"));

        mvc.perform(formLogin().user(email).password("StrongShopAdmin123!"))
            .andExpect(redirectedUrl("/shop-admin-entry.html"));

        MockHttpSession session=new MockHttpSession();
        mvc.perform(get("/shop-admin.html").session(session).with(shopAdmin(email)))
            .andExpect(status().isFound()).andExpect(redirectedUrl("/shop-admin-entry.html"));
        mvc.perform(get("/api/shop-admin/summary").session(session).with(shopAdmin(email)))
            .andExpect(status().isForbidden());
        mvc.perform(post("/api/shop-admin/access").session(session).with(shopAdmin(email)).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsBytes(Map.of("password","StrongShopAdmin123!"))))
            .andExpect(status().isForbidden());
        mvc.perform(post("/api/shop-admin/access").session(session).with(shopAdmin(email)).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsBytes(Map.of("password","IndependentShopPassword5678!"))))
            .andExpect(status().isOk());
        mvc.perform(get("/shop-admin.html").session(session).with(shopAdmin(email)))
            .andExpect(status().isOk());
        mvc.perform(get("/shop-admin.html").session(session).with(shopAdmin(email)))
            .andExpect(status().isFound()).andExpect(redirectedUrl("/shop-admin-entry.html"));
        mvc.perform(get("/api/shop-admin/summary").session(session).with(shopAdmin(email)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.products").isNumber());
        mvc.perform(get("/admin.html").with(shopAdmin(email)))
            .andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/dashboard").with(shopAdmin(email)))
            .andExpect(status().isForbidden());
        mvc.perform(get("/api/shop-admin/summary").with(user("operator@example.com").roles("ADMIN")))
            .andExpect(status().isForbidden());
        mvc.perform(get("/api/shop-admin/summary").session(verifiedSession()).with(superAdmin("super@example.com")))
            .andExpect(status().isOk());
    }

    @Test
    void shopAdministratorCreatesPricedProductAndPublicShopReceivesPrice() throws Exception {
        String code="PRICE_"+UUID.randomUUID().toString().replace("-","").substring(0,10).toUpperCase();
        MockHttpSession session=verifiedSession();
        String response=mvc.perform(multipart("/api/shop-admin/products")
                .param("code",code).param("name","가격 통합 테스트 제품")
                .param("category","테스트").param("description","가격 공개 검증")
                .param("price","275000").param("displayOrder","999").param("active","true")
                .session(session).with(shopAdmin("shop@example.com")).with(csrf()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.price",is(275000)))
            .andReturn().getResponse().getContentAsString();
        long id=mapper.readTree(response).get("id").asLong();

        mvc.perform(get("/api/public/shop/products"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[?(@.id == "+id+")].price").value(275000));
    }

    @Test
    void tossPaymentsSettingsKeepSecretOnServerAndRequireMatchingMode() throws Exception {
        MockHttpSession session=verifiedSession();
        mvc.perform(put("/api/shop-admin/toss-payments")
                .session(session).with(shopAdmin("shop@example.com")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsBytes(Map.of(
                    "enabled",true,"mode","TEST","clientKey","test_ck_shop_admin_client_key"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.ready",is(true)))
            .andExpect(jsonPath("$.secretKeyConfigured",is(true)))
            .andExpect(jsonPath("$.secretKey").doesNotExist());

        mvc.perform(put("/api/shop-admin/toss-payments")
                .session(session).with(shopAdmin("shop@example.com")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsBytes(Map.of(
                    "enabled",true,"mode","LIVE","clientKey","test_ck_wrong_mode"))))
            .andExpect(status().isBadRequest());

        mvc.perform(put("/api/shop-admin/toss-payments")
                .session(session).with(shopAdmin("shop@example.com")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsBytes(Map.of(
                    "enabled",true,"mode","TEST","clientKey","test_gck_widget_key"))))
            .andExpect(status().isBadRequest());
    }

    private RequestPostProcessor shopAdmin(String email){
        return user(email).authorities(
            new SimpleGrantedAuthority("ROLE_ADMIN"),
            new SimpleGrantedAuthority("ADMIN_SCOPE_SHOP_ADMIN"));
    }

    private RequestPostProcessor superAdmin(String email){
        return user(email).authorities(
            new SimpleGrantedAuthority("ROLE_ADMIN"),
            new SimpleGrantedAuthority("ADMIN_SCOPE_SUPER_ADMIN"));
    }

    private MockHttpSession verifiedSession(){
        MockHttpSession session=new MockHttpSession();
        session.setAttribute(ShopAdminAccessService.VERIFIED_AT,System.currentTimeMillis());
        return session;
    }
}
