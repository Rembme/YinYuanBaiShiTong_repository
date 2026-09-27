package cn.bigc.yinyuan.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ChatService {
    private static final Logger log=LoggerFactory.getLogger(ChatService.class);
    private final JdbcTemplate jdbc;
    private final KnowledgeService knowledge;
    private final DeepSeekService deepSeek;
    private final ObjectMapper mapper;
    private final Executor executor;

    public record Citation(String type,String title,String url,Integer page,String section,String excerpt,String publishedAt) {}
    public record ChatRequest(String message) {}

    public ChatService(JdbcTemplate jdbc, KnowledgeService knowledge, DeepSeekService deepSeek,
                       ObjectMapper mapper, @Qualifier("chatExecutor") Executor executor) {
        this.jdbc=jdbc; this.knowledge=knowledge; this.deepSeek=deepSeek; this.mapper=mapper; this.executor=executor;
    }

    public List<Map<String,Object>> sessions(String username) {
        return jdbc.queryForList("select id::text as id,title,created_at,updated_at from chat_sessions where user_id=(select id from app_users where username=?) order by updated_at desc",username);
    }

    public Map<String,Object> createSession(String username,String title) {
        UUID id=UUID.randomUUID();
        String clean=title==null||title.isBlank()?"新会话":title.trim();
        if(clean.length()>100) clean=clean.substring(0,100);
        jdbc.update("insert into chat_sessions(id,user_id,title) select ?,id,? from app_users where username=?",id,clean,username);
        return jdbc.queryForMap("select id::text as id,title,created_at,updated_at from chat_sessions where id=?",id);
    }

    public void deleteSession(String username,String id) {
        requireSession(username,id);
        jdbc.update("delete from chat_sessions where id=?::uuid",id);
    }

    public List<Map<String,Object>> messages(String username,String id) {
        requireSession(username,id);
        return jdbc.query("select id::text as id,role,content,citations_json::text as citations,created_at from chat_messages where session_id=?::uuid order by created_at",
                (rs,row)->{
                    Map<String,Object> item=new LinkedHashMap<>();
                    item.put("id",rs.getString("id")); item.put("role",rs.getString("role")); item.put("content",rs.getString("content"));
                    try { item.put("citations",mapper.readTree(rs.getString("citations"))); } catch(Exception ex){ item.put("citations",List.of()); }
                    item.put("createdAt",rs.getTimestamp("created_at").toInstant().toString());
                    return item;
                },id);
    }

    public SseEmitter stream(String username,String sessionId,ChatRequest request) {
        if(request==null || request.message()==null || request.message().isBlank())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"请输入问题");
        String question=request.message().trim();
        if(question.length()>4000) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"问题长度不能超过 4000 字");
        requireSession(username,sessionId);
        List<Map<String,String>> history=loadHistory(sessionId);
        jdbc.update("insert into chat_messages(id,session_id,role,content) values (?,?::uuid,'USER',?)",UUID.randomUUID(),sessionId,question);
        jdbc.update("update chat_sessions set updated_at=now(),title=case when title='新会话' then ? else title end where id=?::uuid",
                question.length()>36?question.substring(0,36):question,sessionId);
        SseEmitter emitter=new SseEmitter(240000L);
        CompletableFuture.runAsync(()->runAnswer(username,sessionId,question,history,emitter),executor);
        return emitter;
    }

    private void runAnswer(String username,String sessionId,String question,List<Map<String,String>> history,SseEmitter emitter) {
        StringBuilder answer=new StringBuilder();
        List<Citation> citations=new ArrayList<>();
        try {
            boolean noticeIntent=containsAny(question,"通知","公告","公示","新闻","报名","截止","讲座","招聘","会议","培训","竞赛","招生","就业","活动","本周","最近","最新","近期","今天","明天");
            boolean policyIntent=containsAny(question,"制度","规定","流程","办法","细则","学籍","转专业","请假","考试","学分","毕业","选课","教材","实践教学","教学");
            if(!noticeIntent&&!policyIntent){noticeIntent=true;policyIntent=true;}
            if(noticeIntent) citations.addAll(findNotices(question));
            if(policyIntent) {
                for(KnowledgeService.SearchHit hit:knowledge.search(question,4))
                    citations.add(new Citation("policy",hit.document(),null,hit.page(),hit.section(),shorten(hit.content(),1000),null));
            }
            for(int i=0;i<citations.size();i++) sendEvent(emitter,"citation",mapper.writeValueAsString(citations.get(i)));
            List<Map<String,String>> messages=prompt(history,question,citations);
            if(deepSeek.configured()) {
                try {
                    deepSeek.stream(messages,token->{answer.append(token); try{sendEvent(emitter,"token",token);}catch(IOException ex){throw new IllegalStateException(ex);}});
                } catch(Exception ex) {
                    log.warn("DeepSeek response unavailable; returning retrieved sources. Cause: {}",ex.getClass().getSimpleName());
                    answer.setLength(0);
                    emitLocally(emitter,answer,localAnswer(citations));
                }
            } else {
                emitLocally(emitter,answer,localAnswer(citations));
            }
            if(answer.isEmpty()) answer.append(localAnswer(citations));
            jdbc.update("insert into chat_messages(id,session_id,role,content,citations_json) values (?,?::uuid,'ASSISTANT',?,?::jsonb)",
                    UUID.randomUUID(),sessionId,answer.toString(),mapper.writeValueAsString(citations));
            sendEvent(emitter,"done",mapper.writeValueAsString(Map.of("messageId",UUID.randomUUID().toString(),"aiConfigured",deepSeek.configured())));
            emitter.complete();
        } catch(Exception ex) {
            log.warn("Chat request failed: {}",ex.getClass().getSimpleName());
            try { sendEvent(emitter,"error","暂时无法完成检索，请稍后重试。"); } catch(Exception ignored) {}
            emitter.completeWithError(ex);
        }
    }

    private boolean containsAny(String value,String... markers) {
        for(String marker:markers) if(value.contains(marker)) return true;
        return false;
    }
    private List<Citation> findNotices(String query) {
        List<String> terms=new ArrayList<>();
        for(String marker:List.of("通知","公告","公示","新闻","报名","截止","讲座","招聘","会议","培训","竞赛","招生","就业","活动","本周","最近","最新","近期","今天","明天")) {
            if(query.contains(marker)&&!terms.contains(marker)) terms.add(marker);
            if(terms.size()>=4) break;
        }
        if(terms.isEmpty()) terms.add(query.replaceAll("[，。？！,?! ]"," ").trim());
        StringBuilder sql=new StringBuilder("select title,canonical_url,body,published_at from notices where status='ACTIVE' and (");
        List<Object> args=new ArrayList<>();
        for(int i=0;i<terms.size();i++) {
            if(i>0) sql.append(" or ");
            sql.append("(title ilike ? escape '\\' or body ilike ? escape '\\')");
            String pattern="%"+terms.get(i).replace("%","\\%").replace("_","\\_")+"%";
            args.add(pattern); args.add(pattern);
        }
        sql.append(") order by case when title ilike ? escape '\\' then 0 else 1 end,published_at desc nulls last limit 4");
        args.add("%"+terms.get(0).replace("%","\\%").replace("_","\\_")+"%");
        return jdbc.query(sql.toString(),
                (rs,row)->new Citation("notice",rs.getString("title"),rs.getString("canonical_url"),null,null,shorten(rs.getString("body"),900),rs.getTimestamp("published_at")==null?null:rs.getTimestamp("published_at").toInstant().toString()),args.toArray());
    }
    private List<Map<String,String>> loadHistory(String sessionId) {
        List<Map<String,String>> rows=jdbc.query("select role,content from chat_messages where session_id=?::uuid order by created_at desc limit 40",
                (rs,row)->Map.of("role",rs.getString("role").equals("USER")?"user":"assistant","content",rs.getString("content")),sessionId);
        Collections.reverse(rows);
        return rows;
    }

    private List<Map<String,String>> prompt(List<Map<String,String>> history,String question,List<Citation> citations) {
        StringBuilder context=new StringBuilder();
        for(int i=0;i<citations.size();i++) {
            Citation c=citations.get(i);
            context.append("[来源 ").append(i+1).append("] ").append(c.title()).append("\n");
            if(c.url()!=null) context.append("链接：").append(c.url()).append("\n");
            if(c.page()!=null) context.append("PDF 页码：").append(c.page()).append("；章节：").append(c.section()).append("\n");
            context.append(c.excerpt()).append("\n\n");
        }
        List<Map<String,String>> messages=new ArrayList<>();
        messages.add(Map.of("role","system","content","你是北京印刷学院校园助手。严格依据给出的检索材料回答；不要补造校规、日期或通知。区分最新通知和制度文件。材料不足时说明未找到可靠依据，并建议核对学校原文。回答简明、中文优先。\n检索材料：\n"+context));
        int from=Math.max(0,history.size()-40);
        messages.addAll(history.subList(from,history.size()));
        messages.add(Map.of("role","user","content",question));
        return messages;
    }

    private String localAnswer(List<Citation> citations) {
        if(citations.isEmpty()) return "我在当前已导入的通知和制度资料中没有找到足够依据。可以换一种问法，或让管理员先更新对应专区的数据。";
        StringBuilder out=new StringBuilder("我在本地资料中找到以下相关内容：\n\n");
        int n=Math.min(citations.size(),5);
        for(int i=0;i<n;i++) {
            Citation c=citations.get(i);
            out.append(i+1).append(". ").append(c.title()).append("：").append(c.excerpt()).append("\n");
            if(c.page()!=null) out.append("   位置：PDF 第 ").append(c.page()).append(" 页").append(c.section()==null?"":"，"+c.section()).append("\n");
            if(c.url()!=null) out.append("   原文：").append(c.url()).append("\n");
            out.append("\n");
        }
        out.append("当前为本地检索结果；配置 DeepSeek 后可启用基于这些材料的生成式总结。");
        return out.toString();
    }

    private void emitLocally(SseEmitter emitter,StringBuilder answer,String text) {
        int step=48;
        for(int start=0;start<text.length();start+=step) {
            String piece=text.substring(start,Math.min(start+step,text.length()));
            answer.append(piece);
            try { sendEvent(emitter,"token",piece); } catch(IOException ex) { throw new IllegalStateException(ex); }
        }
    }

    private String shorten(String value,int max) {
        if(value==null) return "";
        String clean=value.replaceAll("\\s+"," ").trim();
        return clean.length()<=max?clean:clean.substring(0,max)+"…";
    }

    private void sendEvent(SseEmitter emitter,String name,String data) throws IOException {
        emitter.send(SseEmitter.event().name(name).data(data));
    }

    private void requireSession(String username,String id) {
        Integer count=jdbc.queryForObject("select count(*) from chat_sessions c join app_users u on u.id=c.user_id where c.id=?::uuid and u.username=?",Integer.class,id,username);
        if(count==null || count==0) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"会话不存在");
    }
}
