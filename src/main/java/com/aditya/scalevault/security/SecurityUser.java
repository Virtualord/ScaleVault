package com.aditya.scalevault.security;

import com.aditya.scalevault.entity.Role;
import com.aditya.scalevault.entity.User;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.Collections;
import java.util.Objects;
import java.util.UUID;

public class SecurityUser implements UserDetails {

    private final UUID id;
    private final String email;
    private final String passwordHash;
    private final Role role;
    private final boolean enabled;
    private final Collection<? extends GrantedAuthority> authorities;

    public SecurityUser(User user) {
        this.id = Objects.requireNonNull(user.getId(), "User ID cannot be null");
        this.email = Objects.requireNonNull(user.getEmail(), "User email cannot be null");
        this.passwordHash = Objects.requireNonNull(user.getPasswordHash(), "User password hash cannot be null");
        this.role = Objects.requireNonNull(user.getRole(), "User role cannot be null");
        this.enabled = user.isEnabled();
        this.authorities = Collections.singletonList(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()));
    }

    public UUID getId() {
        return id;
    }

    public Role getRole() {
        return role;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }
}
