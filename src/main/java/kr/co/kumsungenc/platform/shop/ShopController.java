package kr.co.kumsungenc.platform.shop;

import jakarta.servlet.http.HttpServletRequest;
import kr.co.kumsungenc.platform.security.ClientIpResolver;
import kr.co.kumsungenc.platform.content.ManagedContentService;
import kr.co.kumsungenc.platform.file.ObjectStorage;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.*;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.util.*;

@RestController
@RequestMapping("/api/public/shop")
public class ShopController {
    private final JdbcTemplate jdbc;private final ShopInquiryService service;private final ClientIpResolver clientIpResolver;private final ManagedContentService content;private final ShopPaymentService payments;
    public ShopController(JdbcTemplate jdbc,ShopInquiryService service,ClientIpResolver clientIpResolver,ManagedContentService content,ShopPaymentService payments){this.jdbc=jdbc;this.service=service;this.clientIpResolver=clientIpResolver;this.content=content;this.payments=payments;}
    @GetMapping("/products") public List<Map<String,Object>> products(){
        List<Map<String,Object>> rows=jdbc.queryForList("select id,code,name,category,description,price,image_url,image_key from shop_products where active=true order by display_order,id");
        rows.forEach(row->row.put("imageUrl",row.get("image_key")!=null?"/api/public/shop/products/"+row.get("id")+"/image":row.get("image_url")));
        return rows;
    }
    @GetMapping("/products/{id}/image") public ResponseEntity<?> productImage(@PathVariable long id) throws IOException{
        Map<String,Object> row=content.product(id);if(!Boolean.TRUE.equals(row.get("active")))throw new NoSuchElementException();
        ObjectStorage.StoredObject stored=content.productImage(id);
        MediaType type;try{type=MediaType.parseMediaType((String)row.get("image_content_type"));}catch(Exception ignored){type=MediaType.APPLICATION_OCTET_STREAM;}
        return ResponseEntity.ok().contentType(type).contentLength(stored.contentLength()).cacheControl(CacheControl.maxAge(java.time.Duration.ofHours(1)).cachePublic()).body(stored.resource());
    }
    @PostMapping(value="/inquiries",consumes=MediaType.MULTIPART_FORM_DATA_VALUE) public Map<String,String> submit(
        @RequestPart("request") ShopInquiryService.Request body,
        @RequestPart(value="files",required=false) List<MultipartFile> files,HttpServletRequest request) throws IOException{
        return service.submit(body,files==null?List.of():files,clientIpResolver.resolve(request),request.getHeader("User-Agent"));
    }
    @GetMapping("/payment/config") public Map<String,Object> paymentConfig(){return payments.publicConfig();}
    @PostMapping("/orders") public Map<String,Object> createOrder(@RequestBody ShopPaymentService.CreateOrder body,
        java.security.Principal principal,HttpServletRequest request){return payments.create(body,principal,
            clientIpResolver.resolve(request),request.getHeader("User-Agent"));}
    @PostMapping("/payments/confirm") public Map<String,Object> confirmPayment(
        @RequestBody ShopPaymentService.ConfirmOrder body){return payments.confirm(body);}
}
