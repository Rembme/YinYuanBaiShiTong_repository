package cn.bigc.yinyuan.api;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/subscriptions")
public class SubscriptionController {
    private final JdbcTemplate jdbc;
    public SubscriptionController(JdbcTemplate jdbc){this.jdbc=jdbc;}
    public record SubscriptionRequest(String zoneId){}
    public record IntentRequest(String sessionId,String message){}
    public record IntentResponse(boolean handled,String message){}

    @GetMapping
    public List<Map<String,Object>> list(Authentication auth){
        return jdbc.queryForList("select z.id,z.name,z.description,(s.user_id is not null) as subscribed from zones z left join subscriptions s on s.zone_id=z.id and s.user_id=(select id from app_users where username=?) where z.active=true order by z.sort_order",auth.getName());
    }

    @PostMapping
    public Map<String,Object> subscribe(Authentication auth,@RequestBody SubscriptionRequest request){
        Integer exists=jdbc.queryForObject("select count(*) from zones where id=? and active=true",Integer.class,request.zoneId());
        if(exists==null||exists==0)throw new ResponseStatusException(HttpStatus.NOT_FOUND,"专区不存在");
        jdbc.update("insert into subscriptions(user_id,zone_id) select id,? from app_users where username=? on conflict do nothing",request.zoneId(),auth.getName());
        return Map.of("zoneId",request.zoneId(),"subscribed",true);
    }

    @DeleteMapping("/{zoneId}")
    public Map<String,Object> unsubscribe(Authentication auth,@PathVariable String zoneId){
        jdbc.update("delete from subscriptions where user_id=(select id from app_users where username=?) and zone_id=?",auth.getName(),zoneId);
        return Map.of("zoneId",zoneId,"subscribed",false);
    }

    @PostMapping("/intent")
    public IntentResponse intent(Authentication auth,@RequestBody IntentRequest request){
        if(request==null||request.message()==null)return new IntentResponse(false,"");
        String text=request.message().trim();
        boolean action=text.contains("订阅")||text.contains("退订")||text.contains("关注");
        if(!action)return new IntentResponse(false,"");
        List<Map<String,Object>> zones=jdbc.queryForList("select id,name from zones where active=true");
        List<Map<String,Object>> matched=new ArrayList<>();
        String lower=text.toLowerCase();
        for(Map<String,Object> zone:zones){
            String id=String.valueOf(zone.get("id")),name=String.valueOf(zone.get("name"));
            boolean hit=text.contains(name);
            if(id.equals("mie"))hit|=text.contains("信息工程")||lower.contains("mie");
            else if(id.equals("university"))hit|=text.contains("学校官网")||text.contains("北京印刷学院官网")||text.contains("学校通知");
            else if(id.equals("news"))hit|=text.contains("新闻网")||text.contains("校园新闻");
            else if(id.equals("other-official"))hit|=text.contains("其他官方")||text.contains("其他学校网页");
            if(hit)matched.add(zone);
        }
        String reply;
        if(matched.size()!=1){
            if(matched.isEmpty()&&!(text.contains("我想")||text.contains("帮我")||text.contains("请")||text.contains("我要")))return new IntentResponse(false,"");
            reply="请明确一个专区：信息工程学院、学校官网、校园新闻网或其他官方网页。";
        }else{
            String zoneId=String.valueOf(matched.get(0).get("id"));
            String zoneName=String.valueOf(matched.get(0).get("name"));
            boolean remove=text.contains("取消")||text.contains("退订")||text.contains("不再关注");
            if(remove){
                jdbc.update("delete from subscriptions where user_id=(select id from app_users where username=?) and zone_id=?",auth.getName(),zoneId);
                reply="已为你取消“"+zoneName+"”专区订阅。";
            }else{
                jdbc.update("insert into subscriptions(user_id,zone_id) select id,? from app_users where username=? on conflict do nothing",zoneId,auth.getName());
                reply="已为你订阅“"+zoneName+"”专区。之后的周报会汇总该专区的新通知。";
            }
        }
        if(request.sessionId()!=null&&!request.sessionId().isBlank()){
            Integer owns=jdbc.queryForObject("select count(*) from chat_sessions c join app_users u on u.id=c.user_id where c.id=?::uuid and u.username=?",Integer.class,request.sessionId(),auth.getName());
            if(owns==null||owns==0)throw new ResponseStatusException(HttpStatus.NOT_FOUND,"会话不存在");
            jdbc.update("insert into chat_messages(id,session_id,role,content) values (?,?::uuid,'USER',?)",UUID.randomUUID(),request.sessionId(),text);
            jdbc.update("insert into chat_messages(id,session_id,role,content,citations_json) values (?,?::uuid,'ASSISTANT',?,'[]'::jsonb)",UUID.randomUUID(),request.sessionId(),reply);
            jdbc.update("update chat_sessions set updated_at=now(),title=case when title='新会话' then ? else title end where id=?::uuid",text.length()>36?text.substring(0,36):text,request.sessionId());
        }
        return new IntentResponse(true,reply);
    }
}
