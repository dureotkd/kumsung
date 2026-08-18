package kr.co.kumsungenc.platform.shop;

import jakarta.servlet.http.HttpServletRequest;
import kr.co.kumsungenc.platform.content.ManagedContentService;
import kr.co.kumsungenc.platform.security.ClientIpResolver;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.security.Principal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/shop-admin")
public class ShopAdminController {
    private final ManagedContentService content;
    private final TossPaymentsSettingsService payments;
    private final ShopPaymentService orderPayments;
    private final ClientIpResolver clientIpResolver;
    private final ShopAdminAccessService access;

    public ShopAdminController(ManagedContentService content,TossPaymentsSettingsService payments,
            ShopPaymentService orderPayments,ClientIpResolver clientIpResolver,ShopAdminAccessService access){
        this.content=content;this.payments=payments;this.orderPayments=orderPayments;this.clientIpResolver=clientIpResolver;this.access=access;
    }

    @PostMapping("/access")
    public Map<String,String> access(@RequestBody Password body,HttpServletRequest request){
        access.verify(body.password(),request);
        return Map.of("next","/shop-admin.html");
    }

    @GetMapping("/summary") public Map<String,Object> summary(){return payments.summary();}

    @GetMapping("/products")
    public List<Map<String,Object>> products(@RequestParam(defaultValue="25") int limit,
            @RequestParam(defaultValue="0") int offset){
        return content.adminProducts(limit,offset);
    }

    @PostMapping(value="/products",consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String,Object> createProduct(@RequestParam String code,@RequestParam String name,
        @RequestParam(required=false) String category,@RequestParam(required=false) String description,
        @RequestParam(required=false) Long price,@RequestParam(defaultValue="0") int displayOrder,
        @RequestParam(defaultValue="true") boolean active,
        @RequestPart(required=false) MultipartFile image) throws IOException{
        return content.createProduct(code,name,category,description,price,displayOrder,active,image);
    }

    @PutMapping(value="/products/{id}",consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String,Object> updateProduct(@PathVariable long id,@RequestParam String code,@RequestParam String name,
        @RequestParam(required=false) String category,@RequestParam(required=false) String description,
        @RequestParam(required=false) Long price,@RequestParam(defaultValue="0") int displayOrder,
        @RequestParam(defaultValue="true") boolean active,
        @RequestPart(required=false) MultipartFile image) throws IOException{
        return content.updateProduct(id,code,name,category,description,price,displayOrder,active,image);
    }

    @PutMapping("/products/{id}/status")
    public Map<String,String> productStatus(@PathVariable long id,@RequestBody Status body){
        content.productStatus(id,body.active());
        return Map.of("message","제품 공개 상태를 변경했습니다.");
    }

    @GetMapping("/toss-payments") public Map<String,Object> tossPayments(){return payments.settings();}

    @GetMapping("/orders") public List<Map<String,Object>> orders(@RequestParam(defaultValue="25") int limit,
            @RequestParam(defaultValue="0") int offset){return orderPayments.adminOrders(limit,offset);}

    @PutMapping("/toss-payments")
    public Map<String,Object> updateTossPayments(@RequestBody TossSettings body,Principal principal,
            HttpServletRequest request){
        return payments.update(body.enabled(),body.mode(),body.clientKey(),principal.getName(),
            clientIpResolver.resolve(request));
    }

    public record Status(boolean active){}
    public record TossSettings(boolean enabled,String mode,String clientKey){}
    public record Password(String password){}
}
