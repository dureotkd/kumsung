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

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"app.email-outbox.enabled=false","app.admin-email=","app.admin-password="})
@AutoConfigureMockMvc
@EnabledIfSystemProperty(named="integration",matches="true")
@Transactional
class PasswordResetIntegrationTest {
    @Autowired MockMvc mvc;@Autowired JdbcTemplate jdbc;@Autowired ObjectMapper mapper;@Autowired PasswordEncoder encoder;

    @Test
    void customerCanRequestSingleUseResetWithoutAccountEnumeration() throws Exception {
        String email="reset-"+UUID.randomUUID()+"@example.com";String oldHash=encoder.encode("OldPassword123!");
        Long userId=jdbc.queryForObject("""
            insert into app_users(email,password_hash,name,company_name,role,enabled,email_verified,verified_at)
            values (?,?, '비밀번호 테스트','테스트 회사','CUSTOMER',true,true,current_timestamp) returning id
            """,Long.class,email,oldHash);

        mvc.perform(post("/api/auth/password/forgot").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsBytes(Map.of("email","missing-"+UUID.randomUUID()+"@example.com"))))
            .andExpect(status().isOk()).andExpect(jsonPath("$.message").value("가입된 계정이면 비밀번호 재설정 이메일을 발송합니다."));
        mvc.perform(post("/api/auth/password/forgot").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsBytes(Map.of("email",email))))
            .andExpect(status().isOk()).andExpect(jsonPath("$.message").value("가입된 계정이면 비밀번호 재설정 이메일을 발송합니다."));

        String body=jdbc.queryForObject("select body from email_outbox where recipient=? and subject like '%비밀번호 재설정%' order by id desc limit 1",String.class,email);
        assertNotNull(body);String token=body.substring(body.indexOf("#token=")+7).split("\\s")[0];
        mvc.perform(post("/api/auth/password/reset").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsBytes(Map.of("token",token,"password","NewPassword123!"))))
            .andExpect(status().isOk());
        String changed=jdbc.queryForObject("select password_hash from app_users where id=?",String.class,userId);
        assertTrue(encoder.matches("NewPassword123!",changed));assertFalse(encoder.matches("OldPassword123!",changed));
        mvc.perform(post("/api/auth/password/reset").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsBytes(Map.of("token",token,"password","AnotherPassword123!"))))
            .andExpect(status().isBadRequest());
        assertEquals(1,jdbc.queryForObject("select count(*) from audit_logs where action='PASSWORD_RESET' and target_id=?",Integer.class,Long.toString(userId)));
    }
}
