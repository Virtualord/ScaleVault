package com.aditya.scalevault.security;

import com.aditya.scalevault.entity.User;
import com.aditya.scalevault.repository.UserRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    public CustomUserDetailsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        if (username == null || username.isBlank()) {
            throw new UsernameNotFoundException("Email cannot be empty");
        }

        String normalizedEmail = username.trim().toLowerCase();
        User user = userRepository.findByEmailIgnoreCase(normalizedEmail)
            .orElseThrow(() -> new UsernameNotFoundException("User not found with email: " + normalizedEmail));

        return new SecurityUser(user);
    }
}
