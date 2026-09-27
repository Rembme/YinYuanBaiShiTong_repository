package cn.bigc.yinyuan.api;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class PlatformController {
    private final JdbcTemplate jdbc;
    public PlatformController(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @GetMapping("/health")
    public Map<String,Object> health() { return Map.of("status", "ok", "service", "印苑百事通"); }

    @GetMapping("/zones")
    public List<Map<String,Object>> zones() {
        return jdbc.queryForList("select id,name,description,sort_order from zones where active=true order by sort_order");
    }

    @GetMapping("/sources")
    public List<Map<String,Object>> sources() {
        return jdbc.queryForList("select s.id::text as id,s.zone_id,z.name as zone_name,s.name,s.base_url,s.last_crawled_at,s.last_status from data_sources s join zones z on z.id=s.zone_id where s.active=true order by z.sort_order,s.name");
    }

    @GetMapping("/notices")
    public List<Map<String,Object>> notices(@RequestParam(required=false) String q,
                                            @RequestParam(required=false) String zoneId,
                                            @RequestParam(defaultValue="50") int limit) {
        StringBuilder sql = new StringBuilder("select n.id::text as id,n.zone_id,z.name as zone_name,n.title,n.body,n.canonical_url,n.published_at,n.fetched_at from notices n join zones z on z.id=n.zone_id where n.status='ACTIVE'");
        List<Object> args = new ArrayList<>();
        if (zoneId != null && !zoneId.isBlank()) { sql.append(" and n.zone_id=?"); args.add(zoneId); }
        if (q != null && !q.isBlank()) {
            sql.append(" and (n.title ilike ? or n.body ilike ?)");
            args.add("%" + q.trim() + "%"); args.add("%" + q.trim() + "%");
        }
        sql.append(" order by n.published_at desc nulls last,n.fetched_at desc limit ?");
        args.add(Math.max(1, Math.min(limit, 100)));
        return jdbc.queryForList(sql.toString(), args.toArray());
    }
}
