package cn.bigc.yinyuan.api;

import cn.bigc.yinyuan.service.WeeklyReportService;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/weekly")
public class WeeklyController {
    private final WeeklyReportService reports;
    private final JdbcTemplate jdbc;
    public WeeklyController(WeeklyReportService reports,JdbcTemplate jdbc){this.reports=reports;this.jdbc=jdbc;}
    @GetMapping
    public List<Map<String,Object>> list(Authentication auth){return reports.reports(auth.getName());}
    @GetMapping("/summary")
    public Map<String,Object> summary(Authentication auth){
        return Map.of("subscribedZones",jdbc.queryForObject("select count(*) from subscriptions where user_id=(select id from app_users where username=?)",Long.class,auth.getName()),
                "reports",jdbc.queryForObject("select count(*) from weekly_reports where user_id=(select id from app_users where username=?)",Long.class,auth.getName()));
    }
}
