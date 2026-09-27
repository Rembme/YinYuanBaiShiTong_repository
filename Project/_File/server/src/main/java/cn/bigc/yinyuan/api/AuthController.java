package cn.bigc.yinyuan.api;

import cn.bigc.yinyuan.config.JwtService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final JdbcTemplate jdbc;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    public AuthController(JdbcTemplate jdbc,PasswordEncoder passwordEncoder,JwtService jwtService){this.jdbc=jdbc;this.passwordEncoder=passwordEncoder;this.jwtService=jwtService;}

    public record RegisterRequest(@NotBlank @Size(min=3,max=80) String username,
                                  @NotBlank @Size(min=8,max=120) String password,
                                  @Email String email,boolean weeklyEmailOptIn){}
    public record LoginRequest(@NotBlank String username,@NotBlank String password){}
    public record PreferencesRequest(@Email String email,boolean weeklyEmailOptIn){}
    public record AuthResponse(String token,String username,String role,String email,boolean weeklyEmailOptIn){}

    @PostMapping("/register")
    public AuthResponse register(@Valid @RequestBody RegisterRequest request){
        Integer count=jdbc.queryForObject("select count(*) from app_users where lower(username)=lower(?)",Integer.class,request.username());
        if(count!=null&&count>0)throw new ResponseStatusException(HttpStatus.CONFLICT,"用户名已存在");
        String email=request.email()==null||request.email().isBlank()?null:request.email().trim();
        if(request.weeklyEmailOptIn()&&email==null)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"同意邮件周报时请填写邮箱");
        if(email!=null){Integer emailCount=jdbc.queryForObject("select count(*) from app_users where lower(email)=lower(?)",Integer.class,email);if(emailCount!=null&&emailCount>0)throw new ResponseStatusException(HttpStatus.CONFLICT,"邮箱已被使用");}
        String username=request.username().trim();
        jdbc.update("insert into app_users(id,username,email,password_hash,role,weekly_email_enabled) values (?,?,?,?, 'STUDENT',?)",UUID.randomUUID(),username,email,passwordEncoder.encode(request.password()),request.weeklyEmailOptIn());
        return new AuthResponse(jwtService.issue(username,"STUDENT"),username,"STUDENT",email,request.weeklyEmailOptIn());
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request){
        var users=jdbc.query("select username,email,password_hash,role,active,weekly_email_enabled from app_users where lower(username)=lower(?)",
                (rs,row)->Map.<String,Object>of("username",rs.getString("username"),"email",rs.getString("email")==null?"":rs.getString("email"),"password",rs.getString("password_hash"),"role",rs.getString("role"),"active",rs.getBoolean("active"),"weeklyEmailOptIn",rs.getBoolean("weekly_email_enabled")),request.username());
        if(users.isEmpty())throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"账号或密码不正确");
        Map<String,Object> user=users.get(0);
        if(!Boolean.TRUE.equals(user.get("active"))||!passwordEncoder.matches(request.password(),(String)user.get("password")))throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"账号或密码不正确");
        String role=(String)user.get("role"),email=((String)user.get("email")).isBlank()?null:(String)user.get("email");
        return new AuthResponse(jwtService.issue((String)user.get("username"),role),(String)user.get("username"),role,email,(Boolean)user.get("weeklyEmailOptIn"));
    }

    @GetMapping("/me")
    public Map<String,Object> me(Authentication auth){return jdbc.queryForMap("select id::text as id,username,email,role,weekly_email_enabled,created_at from app_users where username=?",auth.getName());}

    @PutMapping("/preferences")
    public Map<String,Object> updatePreferences(Authentication auth,@Valid @RequestBody PreferencesRequest request){
        String email=request.email()==null||request.email().isBlank()?null:request.email().trim();
        if(request.weeklyEmailOptIn()&&email==null)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"启用邮件周报时请填写邮箱");
        if(email!=null){Integer count=jdbc.queryForObject("select count(*) from app_users where lower(email)=lower(?) and username<>?",Integer.class,email,auth.getName());if(count!=null&&count>0)throw new ResponseStatusException(HttpStatus.CONFLICT,"邮箱已被其他账号使用");}
        jdbc.update("update app_users set email=?,weekly_email_enabled=? where username=?",email,request.weeklyEmailOptIn(),auth.getName());
        return me(auth);
    }
}
