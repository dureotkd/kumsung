package kr.co.kumsungenc.platform.shop;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;

@Component
public class TossPaymentsClient {
    private final ObjectMapper mapper;
    private final HttpClient http;
    private final String secretKey;
    private final String baseUrl;

    public TossPaymentsClient(ObjectMapper mapper,
            @Value("${app.toss-payments.secret-key:}") String secretKey,
            @Value("${app.toss-payments.api-base-url:https://api.tosspayments.com}") String baseUrl) {
        this.mapper=mapper;
        this.secretKey=secretKey==null?"":secretKey.trim();
        this.baseUrl=baseUrl.replaceAll("/+$","");
        this.http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    }

    public JsonNode confirm(String paymentKey,String orderId,long amount){
        return post("/v1/payments/confirm",
            Map.of("paymentKey",paymentKey,"orderId",orderId,"amount",amount),orderId+"-confirm");
    }

    public JsonNode cancel(String paymentKey,String orderId,String reason){
        return post("/v1/payments/"+encodePath(paymentKey)+"/cancel",
            Map.of("cancelReason",reason),orderId+"-cancel");
    }

    private JsonNode post(String path,Map<String,?> payload,String idempotencyKey){
        if(secretKey.isBlank())throw new IllegalStateException("토스페이먼츠 서버 시크릿 키가 설정되지 않았습니다.");
        try{
            String authorization=Base64.getEncoder().encodeToString((secretKey+":").getBytes(StandardCharsets.UTF_8));
            String body=mapper.writeValueAsString(payload);
            HttpRequest request=HttpRequest.newBuilder(URI.create(baseUrl+path))
                .timeout(Duration.ofSeconds(15))
                .header("Authorization","Basic "+authorization)
                .header("Content-Type","application/json")
                .header("Idempotency-Key",idempotencyKey)
                .POST(HttpRequest.BodyPublishers.ofString(body,StandardCharsets.UTF_8)).build();
            HttpResponse<String> response=http.send(request,HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            JsonNode result=mapper.readTree(response.body());
            if(response.statusCode()<200||response.statusCode()>=300){
                String message=result.path("message").asText("결제 승인에 실패했습니다.");
                throw new IllegalArgumentException(message);
            }
            return result;
        }catch(IllegalArgumentException|IllegalStateException exception){
            throw exception;
        }catch(InterruptedException exception){
            Thread.currentThread().interrupt();
            throw new IllegalStateException("토스페이먼츠 결제 승인 요청이 중단되었습니다.",exception);
        }catch(Exception exception){
            throw new IllegalStateException("토스페이먼츠 결제 승인 서버에 연결하지 못했습니다.",exception);
        }
    }

    private String encodePath(String value){
        return java.net.URLEncoder.encode(value,StandardCharsets.UTF_8).replace("+","%20");
    }
}
