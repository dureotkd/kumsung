package kr.co.kumsungenc.platform.content;

import kr.co.kumsungenc.platform.file.ObjectStorage;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/public/content")
public class PublicContentController {
    private final ManagedContentService service;
    public PublicContentController(ManagedContentService service){this.service=service;}

    @GetMapping("/innovation") public List<Map<String,Object>> innovation(){return service.publicInnovation();}

    @GetMapping("/innovation/{id}/image") public ResponseEntity<?> innovationImage(@PathVariable long id) throws IOException{
        Map<String,Object> row=service.innovation(id);
        if(!Boolean.TRUE.equals(row.get("published")))throw new java.util.NoSuchElementException();
        return image(service.innovationImage(id,true),(String)row.get("image_content_type"),(String)row.get("image_original_name"));
    }

    @PostMapping("/innovation/{id}/download") public ResponseEntity<?> download(@PathVariable long id,@RequestBody Password body) throws IOException{
        ManagedContentService.Download download=service.innovationDownload(id,body.password());
        return ResponseEntity.ok()
            .contentType(mediaType(download.contentType()))
            .contentLength(download.object().contentLength())
            .header(HttpHeaders.CONTENT_DISPOSITION,ContentDisposition.attachment().filename(download.filename(),StandardCharsets.UTF_8).build().toString())
            .header(HttpHeaders.CACHE_CONTROL,"no-store")
            .body(download.object().resource());
    }

    @GetMapping("/posts") public List<Map<String,Object>> posts(@RequestParam(defaultValue="COMPANY_NEWS") String type){return service.publicPosts(type);}

    @GetMapping("/posts/{id}/image") public ResponseEntity<?> postImage(@PathVariable long id) throws IOException{
        Map<String,Object> row=service.post(id);
        if(!Boolean.TRUE.equals(row.get("published")))throw new java.util.NoSuchElementException();
        return image(service.postImage(id,true),(String)row.get("image_content_type"),(String)row.get("image_original_name"));
    }

    private ResponseEntity<?> image(ObjectStorage.StoredObject stored,String contentType,String filename){
        return ResponseEntity.ok().contentType(mediaType(contentType)).contentLength(stored.contentLength())
            .header(HttpHeaders.CONTENT_DISPOSITION,ContentDisposition.inline().filename(filename,StandardCharsets.UTF_8).build().toString())
            .cacheControl(CacheControl.maxAge(java.time.Duration.ofHours(1)).cachePublic()).body(stored.resource());
    }
    private MediaType mediaType(String value){try{return MediaType.parseMediaType(value);}catch(Exception ignored){return MediaType.APPLICATION_OCTET_STREAM;}}
    public record Password(String password){}
}
