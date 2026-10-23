package com.queueforge.api.config;

import com.queueforge.api.user.AppUser;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

public record AppPrincipal(UUID id, String name, String email, String passwordHash) implements UserDetails {
    public static AppPrincipal from(AppUser user) {
        return new AppPrincipal(user.getId(), user.getName(), user.getEmail(), user.getPasswordHash());
    }

    @Override public Collection<? extends GrantedAuthority> getAuthorities() { return List.of(); }
    @Override public String getPassword() { return passwordHash; }
    @Override public String getUsername() { return email; }
    @Override public boolean isAccountNonExpired() { return true; }
    @Override public boolean isAccountNonLocked() { return true; }
    @Override public boolean isCredentialsNonExpired() { return true; }
    @Override public boolean isEnabled() { return true; }
}

