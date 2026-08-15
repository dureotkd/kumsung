package kr.co.kumsungenc.platform.quote;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.util.*;
import kr.co.kumsungenc.platform.security.ClientIpResolver;

@RestController
@RequestMapping("/api/quotes")
public class QuoteController {
    private final QuoteService service;
    private final ClientIpResolver clientIpResolver;
    public QuoteController(QuoteService service,ClientIpResolver clientIpResolver) { this.service = service;this.clientIpResolver=clientIpResolver; }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String,String>> submit(
            @Valid @RequestPart("request") QuoteForm form,
            @RequestPart(value="files", required=false) List<MultipartFile> files,
            HttpServletRequest request) throws IOException {
        QuoteRequest saved = service.submit(form, files == null ? List.of() : files,clientIpResolver.resolve(request),request.getHeader("User-Agent"));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Map.of("receiptNumber", saved.getReceiptNumber(), "message", "견적 요청이 접수되었습니다."));
    }

    @ExceptionHandler({IllegalArgumentException.class, IOException.class})
    ResponseEntity<Map<String,String>> badRequest(Exception e) {
        return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
    }
    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<Map<String,String>> validation(MethodArgumentNotValidException e) {
        String message = Optional.ofNullable(e.getBindingResult().getFieldError())
                .map(x -> x.getDefaultMessage()).orElse("입력 내용을 확인해 주세요.");
        return ResponseEntity.badRequest().body(Map.of("message", message));
    }
}
