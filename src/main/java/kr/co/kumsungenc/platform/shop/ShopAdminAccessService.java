package kr.co.kumsungenc.platform.shop;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@Service
public class ShopAdminAccessService {
    public static final String ENTRY_GRANTED="SHOP_ADMIN_ENTRY_GRANTED";
    public static final String VERIFIED_AT="SHOP_ADMIN_VERIFIED_AT";
    public static final long VERIFICATION_TTL_MS=30*60*1000L;
    private static final String ACCESS_PASSWORD="local-shop-admin-5678";

    public void verify(String password,HttpServletRequest request){
        HttpSession session=request.getSession(true);
        session.removeAttribute(ENTRY_GRANTED);session.removeAttribute(VERIFIED_AT);
        byte[] supplied=(password==null?"":password).getBytes(StandardCharsets.UTF_8);
        byte[] expected=ACCESS_PASSWORD.getBytes(StandardCharsets.UTF_8);
        if(!MessageDigest.isEqual(supplied,expected))
            throw new AccessDeniedException("SHOP 관리자 2차 비밀번호가 올바르지 않습니다.");
        session.setAttribute(ENTRY_GRANTED,Boolean.TRUE);
    }

    public static boolean isRecentlyVerified(HttpServletRequest request){
        HttpSession session=request.getSession(false);if(session==null)return false;
        Object value=session.getAttribute(VERIFIED_AT);
        return value instanceof Long verifiedAt
            &&System.currentTimeMillis()-verifiedAt>=0
            &&System.currentTimeMillis()-verifiedAt<=VERIFICATION_TTL_MS;
    }
}
