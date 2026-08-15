package kr.co.kumsungenc.platform.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={
    "app.email-outbox.enabled=false",
    "app.admin-email=",
    "app.admin-password="
})
@AutoConfigureMockMvc
@EnabledIfSystemProperty(named="integration",matches="true")
@Transactional
class AdminManagementIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper mapper;
    @Autowired PasswordEncoder passwordEncoder;

    @Test
    void administratorLoginRedirectsDirectlyToAdminPage() throws Exception {
        String email="login-admin-"+UUID.randomUUID()+"@example.com";
        jdbc.update("""
            insert into app_users(email,password_hash,name,company_name,role,admin_role,enabled,email_verified,verified_at)
            values (?,?,'로그인 관리자','(주)금성이엔씨','ADMIN','ADMIN',true,true,current_timestamp)
            """,email,passwordEncoder.encode("StrongAdmin123!"));

        mvc.perform(formLogin().user(email).password("StrongAdmin123!"))
            .andExpect(redirectedUrl("/admin.html"));
    }

    @Test
    void superAdminInvitesManagesAndResetsAdminWhileNormalAdminIsForbidden() throws Exception {
        String suffix=UUID.randomUUID().toString();
        String superEmail="super-"+suffix+"@example.com";
        String normalEmail="operator-"+suffix+"@example.com";
        long superId=createAdmin(superEmail,"SUPER_ADMIN");

        mvc.perform(post("/api/admin/accounts/invitations")
                .with(user(superEmail).roles("ADMIN")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsBytes(Map.of(
                    "email",normalEmail,"name","견적 담당자","adminRole","ADMIN"))))
            .andExpect(status().isCreated());

        String inviteBody=jdbc.queryForObject("""
            select body from email_outbox where recipient=? and subject like '%관리자 계정 초대%'
            order by id desc limit 1
            """,String.class,normalEmail);
        String inviteToken=token(inviteBody);

        mvc.perform(get("/api/auth/admin-account").param("token",inviteToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.email",is(normalEmail)))
            .andExpect(jsonPath("$.admin_role",is("ADMIN")));
        mvc.perform(post("/api/auth/admin-account").with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsBytes(Map.of(
                    "token",inviteToken,"password","StrongAdmin123!"))))
            .andExpect(status().isOk());

        long normalId=Objects.requireNonNull(jdbc.queryForObject(
            "select id from app_users where email=?",Long.class,normalEmail));
        mvc.perform(get("/api/admin/accounts").with(user(normalEmail).roles("ADMIN")))
            .andExpect(status().isForbidden());
        mvc.perform(put("/api/admin/accounts/{id}/enabled",superId)
                .with(user(superEmail).roles("ADMIN")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsBytes(Map.of("enabled",false))))
            .andExpect(status().isBadRequest());

        String before=jdbc.queryForObject("select password_hash from app_users where id=?",String.class,normalId);
        mvc.perform(post("/api/admin/accounts/{id}/password-reset",normalId)
                .with(user(superEmail).roles("ADMIN")).with(csrf()))
            .andExpect(status().isOk());
        String resetBody=jdbc.queryForObject("""
            select body from email_outbox where recipient=? and subject like '%비밀번호 재설정%'
            order by id desc limit 1
            """,String.class,normalEmail);
        mvc.perform(post("/api/auth/admin-account").with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsBytes(Map.of(
                    "token",token(resetBody),"password","ChangedAdmin123!"))))
            .andExpect(status().isOk());
        String after=jdbc.queryForObject("select password_hash from app_users where id=?",String.class,normalId);
        assertNotEquals(before,after);

        Integer auditCount=jdbc.queryForObject("""
            select count(*) from audit_logs
            where actor_email in (?,?) and action in
              ('ADMIN_INVITE','ADMIN_INVITE_ACCEPT','ADMIN_PASSWORD_RESET_REQUEST','ADMIN_PASSWORD_RESET')
            """,Integer.class,superEmail,normalEmail);
        assertTrue(Objects.requireNonNull(auditCount)>=4);
    }

    private long createAdmin(String email,String adminRole){
        return Objects.requireNonNull(jdbc.queryForObject("""
            insert into app_users(email,password_hash,name,company_name,role,admin_role,enabled,email_verified,verified_at)
            values (?,'test-hash','테스트 관리자','(주)금성이엔씨','ADMIN',?,true,true,current_timestamp)
            returning id
            """,Long.class,email,adminRole));
    }
    private String token(String body){
        return body.substring(body.indexOf("token=")+6).split("\\s")[0];
    }
}
