package kr.co.kumsungenc.platform.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import kr.co.kumsungenc.platform.shop.TossPaymentsClient;
import kr.co.kumsungenc.platform.shop.ShopPaymentService;
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
    @Autowired ShopPaymentService payments;
    @MockitoBean TossPaymentsClient toss;

    @Test
    void exposesApprovedFixedCatalogWithUnitPrices() throws Exception {
        mvc.perform(get("/api/public/shop/products"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()",is(12)))
            .andExpect(jsonPath("$[0].code",is("DRY_PD")))
            .andExpect(jsonPath("$[0].name",is("건식PD 표준형")))
            .andExpect(jsonPath("$[0].price",is(128000)))
            .andExpect(jsonPath("$[3].code",is("SEISMIC_FRAME")))
            .andExpect(jsonPath("$[3].price",is(145000)))
            .andExpect(jsonPath("$[4].code",is("SITE_GANGNAM")))
            .andExpect(jsonPath("$[4].price",is(1000000)))
            .andExpect(jsonPath("$[7].code",is("ACCESSORY_HANDLE")))
            .andExpect(jsonPath("$[7].price",is(1000)))
            .andExpect(jsonPath("$[8].price",is(500)))
            .andExpect(jsonPath("$[9].price",is(2500)))
            .andExpect(jsonPath("$[10].price",is(10000)))
            .andExpect(jsonPath("$[11].code",is("OTHER_2")))
            .andExpect(jsonPath("$[11].price",is(10000)));
    }

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

        ObjectNode approved=mapper.createObjectNode();approved.put("paymentKey","test_payment_key");approved.put("orderId",orderId);approved.put("totalAmount",250000);
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
        org.junit.jupiter.api.Assertions.assertEquals(1,jdbc.queryForObject("""
            select count(*) from email_outbox where reference_type='SHOP_ORDER'
              and reference_id=(select id from shop_orders where order_id=?)
            """,Integer.class,orderId));

        long databaseId=jdbc.queryForObject("select id from shop_orders where order_id=?",Long.class,orderId);
        ObjectNode canceled=mapper.createObjectNode();canceled.put("paymentKey","test_payment_key");canceled.put("orderId",orderId);canceled.put("status","CANCELED");
        canceled.putArray("cancels").addObject().put("canceledAt","2026-08-18T12:05:00+09:00");
        when(toss.cancel("test_payment_key",orderId,"구매자 요청")).thenReturn(canceled);
        org.junit.jupiter.api.Assertions.assertEquals("CANCELED",
            payments.cancel(databaseId,new ShopPaymentService.CancelOrder("구매자 요청"),"admin@example.com","127.0.0.1").get("status"));
        org.junit.jupiter.api.Assertions.assertEquals("CANCELED",
            payments.cancel(databaseId,new ShopPaymentService.CancelOrder("구매자 요청"),"admin@example.com","127.0.0.1").get("status"));
        verify(toss,times(1)).cancel("test_payment_key",orderId,"구매자 요청");
    }

    @Test
    void recordsFailedAndCustomerCanceledPaymentOutcomesWithoutDowngradingPaidOrders() throws Exception {
        jdbc.update("update shop_payment_settings set enabled=true,mode='TEST',client_key='test_ck_D5GePWvyJnrK0W0k6q8gLzN97Eoq' where id=1");
        long productId=createProduct(10000L);
        String orderId=createOrder(productId);

        mvc.perform(post("/api/public/shop/payments/fail").with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsBytes(Map.of("orderId",orderId,"code","PAY_PROCESS_ABORTED","message","카드 승인이 중단되었습니다."))))
            .andExpect(status().isOk()).andExpect(jsonPath("$.status",is("FAILED")));
        org.junit.jupiter.api.Assertions.assertEquals("FAILED",jdbc.queryForObject("select status from shop_orders where order_id=?",String.class,orderId));

        String canceledOrderId=createOrder(productId);
        mvc.perform(post("/api/public/shop/payments/fail").with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsBytes(Map.of("orderId",canceledOrderId,"code","PAY_PROCESS_CANCELED","message","구매자가 취소했습니다."))))
            .andExpect(status().isOk()).andExpect(jsonPath("$.status",is("CANCELED")));
        org.junit.jupiter.api.Assertions.assertEquals("CANCELED",jdbc.queryForObject("select status from shop_orders where order_id=?",String.class,canceledOrderId));
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

    @Test
    void sitePaymentUsesOneApprovedAmountOnly() throws Exception {
        jdbc.update("update shop_payment_settings set enabled=true,mode='TEST',client_key='test_ck_D5GePWvyJnrK0W0k6q8gLzN97Eoq' where id=1");
        long productId=jdbc.queryForObject("select id from shop_products where code='SITE_GANGNAM'",Long.class);
        mvc.perform(post("/api/public/shop/orders").with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsBytes(Map.of(
                    "productId",productId,"quantity",2,"buyerName","테스트 구매자",
                    "buyerEmail","buyer@example.com","buyerPhone","01012345678",
                    "deliveryAddress","서울시 테스트구 1","privacyAgreed",true))))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message",is("현장별 결제는 확정 금액 1건으로만 결제할 수 있습니다.")));
    }

    private long createProduct(Long price){
        return jdbc.queryForObject("""
            insert into shop_products(code,name,category,description,price,active,display_order)
            values (?,?,?,?,?,true,999) returning id
            """,Long.class,"PAY_"+UUID.randomUUID().toString().replace("-","").substring(0,12),
            "결제 테스트 제품","테스트","관리자 가격 연동",price);
    }

    private String createOrder(long productId) throws Exception{
        String response=mvc.perform(post("/api/public/shop/orders").with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsBytes(Map.of(
                    "productId",productId,"quantity",1,"buyerName","테스트 구매자",
                    "buyerEmail","buyer@example.com","buyerPhone","010-1234-5678",
                    "deliveryAddress","서울시 테스트구 1","privacyAgreed",true))))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return mapper.readTree(response).path("orderId").asText();
    }
}
