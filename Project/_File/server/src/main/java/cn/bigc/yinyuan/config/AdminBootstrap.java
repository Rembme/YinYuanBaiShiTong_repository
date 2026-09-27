package cn.bigc.yinyuan.config;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class AdminBootstrap implements ApplicationRunner {
    private final JdbcTemplate jdbc;
    private final PasswordEncoder encoder;
    private final String username;
    private final String password;
    private final String email;

    public AdminBootstrap(JdbcTemplate jdbc,PasswordEncoder encoder,
                          @Value("$"+"{app.admin.username:}") String username,
                          @Value("$"+"{app.admin.password:}") String password,
                          @Value("$"+"{app.admin.email:}") String email) {
        this.jdbc=jdbc; this.encoder=encoder; this.username=username; this.password=password; this.email=email;
    }

    @Override
    public void run(ApplicationArguments args) {
        if(username==null||username.isBlank()||password==null||password.isBlank()) return;
        List<Map<String,Object>> existing=jdbc.queryForList("select username,role from app_users where lower(username)=lower(?)",username.trim());
        if(!existing.isEmpty()) {
            if(!"ADMIN".equals(existing.get(0).get("role")))
                throw new IllegalStateException("配置的管理员用户名已被学生账号占用，请更换 APP_ADMIN_USERNAME。");
            return;
        }
        jdbc.update("insert into app_users(id,username,email,password_hash,role) values (?,?,?,?, 'ADMIN')",
                UUID.randomUUID(),username.trim(),email==null||email.isBlank()?null:email.trim(),encoder.encode(password));
    }
}