package kr.co.kumsungenc.platform.shop;

import com.fasterxml.jackson.databind.JsonNode;
import kr.co.kumsungenc.platform.privacy.PrivacyConsentService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.Principal;
import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;

@Service
public class ShopPaymentService {
    private final JdbcTemplate jdbc;
    private final TossPaymentsSettingsService settings;
    private final TossPaymentsClient toss;
    private final PrivacyConsentService privacy;

    public ShopPaymentService(JdbcTemplate jdbc,TossPaymentsSettingsService settings,TossPaymentsClient toss,
            PrivacyConsentService privacy){
        this.jdbc=jdbc;this.settings=settings;this.toss=toss;this.privacy=privacy;
    }

    public Map<String,Object> publicConfig(){
        Map<String,Object> configured=settings.settings();
        boolean ready=Boolean.TRUE.equals(configured.get("ready"));
        Map<String,Object> result=new LinkedHashMap<>();
        result.put("ready",ready);
        result.put("mode",configured.get("mode"));
        result.put("testMode","TEST".equals(configured.get("mode")));
        if(ready)result.put("clientKey",configured.get("clientKey"));
        return result;
    }

    @Transactional
    public Map<String,Object> create(CreateOrder request,Principal principal,String ip,String userAgent){
        requirePaymentReady();
        if(request==null||request.productId()==null||request.quantity()==null
                ||request.quantity()<1||request.quantity()>99)
            throw new IllegalArgumentException("제품과 구매 수량을 확인해 주세요.");
        text(request.buyerName(),100,"구매자명");
        text(request.buyerEmail(),120,"이메일");
        text(request.buyerPhone(),30,"연락처");
        text(request.deliveryAddress(),500,"배송·현장 주소");
        if(!request.buyerEmail().trim().matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$"))
            throw new IllegalArgumentException("이메일 형식을 확인해 주세요.");
        String phone=request.buyerPhone().replaceAll("\\D","");
        if(phone.length()<8||phone.length()>15)throw new IllegalArgumentException("연락처 형식을 확인해 주세요.");
        if(!Boolean.TRUE.equals(request.privacyAgreed()))
            throw new IllegalArgumentException("개인정보 수집 및 이용에 동의해 주세요.");

        List<Map<String,Object>> products=jdbc.queryForList("""
            select id,code,name,price from shop_products
            where id=? and active=true and price is not null and price>0
            """,request.productId());
        if(products.isEmpty())throw new IllegalArgumentException("현재 바로 구매할 수 없는 제품입니다.");
        Map<String,Object> product=products.getFirst();
        long unitPrice=((Number)product.get("price")).longValue();
        long amount;
        try{amount=Math.multiplyExact(unitPrice,request.quantity().longValue());}
        catch(ArithmeticException exception){throw new IllegalArgumentException("결제 금액을 확인해 주세요.");}
        String orderId="KSE_"+UUID.randomUUID().toString().replace("-","");
        Long customerUserId=customerUserId(principal);
        Long databaseId=jdbc.queryForObject("""
            insert into shop_orders(order_id,customer_user_id,product_id,product_code,product_name,
                unit_price,quantity,amount,buyer_name,buyer_email,buyer_phone,delivery_address)
            values (?,?,?,?,?,?,?,?,?,?,?,?)
            returning id
            """,Long.class,orderId,customerUserId,product.get("id"),product.get("code"),product.get("name"),
            unitPrice,request.quantity(),amount,request.buyerName().trim(),request.buyerEmail().trim().toLowerCase(),phone,
            request.deliveryAddress().trim());
        if(databaseId==null)throw new IllegalStateException("주문 저장에 실패했습니다.");
        privacy.record("SHOP_ORDER",databaseId,request.buyerEmail().trim().toLowerCase(),ip,userAgent);
        Map<String,Object> result=new LinkedHashMap<>();
        result.put("orderId",orderId);result.put("orderName",product.get("name"));
        result.put("amount",amount);result.put("customerKey","CUSTOMER_"+UUID.randomUUID().toString().replace("-",""));
        result.put("customerName",request.buyerName().trim());result.put("customerEmail",request.buyerEmail().trim().toLowerCase());
        result.put("customerMobilePhone",phone);result.putAll(publicConfig());
        return result;
    }

    @Transactional
    public Map<String,Object> confirm(ConfirmOrder request){
        requirePaymentReady();
        if(request==null||request.orderId()==null||request.paymentKey()==null||request.amount()==null
                ||!request.orderId().matches("[A-Za-z0-9_-]{6,64}")||request.paymentKey().isBlank()
                ||request.paymentKey().length()>200)
            throw new IllegalArgumentException("결제 승인 정보를 확인해 주세요.");
        List<Map<String,Object>> rows=jdbc.queryForList("select * from shop_orders where order_id=? for update",request.orderId());
        if(rows.isEmpty())throw new NoSuchElementException("주문을 찾을 수 없습니다.");
        Map<String,Object> order=rows.getFirst();
        long expected=((Number)order.get("amount")).longValue();
        if(expected!=request.amount())throw new IllegalArgumentException("주문 금액과 결제 금액이 일치하지 않습니다.");
        if("PAID".equals(order.get("status"))){
            if(!request.paymentKey().equals(order.get("payment_key")))
                throw new IllegalArgumentException("이미 다른 결제 정보로 승인된 주문입니다.");
            return paidResult(order);
        }
        JsonNode payment=toss.confirm(request.paymentKey(),request.orderId(),expected);
        if(!request.orderId().equals(payment.path("orderId").asText())
                ||expected!=payment.path("totalAmount").asLong()
                ||!"DONE".equals(payment.path("status").asText()))
            throw new IllegalArgumentException("토스페이먼츠 승인 결과가 주문 정보와 일치하지 않습니다.");
        Timestamp approvedAt=null;
        String approved=payment.path("approvedAt").asText("");
        if(!approved.isBlank())approvedAt=Timestamp.from(OffsetDateTime.parse(approved).toInstant());
        String method=nullable(payment.path("method").asText(""));
        String receiptUrl=nullable(payment.path("receipt").path("url").asText(""));
        jdbc.update("""
            update shop_orders set status='PAID',payment_key=?,payment_method=?,receipt_url=?,
                paid_at=coalesce(?,current_timestamp),failure_code=null,failure_message=null,updated_at=current_timestamp
            where id=?
            """,request.paymentKey(),method,receiptUrl,approvedAt,order.get("id"));
        order.put("status","PAID");order.put("payment_key",request.paymentKey());order.put("payment_method",method);
        order.put("receipt_url",receiptUrl);order.put("paid_at",approvedAt);
        return paidResult(order);
    }

    public List<Map<String,Object>> adminOrders(int limit,int offset){
        int safeLimit=Math.min(Math.max(limit,1),101),safeOffset=Math.max(offset,0);
        return jdbc.queryForList("""
            select id,order_id,product_code,product_name,unit_price,quantity,amount,buyer_name,buyer_email,
                   buyer_phone,delivery_address,status,payment_method,receipt_url,paid_at,created_at
            from shop_orders order by created_at desc,id desc limit ? offset ?
            """,safeLimit,safeOffset);
    }

    private Map<String,Object> paidResult(Map<String,Object> order){
        Map<String,Object> result=new LinkedHashMap<>();
        result.put("orderId",order.get("order_id"));result.put("productName",order.get("product_name"));
        result.put("amount",order.get("amount"));result.put("status","PAID");
        result.put("method",order.get("payment_method"));result.put("receiptUrl",order.get("receipt_url"));
        result.put("paidAt",order.get("paid_at"));return result;
    }
    private void requirePaymentReady(){if(!Boolean.TRUE.equals(settings.settings().get("ready")))throw new IllegalStateException("결제 모듈이 준비되지 않았습니다.");}
    private Long customerUserId(Principal principal){
        if(principal==null)return null;
        List<Long> ids=jdbc.query("select id from app_users where lower(email)=lower(?)",(rs,n)->rs.getLong(1),principal.getName());
        return ids.isEmpty()?null:ids.getFirst();
    }
    private void text(String value,int max,String label){if(value==null||value.isBlank()||value.trim().length()>max)throw new IllegalArgumentException(label+"을(를) "+max+"자 이내로 입력해 주세요.");}
    private String nullable(String value){return value==null||value.isBlank()?null:value;}

    public record CreateOrder(Long productId,Integer quantity,String buyerName,String buyerEmail,String buyerPhone,String deliveryAddress,Boolean privacyAgreed){}
    public record ConfirmOrder(String paymentKey,String orderId,Long amount){}
}
