package cn.bigc.yinyuan.api;

import cn.bigc.yinyuan.service.ChatService;
import java.util.List;
import java.util.Map;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/chat/sessions")
public class ChatController {
    private final ChatService service;
    public ChatController(ChatService service) { this.service=service; }

    public record CreateSessionRequest(String title) {}

    @GetMapping
    public List<Map<String,Object>> sessions(Authentication auth) { return service.sessions(auth.getName()); }

    @PostMapping
    public Map<String,Object> create(Authentication auth,@RequestBody(required=false) CreateSessionRequest request) {
        return service.createSession(auth.getName(),request==null?null:request.title());
    }

    @GetMapping("/{id}/messages")
    public List<Map<String,Object>> messages(Authentication auth,@PathVariable String id) { return service.messages(auth.getName(),id); }

    @DeleteMapping("/{id}")
    public Map<String,Object> delete(Authentication auth,@PathVariable String id) {
        service.deleteSession(auth.getName(),id);
        return Map.of("deleted",true);
    }

    @PostMapping(value="/{id}/stream",produces="text/event-stream")
    public SseEmitter stream(Authentication auth,@PathVariable String id,@RequestBody ChatService.ChatRequest request) {
        return service.stream(auth.getName(),id,request);
    }
}
