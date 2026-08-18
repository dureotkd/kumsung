package kr.co.kumsungenc.platform.content;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/content")
public class AdminContentController {
    private final ManagedContentService service;
    public AdminContentController(ManagedContentService service){this.service=service;}

    @GetMapping("/innovation")
    public List<Map<String,Object>> innovation(@RequestParam(defaultValue="25") int limit,@RequestParam(defaultValue="0") int offset){
        return service.adminInnovation(limit,offset);
    }

    @PostMapping(value="/innovation",consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String,Object> createInnovation(@RequestParam(defaultValue="TECHNICAL") String category,@RequestParam String title,@RequestParam(required=false) String description,
        @RequestParam(defaultValue="0") int displayOrder,@RequestParam(defaultValue="true") boolean published,
        @RequestParam String password,@RequestPart MultipartFile image,@RequestPart MultipartFile file) throws IOException{
        return service.createInnovation(category,title,description,displayOrder,published,password,image,file);
    }

    @PutMapping("/innovation/{id}/status") public Map<String,String> innovationStatus(@PathVariable long id,@RequestBody Published body){
        service.innovationStatus(id,body.published());return Map.of("message","기술자료 공개 상태를 변경했습니다.");
    }

    @DeleteMapping("/innovation/{id}") public Map<String,String> deleteInnovation(@PathVariable long id){
        service.deleteInnovation(id);return Map.of("message","기술자료를 삭제했습니다.");
    }

    @GetMapping("/posts")
    public List<Map<String,Object>> posts(@RequestParam(defaultValue="25") int limit,@RequestParam(defaultValue="0") int offset){
        return service.adminPosts(limit,offset);
    }

    @PostMapping(value="/posts",consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String,Object> createPost(@RequestParam String type,@RequestParam String title,@RequestParam(required=false) String content,
        @RequestParam(defaultValue="true") boolean published,@RequestParam(defaultValue="false") boolean pinned,
        @RequestPart MultipartFile image) throws IOException{
        return service.createPost(type,title,content,published,pinned,image);
    }

    @PutMapping("/posts/{id}/status") public Map<String,String> postStatus(@PathVariable long id,@RequestBody PostStatus body){
        service.postStatus(id,body.published(),body.pinned());return Map.of("message","게시물 상태를 변경했습니다.");
    }

    @DeleteMapping("/posts/{id}") public Map<String,String> deletePost(@PathVariable long id){
        service.deletePost(id);return Map.of("message","게시물을 삭제했습니다.");
    }

    public record Published(boolean published){}
    public record PostStatus(boolean published,boolean pinned){}
}
