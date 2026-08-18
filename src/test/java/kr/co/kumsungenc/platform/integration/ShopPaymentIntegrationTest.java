package kr.co.kumsungenc.platform.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import kr.co.kumsungenc.platform.shop.TossPaymentsClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={
    "app.email-outbox.enabled=false","app.google-sheets.enabled=false",
    "app.admin-email=","app.admin-password=",
    "app.toss-payments.secret-key=test_sk_local_payment_test_key"
})
@AutoConfigureMockMvc
@EnabledIfSystemProperty(named="integration",matches="true")
@Transactional
class ShopPaymentIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper mapper;
    @MockitoBean TossPaymentsClient toss;

    @Test
    void createsOrderFromManagedProductPriceAndConfirmsMatchingPayment() throws Exception {
        jdbc.update("""
            update shop_payment_settings set enabled=true,mode='TEST',
                client_key='test_ck_D5GePWvyJnrK0W0k6q8gLzN97Eoq' where id=1
            """);
        long productId=createProduct(125000L);
        String createResponse=mvc.perform(post("/api/public/shop/orders").with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsBytes(Map.of(
                    "productId",productId,"quantity",2,"buyerName","테스트 구매자",
                    "buyerEmail","buyer@example.com","buyerPhone","010-1234-5678",
                    "deliveryAddress","서울시 테스트구 1","privacyAgreed",true))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.amount",is(250000)))
            .andExpect(jsonPath("$.clientKey",is("test_ck_D5GePWvyJnrK0W0k6q8gLzN97Eoq")))
            .andExpect(jsonPath("$.testMode",is(true)))
            .andReturn().getResponse().getContentAsString();
        String orderId=mapper.readTree(createResponse).path("orderId").asText();

        mvc.perform(post("/api/public/shop/payments/confirm").with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsBytes(Map.of("paymentKey","test_payment_key","orderId",orderId,"amount",1))))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message",is("주문 금액과 결제 금액이 일치하지 않습니다.")));
        verifyNoInteractions(toss);

        ObjectNode approved=mapper.createObjectNode();approved.put("orderId",orderId);approved.put("totalAmount",250000);
        approved.put("status","DONE");approved.put("method","카드");approved.put("approvedAt","2026-08-18T12:00:00+09:00");
        approved.putObject("receipt").put("url","https://dashboard.tosspayments.com/receipt/test");
        when(toss.confirm("test_payment_key",orderId,250000L)).thenReturn(approved);

        mvc.perform(post("/api/public/shop/payments/confirm").with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsBytes(Map.of("paymentKey","test_payment_key","orderId",orderId,"amount",250000))))
            .andExpect(status().isOk()).andExpect(jsonPath("$.status",is("PAID")))
            .andExpect(jsonPath("$.amount",is(250000))).andExpect(jsonPath("$.method",is("카드")));
        mvc.perform(post("/api/public/shop/payments/confirm").with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsBytes(Map.of("paymentKey","test_payment_key","orderId",orderId,"amount",250000))))
            .andExpect(status().isOk()).andExpect(jsonPath("$.status",is("PAID")));
        verify(toss,times(1)).confirm("test_payment_key",orderId,250000L);
    }

    @Test
    void refusesInactiveUnpricedOrClientSuppliedPrice() throws Exception {
        jdbc.update("update shop_payment_settings set enabled=true,mode='TEST',client_key='test_ck_D5GePWvyJnrK0W0k6q8gLzN97Eoq' where id=1");
        long productId=createProduct(null);
        mvc.perform(post("/api/public/shop/orders").with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsBytes(Map.of(
                    "productId",productId,"quantity",1,"price",100,"buyerName","테스트 구매자",
                    "buyerEmail","buyer@example.com","buyerPhone","01012345678",
                    "deliveryAddress","서울시 테스트구 1","privacyAgreed",true))))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message",is("현재 바로 구매할 수 없는 제품입니다.")));
    }

    private long createProduct(Long price){
        return jdbc.queryForObject("""
            insert into shop_products(code,name,category,description,price,active,display_order)
            values (?,?,?,?,?,true,999) returning id
            """,Long.class,"PAY_"+UUID.randomUUID().toString().replace("-","").substring(0,12),
            "결제 테스트 제품","테스트","관리자 가격 연동",price);
    }
}
