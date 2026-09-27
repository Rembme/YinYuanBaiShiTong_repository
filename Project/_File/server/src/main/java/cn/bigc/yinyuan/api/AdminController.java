package cn.bigc.yinyuan.api;

import cn.bigc.yinyuan.service.CrawlerService;
import cn.bigc.yinyuan.service.KnowledgeService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/admin")
public class AdminController {
    private final JdbcTemplate jdbc;
    private final CrawlerService crawler;
    private final KnowledgeService knowledge;

    public AdminController(JdbcTemplate jdbc,CrawlerService crawler,KnowledgeService knowledge) {
        this.jdbc=jdbc;
        this.crawler=crawler;
        this.knowledge=knowledge;
    }

    public record SourceRequest(@NotBlank String name,@NotBlank String zoneId,@NotBlank String baseUrl) {}
    public record UserUpdateRequest(@NotBlank String role,boolean active) {}

    @GetMapping("/status")
    public Map<String,Object> status() {
        return Map.of("notices",jdbc.queryForObject("select count(*) from notices where status='ACTIVE'",Long.class),
                "documents",jdbc.queryForObject("select count(*) from knowledge_documents",Long.class),
                "sources",jdbc.queryForObject("select count(*) from data_sources where active=true",Long.class),
                "crawlRunning",crawler.isRunning());
    }

    @GetMapping("/sources")
    public List<Map<String,Object>> sources() {
        return jdbc.queryForList("select s.id::text as id,s.zone_id,z.name as zone_name,s.name,s.base_url,s.active,s.last_crawled_at,s.last_status,s.last_error from data_sources s join zones z on z.id=s.zone_id order by z.sort_order,s.name");
    }

    @PostMapping("/sources")
    public Map<String,Object> addSource(@Valid @RequestBody SourceRequest request) {
        URI uri;
        try { uri=URI.create(request.baseUrl()); }
        catch(Exception ex) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"网址格式不正确"); }
        String host=uri.getHost();
        if(!"https".equalsIgnoreCase(uri.getScheme()) || host==null || !(host.equalsIgnoreCase("bigc.edu.cn") || host.toLowerCase().endsWith(".bigc.edu.cn")))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"来源必须是 HTTPS 的 bigc.edu.cn 官方网址");
        Integer zone=jdbc.queryForObject("select count(*) from zones where id=? and active=true",Integer.class,request.zoneId());
        if(zone==null || zone==0) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"专区不存在");
        UUID id=UUID.randomUUID();
        try { jdbc.update("insert into data_sources(id,zone_id,name,base_url) values (?,?,?,?)",id,request.zoneId(),request.name().trim(),uri.toString()); }
        catch(Exception ex) { throw new ResponseStatusException(HttpStatus.CONFLICT,"该来源网址已存在"); }
        return Map.of("id",id.toString(),"zoneId",request.zoneId(),"name",request.name(),"baseUrl",uri.toString());
    }

    @PostMapping("/crawl")
    public Map<String,Object> crawl() {
        boolean started=crawler.startCrawl();
        if(!started) throw new ResponseStatusException(HttpStatus.CONFLICT,"采集任务正在运行");
        return Map.of("accepted",true,"message","采集任务已启动");
    }

    @GetMapping("/crawl-runs")
    public List<Map<String,Object>> runs() { return crawler.runs(); }

    @PostMapping("/knowledge/import")
    public KnowledgeService.ImportResult importPdf(@RequestPart("file") MultipartFile file) throws Exception {
        if(file.isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"文件为空");
        if(file.getSize()>80L*1024*1024) throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE,"文件不能超过 80 MB");
        return knowledge.importPdf(file.getOriginalFilename(),file.getBytes());
    }

    @GetMapping("/knowledge/documents")
    public List<Map<String,Object>> documents() { return knowledge.documents(); }

    @GetMapping("/users")
    public List<Map<String,Object>> users() {
        return jdbc.queryForList("select id::text as id,username,email,role,active,created_at from app_users order by created_at desc limit 200");
    }

    @PutMapping("/users/{id}")
    public Map<String,Object> updateUser(@PathVariable String id,@Valid @RequestBody UserUpdateRequest request,Authentication auth) {
        UUID userId;
        try { userId=UUID.fromString(id); }
        catch(IllegalArgumentException ex) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"用户编号无效"); }
        String role=request.role().trim().toUpperCase();
        if(!role.equals("STUDENT")&&!role.equals("ADMIN"))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"角色只能是 STUDENT 或 ADMIN");
        List<Map<String,Object>> target=jdbc.queryForList("select username,role from app_users where id=?",userId);
        if(target.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"用户不存在");
        String username=(String)target.get(0).get("username");
        String currentRole=(String)target.get(0).get("role");
        if(username.equals(auth.getName())&&(!request.active()||!role.equals("ADMIN")))
            throw new ResponseStatusException(HttpStatus.CONFLICT,"不能停用或降级当前管理员账号");
        if(currentRole.equals("ADMIN")&&(!request.active()||!role.equals("ADMIN"))) {
            Integer others=jdbc.queryForObject("select count(*) from app_users where role='ADMIN' and active=true and id<>?",Integer.class,userId);
            if(others==null||others==0) throw new ResponseStatusException(HttpStatus.CONFLICT,"至少保留一个启用中的管理员");
        }
        jdbc.update("update app_users set role=?,active=? where id=?",role,request.active(),userId);
        return jdbc.queryForMap("select id::text as id,username,email,role,active,created_at from app_users where id=?",userId);
    }
}