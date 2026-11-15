package com.inventory.service;

import com.inventory.model.AppUser;
import com.inventory.repository.AppUserRepository;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class DbUserDetailsService implements UserDetailsService {
    private final AppUserRepository users;
    public DbUserDetailsService(AppUserRepository users) { this.users = users; }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        AppUser u = users.findByUsernameIgnoreCase(username)
            .orElseThrow(() -> new UsernameNotFoundException("Unknown user"));
        return User.withUsername(u.getUsername())
            .password(u.getPasswordHash())
            .roles(u.getRole().name())
            .disabled(!u.isEnabled())
            .build();
    }
}
