package kr.co.kumsungenc.platform.security;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.time.Instant;
import java.util.concurrent.*;

@Component
public class RequestRateLimitFilter extends OncePerRequestFilter {
    private record Key(String ip,String path){}
    private static final class Window {long started;int count;Window(long started){this.started=started;}}
    private final ConcurrentHashMap<Key,Window> windows=new ConcurrentHashMap<>();
    private final ClientIpResolver clientIpResolver;
    public RequestRateLimitFilter(ClientIpResolver clientIpResolver){this.clientIpResolver=clientIpResolver;}
    @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain)
        throws ServletException,IOException {
        if(!"POST".equals(request.getMethod())){chain.doFilter(request,response);return;}
        String path=request.getRequestURI();int limit;long seconds;
        if("/login".equals(path)){limit=10;seconds=900;}
        else if("/api/auth/register".equals(path)||"/api/auth/resend".equals(path)||"/api/auth/password/forgot".equals(path)){limit=5;seconds=3600;}
        else if("/api/auth/password/reset".equals(path)){limit=10;seconds=3600;}
        else if("/api/auth/admin-account".equals(path)){limit=10;seconds=900;}
        else if("/api/quotes".equals(path)){limit=20;seconds=3600;}
        else if("/api/public/shop/inquiries".equals(path)||"/api/public/support".equals(path)){limit=10;seconds=3600;}
        else if(path.matches("/api/public/content/innovation/\\d+/download")){limit=20;seconds=900;}
        else{chain.doFilter(request,response);return;}
        String ip=clientIpResolver.resolve(request);
        long now=Instant.now().getEpochSecond();Key key=new Key(ip,path);
        Window window=windows.compute(key,(k,w)->{
            if(w==null||now-w.started>=seconds)w=new Window(now);
            w.count++;return w;
        });
        if(window.count>limit){
            response.setStatus(429);response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"message\":\"요청이 너무 많습니다. 잠시 후 다시 시도해 주세요.\"}");
            return;
        }
        if(windows.size()>10000)windows.entrySet().removeIf(e->now-e.getValue().started>3600);
        chain.doFilter(request,response);
    }
}
