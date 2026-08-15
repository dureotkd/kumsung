package kr.co.kumsungenc.platform.portal;

import jakarta.servlet.http.HttpServletRequest;
import kr.co.kumsungenc.platform.security.ClientIpResolver;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api/public/support")
public class PublicInquiryController {
    private final PublicInquiryService service;private final ClientIpResolver clientIpResolver;
    public PublicInquiryController(PublicInquiryService service,ClientIpResolver clientIpResolver){this.service=service;this.clientIpResolver=clientIpResolver;}
    @PostMapping public Map<String,String> submit(@RequestBody PublicInquiryService.Request body,HttpServletRequest request){
        return service.submit(body,clientIpResolver.resolve(request),request.getHeader("User-Agent"));
    }
}
