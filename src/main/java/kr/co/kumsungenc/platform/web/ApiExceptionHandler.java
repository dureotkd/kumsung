package kr.co.kumsungenc.platform.web;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.util.Map;
import java.util.NoSuchElementException;

@RestControllerAdvice
public class ApiExceptionHandler {
    private static final Logger log=LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler({NoSuchElementException.class,EmptyResultDataAccessException.class})
    ResponseEntity<Map<String,String>> notFound(){
        return response(HttpStatus.NOT_FOUND,"요청한 데이터를 찾을 수 없습니다.");
    }

    @ExceptionHandler({IllegalArgumentException.class,HttpMessageNotReadableException.class,MethodArgumentNotValidException.class})
    ResponseEntity<Map<String,String>> badRequest(Exception exception){
        String message=exception instanceof IllegalArgumentException&&exception.getMessage()!=null
            ?exception.getMessage():"입력값을 확인해 주세요.";
        return response(HttpStatus.BAD_REQUEST,message);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ResponseEntity<Map<String,String>> tooLarge(){
        return response(HttpStatus.PAYLOAD_TOO_LARGE,"업로드 용량 제한을 초과했습니다. 파일당 50MB, 요청당 200MB까지 가능합니다.");
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<Map<String,String>> conflict(){
        return response(HttpStatus.CONFLICT,"이미 등록된 값이거나 현재 데이터 관계와 충돌합니다.");
    }

    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<Map<String,String>> forbidden(AccessDeniedException exception){
        String message=exception.getMessage()==null||exception.getMessage().isBlank()
            ?"이 작업을 수행할 권한이 없습니다.":exception.getMessage();
        return response(HttpStatus.FORBIDDEN,message);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    ResponseEntity<Map<String,String>> methodNotAllowed(){
        return response(HttpStatus.METHOD_NOT_ALLOWED,"허용되지 않은 요청 방식입니다.");
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<Map<String,String>> unexpected(Exception exception){
        log.error("처리되지 않은 API 오류",exception);
        return response(HttpStatus.INTERNAL_SERVER_ERROR,"서버 처리 중 오류가 발생했습니다.");
    }

    private ResponseEntity<Map<String,String>> response(HttpStatus status,String message){
        return ResponseEntity.status(status).body(Map.of("message",message));
    }
}
