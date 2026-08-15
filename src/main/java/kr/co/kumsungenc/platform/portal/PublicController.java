package kr.co.kumsungenc.platform.portal;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/api/public")
public class PublicController {
    private final JdbcTemplate jdbc;
    public PublicController(JdbcTemplate jdbc){this.jdbc=jdbc;}
    @GetMapping("/notices") public List<Map<String,Object>> notices(@RequestParam(defaultValue="50") int limit,@RequestParam(defaultValue="0") int offset){
        int size=Math.max(1,Math.min(limit,100));
        return jdbc.queryForList("select id,title,content,pinned,created_at from notices where published=true order by pinned desc,created_at desc,id desc limit ? offset ?",
            size,Math.max(0,Math.min(offset,100000)));
    }
}
