package vn.glassliving.auth.security;

import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import vn.glassliving.auth.entity.User;

import java.util.Collection;
import java.util.UUID;

@Getter
public class AppUserDetails implements UserDetails {

    private final UUID id;
    private final String email;
    private final String fullName;
    private final String passwordHash;
    private final boolean enabled;
    private final Collection<? extends GrantedAuthority> authorities;

    public AppUserDetails(User u) {
        this.id = u.getId();
        this.email = u.getEmail();
        this.fullName = u.getFullName();
        this.passwordHash = u.getPasswordHash();
        this.enabled = u.getStatus() == User.UserStatus.ACTIVE;
        this.authorities = u.getRoles().stream()
                .map(r -> (GrantedAuthority) new SimpleGrantedAuthority("ROLE_" + r.name()))
                .toList();
    }

    @Override public String getPassword() { return passwordHash; }
    @Override public String getUsername() { return email; }

    /** First character of full name — exposed for sec:authentication="principal.initial" in templates. */
    public String getInitial() {
        if (fullName == null || fullName.isBlank()) return "U";
        return fullName.substring(0, 1).toUpperCase();
    }
    @Override public boolean isAccountNonExpired() { return true; }
    @Override public boolean isAccountNonLocked() { return enabled; }
    @Override public boolean isCredentialsNonExpired() { return true; }
    @Override public boolean isEnabled() { return enabled; }
}
