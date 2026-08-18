package kr.co.kumsungenc.platform.shop;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

import java.net.URI;

@Controller
public class ShopAdminPageController {
    @GetMapping("/shop-admin.html")
    public ResponseEntity<?> page(HttpServletRequest request){
        HttpSession session=request.getSession(false);
        if(session==null||!Boolean.TRUE.equals(session.getAttribute(ShopAdminAccessService.ENTRY_GRANTED))){
            return ResponseEntity.status(302).location(URI.create("/shop-admin-entry.html")).build();
        }
        session.removeAttribute(ShopAdminAccessService.ENTRY_GRANTED);
        session.setAttribute(ShopAdminAccessService.VERIFIED_AT,System.currentTimeMillis());
        return ResponseEntity.ok()
            .cacheControl(CacheControl.noStore())
            .contentType(MediaType.TEXT_HTML)
            .body(new ClassPathResource("static/shop-admin.html"));
    }
}
