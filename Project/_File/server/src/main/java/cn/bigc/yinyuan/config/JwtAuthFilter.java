package cn.bigc.yinyuan.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class JwtAuthFilter extends OncePerRequestFilter {
    private final JwtService jwtService;
    private final JdbcTemplate jdbc;

    public JwtAuthFilter(JwtService jwtService,JdbcTemplate jdbc) {
        this.jwtService=jwtService;
        this.jdbc=jdbc;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain) throws ServletException,IOException {
        String header=request.getHeader("Authorization");
        if(header!=null&&header.startsWith("Bearer ")) {
            try {
                String username=jwtService.parse(header.substring(7)).getSubject();
                List<String> roles=jdbc.query("select role from app_users where username=? and active=true",
                        (rs,row)->rs.getString("role"),username);
                if(!roles.isEmpty()) {
                    var auth=new UsernamePasswordAuthenticationToken(username,null,
                            List.of(new SimpleGrantedAuthority("ROLE_"+roles.get(0))));
                    SecurityContextHolder.getContext().setAuthentication(auth);
                } else {
                    SecurityContextHolder.clearContext();
                }
            } catch(RuntimeException ignored) {
                SecurityContextHolder.clearContext();
            }
        }
        chain.doFilter(request,response);
    }
}