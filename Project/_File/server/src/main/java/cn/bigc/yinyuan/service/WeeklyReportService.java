package cn.bigc.yinyuan.service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class WeeklyReportService {
    private static final Logger log=LoggerFactory.getLogger(WeeklyReportService.class);
    private final JdbcTemplate jdbc;
    private final DeepSeekService deepSeek;
    private final ObjectProvider<JavaMailSender> mailProvider;
    private final String mailFrom,mailHost;
    private final boolean enabled;

    public WeeklyReportService(JdbcTemplate jdbc,DeepSeekService deepSeek,ObjectProvider<JavaMailSender> mailProvider,
                              @Value("$"+"{app.mail.from:}") String mailFrom,
                              @Value("$"+"{spring.mail.host:}") String mailHost,
                              @Value("$"+"{app.weekly.enabled:true}") boolean enabled) {
        this.jdbc=jdbc; this.deepSeek=deepSeek; this.mailProvider=mailProvider;
        this.mailFrom=mailFrom; this.mailHost=mailHost; this.enabled=enabled;
    }

    @Scheduled(cron="0 0 8 * * MON",zone="Asia/Shanghai")
    public void scheduled() {
        if(!enabled) return;
        LocalDate today=LocalDate.now(ZoneId.of("Asia/Shanghai"));
        LocalDate end=today.with(TemporalAdjusters.previousOrSame(java.time.DayOfWeek.SUNDAY));
        generateForAll(end.minusDays(6),end);
    }

    public List<Map<String,Object>> reports(String username) {
        return jdbc.queryForList("select id::text as id,period_start,period_end,content,status,email_status,created_at from weekly_reports where user_id=(select id from app_users where username=?) order by period_end desc limit 30",username);
    }

    private void generateForAll(LocalDate start,LocalDate end) {
        List<Map<String,Object>> users=jdbc.queryForList("select u.id::text as id,u.username,u.email,u.weekly_email_enabled from app_users u where exists(select 1 from subscriptions s where s.user_id=u.id)");
        for(Map<String,Object> user:users) {
            try {
                generateOne((String)user.get("id"),(String)user.get("username"),(String)user.get("email"),
                        Boolean.TRUE.equals(user.get("weekly_email_enabled")),start,end);
            } catch(Exception ex) {
                log.warn("Weekly report failed for one user: {}",ex.getClass().getSimpleName());
            }
        }
    }

    private void generateOne(String userId,String username,String email,boolean emailOptIn,LocalDate start,LocalDate end) throws Exception {
        List<Map<String,Object>> items=jdbc.queryForList("select n.title,n.body,n.canonical_url,n.published_at,z.name as zone_name from notices n join zones z on z.id=n.zone_id join subscriptions s on s.zone_id=n.zone_id where s.user_id=?::uuid and n.status='ACTIVE' and n.published_at >= ? and n.published_at < ? order by n.published_at desc limit 100",
                userId,start.atStartOfDay(ZoneId.of("Asia/Shanghai")).toInstant(),end.plusDays(1).atStartOfDay(ZoneId.of("Asia/Shanghai")).toInstant());
        String report;
        if(items.isEmpty()) report="# 本周校园周报\n\n统计周期："+start+" 至 "+end+"\n\n你订阅的专区本周没有采集到新通知。";
        else {
            StringBuilder source=new StringBuilder();
            for(Map<String,Object> item:items) source.append("- ").append(item.get("zone_name")).append("｜").append(item.get("title")).append("｜").append(item.get("canonical_url")).append("\n").append(shorten(String.valueOf(item.get("body")),400)).append("\n");
            if(deepSeek.configured()) {
                String prompt="请根据以下校园通知整理简明周报，保留专区、原文链接和日期。只使用提供的信息，不要补充外部事实。统计周期："+start+" 至 "+end+"。\n\n"+source;
                report=deepSeek.complete(List.of(Map.of("role","system","content","你是校园通知周报整理助手，只依据输入材料写作。"),Map.of("role","user","content",prompt)));
            } else report=localReport(start,end,items);
        }
        UUID id=UUID.randomUUID();
        JavaMailSender sender=mailProvider.getIfAvailable();
        boolean mailReady=emailOptIn&&email!=null&&!email.isBlank()&&mailHost!=null&&!mailHost.isBlank()&&sender!=null;
        String emailStatus=!emailOptIn?"NOT_ENABLED":mailReady?"PENDING":"NOT_CONFIGURED";
        jdbc.update("insert into weekly_reports(id,user_id,period_start,period_end,content,status,email_status) values (?,?::uuid,?,?,?,'READY',?) on conflict(user_id,period_start,period_end) do update set content=excluded.content,status='READY',email_status=excluded.email_status,created_at=now()",
                id,userId,start,end,report,emailStatus);
        if(mailReady) {
            try {
                SimpleMailMessage message=new SimpleMailMessage();
                if(mailFrom!=null&&!mailFrom.isBlank()) message.setFrom(mailFrom);
                message.setTo(email);
                message.setSubject("印苑百事通｜本周校园通知周报");
                message.setText(report);
                sender.send(message);
                jdbc.update("update weekly_reports set email_status='SENT' where user_id=?::uuid and period_start=? and period_end=?",userId,start,end);
            } catch(Exception ex) {
                jdbc.update("update weekly_reports set email_status='FAILED' where user_id=?::uuid and period_start=? and period_end=?",userId,start,end);
                log.warn("Weekly report email delivery failed: {}",ex.getClass().getSimpleName());
            }
        }
    }

    private String localReport(LocalDate start,LocalDate end,List<Map<String,Object>> items) {
        StringBuilder out=new StringBuilder("# 本周校园周报\n\n统计周期：").append(start).append(" 至 ").append(end).append("\n\n");
        String current="";
        for(Map<String,Object> item:items) {
            String zone=String.valueOf(item.get("zone_name"));
            if(!zone.equals(current)){current=zone;out.append("## ").append(zone).append("\n\n");}
            out.append("- **").append(item.get("title")).append("**：").append(shorten(String.valueOf(item.get("body")),220)).append(" [查看原文](").append(item.get("canonical_url")).append(")\n");
        }
        return out.toString();
    }

    private String shorten(String value,int max) {
        String clean=value==null?"":value.replaceAll("\\s+"," ").trim();
        return clean.length()<=max?clean:clean.substring(0,max)+"…";
    }
}