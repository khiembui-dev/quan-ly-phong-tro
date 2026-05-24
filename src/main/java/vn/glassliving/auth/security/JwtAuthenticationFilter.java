package vn.glassliving.auth.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import vn.glassliving.auth.entity.User;
import vn.glassliving.auth.repository.UserRepository;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UserRepository userRepository;

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {

        String token = extractToken(req);
        if (token != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            try {
                Claims claims = jwtService.parse(token);
                if (!"access".equals(claims.get("type"))) {
                    chain.doFilter(req, res); return;
                }
                UUID userId = UUID.fromString(claims.getSubject());
                User user = userRepository.findById(userId).orElse(null);
                if (user != null && user.getStatus() == User.UserStatus.ACTIVE) {
                    AppUserDetails principal = new AppUserDetails(user);
                    var auth = new UsernamePasswordAuthenticationToken(
                            principal, null, principal.getAuthorities());
                    auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(req));
                    SecurityContextHolder.getContext().setAuthentication(auth);
                }
            } catch (JwtException ex) {
                log.debug("Invalid JWT: {}", ex.getMessage());
            } catch (Exception ex) {
                log.warn("JWT filter error", ex);
            }
        }
        chain.doFilter(req, res);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        // Only run on /api/** — web routes use session auth.
        return !request.getRequestURI().startsWith("/api/");
    }

    private String extractToken(HttpServletRequest req) {
        String h = req.getHeader("Authorization");
        if (h != null && h.startsWith("Bearer ")) return h.substring(7);
        // Also accept httpOnly cookie "access_token"
        if (req.getCookies() != null) {
            for (var c : req.getCookies()) {
                if ("access_token".equals(c.getName())) return c.getValue();
            }
        }
        return null;
    }

    @SuppressWarnings("unused")
    private static List<SimpleGrantedAuthority> mapRoles(List<String> roles) {
        return roles.stream().map(r -> new SimpleGrantedAuthority("ROLE_" + r)).toList();
    }
}
