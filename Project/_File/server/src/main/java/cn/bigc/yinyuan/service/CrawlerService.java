package cn.bigc.yinyuan.service;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class CrawlerService {
    private static final Logger log=LoggerFactory.getLogger(CrawlerService.class);
    private static final Pattern INTERESTING=Pattern.compile("通知|公告|公示|讲座|招生|教务|招聘|会议|培训|竞赛|报名|就业|学术|科研|活动|新闻");
    private static final Pattern DETAIL_PATH=Pattern.compile("content|info|detail|notice|news|article|202[0-9]",Pattern.CASE_INSENSITIVE);
    private static final Pattern DATE=Pattern.compile("(20\\d{2})[年./-](\\d{1,2})[月./-](\\d{1,2})");
    private final JdbcTemplate jdbc;
    private final Executor executor;
    private final boolean scheduledEnabled;
    private final int maxLinks;
    private final int timeoutMs;
    private final AtomicBoolean running=new AtomicBoolean(false);

    public CrawlerService(JdbcTemplate jdbc,@Qualifier("chatExecutor") Executor executor,
                          @Value("${app.crawler.enabled:false}") boolean scheduledEnabled,
                          @Value("${app.crawler.max-links-per-source:24}") int maxLinks,
                          @Value("${app.crawler.timeout-ms:15000}") int timeoutMs) {
        this.jdbc=jdbc; this.executor=executor; this.scheduledEnabled=scheduledEnabled;
        this.maxLinks=Math.max(1,Math.min(maxLinks,100)); this.timeoutMs=Math.max(3000,Math.min(timeoutMs,30000));
    }

    public boolean startCrawl() {
        if(!running.compareAndSet(false,true)) return false;
        UUID runId=UUID.randomUUID();
        jdbc.update("insert into crawl_runs(id,status) values (?,'RUNNING')",runId);
        executor.execute(()->{
            int sourceCount=0,noticeCount=0;
            List<String> errors=new ArrayList<>();
            try {
                List<Map<String,Object>> sources=jdbc.queryForList("select id::text as id,zone_id,name,base_url from data_sources where active=true order by created_at");
                sourceCount=sources.size();
                for(Map<String,Object> source:sources) {
                    try { noticeCount+=crawlSource(source); }
                    catch(Exception ex) {
                        String message=source.get("name")+": "+ex.getClass().getSimpleName();
                        errors.add(message); log.warn("Crawler source failed: {}",message);
                        jdbc.update("update data_sources set last_status='FAILED',last_error=?,last_crawled_at=now() where id=?::uuid",message,source.get("id"));
                    }
                }
                String status=errors.isEmpty()?"SUCCEEDED":"COMPLETED_WITH_ERRORS";
                jdbc.update("update crawl_runs set finished_at=now(),status=?,source_count=?,notice_count=?,error_summary=? where id=?",
                        status,sourceCount,noticeCount,errors.isEmpty()?null:String.join("; ",errors),runId);
            } catch(Exception ex) {
                jdbc.update("update crawl_runs set finished_at=now(),status='FAILED',source_count=?,notice_count=?,error_summary=? where id=?",
                        sourceCount,noticeCount,ex.getClass().getSimpleName(),runId);
                log.error("Crawler run failed: {}",ex.getClass().getSimpleName());
            } finally { running.set(false); }
        });
        return true;
    }

    @Scheduled(cron="0 0 3 * * *",zone="Asia/Shanghai")
    public void scheduledCrawl() {
        if(scheduledEnabled) startCrawl();
    }

    public List<Map<String,Object>> runs() {
        return jdbc.queryForList("select id::text as id,started_at,finished_at,status,source_count,notice_count,error_summary from crawl_runs order by started_at desc limit 30");
    }

    public boolean isRunning() { return running.get(); }

    private int crawlSource(Map<String,Object> source) throws Exception {
        String baseUrl=(String)source.get("base_url");
        URI base=URI.create(baseUrl);
        validateHost(base);
        Document home=fetch(baseUrl);
        List<String> links=new ArrayList<>();
        Elements anchors=home.select("a[href]");
        for(Element anchor:anchors) {
            String url=anchor.absUrl("href");
            String title=anchor.text().replaceAll("\\s+"," ").trim();
            if(title.length()<4 || title.length()>160 || url.isBlank()) continue;
            URI uri;
            try { uri=URI.create(url); } catch(Exception ex) { continue; }
            if(!sameHost(base.getHost(),uri.getHost()) || !"https".equalsIgnoreCase(uri.getScheme())) continue;
            if(url.toLowerCase().matches(".*\\.(pdf|jpg|jpeg|png|gif|zip|doc|docx)([?#].*)?$")) continue;
            String hint=title+" "+uri.getPath();
            if(!INTERESTING.matcher(title).find() && !DETAIL_PATH.matcher(hint).find()) continue;
            if(!links.contains(url)) links.add(url);
            if(links.size()>=maxLinks) break;
        }
        int saved=0;
        for(String url:links) {
            try {
                Document page=fetch(url);
                page.select("script,style,noscript,iframe,nav,footer,header,form").remove();
                String title=page.select("h1").first()!=null?page.select("h1").first().text():page.title();
                String body=articleText(page);
                if(title==null || title.isBlank() || body.length()<100) continue;
                String hash=sha256(title+"\n"+body);
                jdbc.update("insert into notices(id,source_id,zone_id,title,body,canonical_url,published_at,content_hash,status) values (?::uuid,?::uuid,?,?,?,?,?,?,'ACTIVE') on conflict(canonical_url) do update set source_id=excluded.source_id,zone_id=excluded.zone_id,title=excluded.title,body=excluded.body,published_at=coalesce(excluded.published_at,notices.published_at),content_hash=excluded.content_hash,fetched_at=now(),updated_at=now(),status='ACTIVE'",
                        UUID.randomUUID().toString(),source.get("id"),source.get("zone_id"),title.trim(),body,url,publishedAt(page),hash);
                saved++;
            } catch(Exception ex) {
                log.debug("Skipping crawler page after parse/fetch issue: {}",ex.getClass().getSimpleName());
            }
        }
        jdbc.update("update data_sources set last_status='SUCCEEDED',last_error=null,last_crawled_at=now() where id=?::uuid",source.get("id"));
        return saved;
    }

    private Document fetch(String url) throws Exception {
        try { Thread.sleep(350L); }
        catch(InterruptedException ex) { Thread.currentThread().interrupt(); throw ex; }
        Connection.Response response=Jsoup.connect(url).userAgent("YinYuanBaiShiTong/0.1 (+campus notice index)")
                .timeout(timeoutMs).maxBodySize(3_000_000).followRedirects(true).execute();
        URI finalUri=URI.create(response.url().toString());
        validateHost(finalUri);
        return response.parse();
    }

    private void validateHost(URI uri) {
        String host=uri.getHost();
        if(!"https".equalsIgnoreCase(uri.getScheme()) || host==null || !sameHost("bigc.edu.cn",host))
            throw new IllegalArgumentException("采集来源必须使用 HTTPS 且属于 bigc.edu.cn 官方域名");
    }

    private boolean sameHost(String base,String target) {
        if(base==null || target==null) return false;
        String host=target.toLowerCase();
        String allowed=base.toLowerCase();
        return host.equals(allowed) || host.endsWith("."+allowed);
    }

    private String articleText(Document page) {
        for(String selector:List.of("article","#article","#content",".article-content",".content","main")) {
            Element element=page.selectFirst(selector);
            if(element!=null && element.text().length()>100) return element.text().replaceAll("\\s+"," ").trim();
        }
        return page.body()==null?"":page.body().text().replaceAll("\\s+"," ").trim();
    }

    private java.sql.Timestamp publishedAt(Document page) {
        String value=null;
        Element time=page.selectFirst("time[datetime]");
        if(time!=null) value=time.attr("datetime");
        if(value==null || value.isBlank()) {
            Matcher matcher=DATE.matcher(page.text());
            if(matcher.find()) value=matcher.group(1)+"-"+matcher.group(2)+"-"+matcher.group(3);
        }
        if(value==null) return null;
        try { return java.sql.Timestamp.valueOf(LocalDateTime.parse(value)); }
        catch(Exception ignored) { }
        try { return java.sql.Timestamp.valueOf(LocalDate.parse(value.substring(0,10)).atStartOfDay()); }
        catch(Exception ignored) { return null; }
    }

    private String sha256(String text) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));
    }
}
