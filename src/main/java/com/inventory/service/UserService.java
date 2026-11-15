package com.inventory.service;

import com.inventory.dto.UserCreateRequest;
import com.inventory.dto.UserUpdateRequest;
import com.inventory.dto.UserView;
import com.inventory.model.AppUser;
import com.inventory.model.Role;
import com.inventory.repository.AppUserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
public class UserService {
    private final AppUserRepository users;
    private final PasswordEncoder encoder;

    public UserService(AppUserRepository users, PasswordEncoder encoder) {
        this.users = users;
        this.encoder = encoder;
    }

    @Transactional(readOnly = true)
    public List<UserView> list() { return users.findAllByOrderByUsernameAsc().stream().map(UserView::from).toList(); }

    @Transactional
    public UserView create(UserCreateRequest r) {
        String name = r.username().trim();
        if (users.existsByUsernameIgnoreCase(name)) throw new IllegalArgumentException("That username is already taken.");
        return UserView.from(users.save(new AppUser(name, encoder.encode(r.password()), r.role())));
    }

    @Transactional
    public UserView update(Long id, UserUpdateRequest r, String currentUsername) {
        AppUser u = users.findById(id).orElseThrow(() -> new IllegalArgumentException("User not found."));
        boolean self = u.getUsername().equalsIgnoreCase(currentUsername);
        Role newRole = r.role() != null ? r.role() : u.getRole();
        boolean newEnabled = r.enabled() != null ? r.enabled() : u.isEnabled();
        boolean losesAdmin = u.getRole() == Role.ADMIN && u.isEnabled() && (newRole != Role.ADMIN || !newEnabled);
        if (self && losesAdmin) throw new IllegalArgumentException("You cannot remove your own admin access or disable your own account.");
        if (losesAdmin && users.countByRoleAndEnabledTrue(Role.ADMIN) <= 1) {
            throw new IllegalArgumentException("There must always be at least one active Inventory Admin.");
        }
        u.setRole(newRole);
        u.setEnabled(newEnabled);
        if (r.password() != null && !r.password().isBlank()) u.setPasswordHash(encoder.encode(r.password()));
        return UserView.from(u);
    }

    @Transactional
    public void delete(Long id, String currentUsername) {
        AppUser u = users.findById(id).orElseThrow(() -> new IllegalArgumentException("User not found."));
        if (u.getUsername().equalsIgnoreCase(currentUsername)) throw new IllegalArgumentException("You cannot delete your own account.");
        if (u.getRole() == Role.ADMIN && u.isEnabled() && users.countByRoleAndEnabledTrue(Role.ADMIN) <= 1) {
            throw new IllegalArgumentException("There must always be at least one active Inventory Admin.");
        }
        users.delete(u);
    }
}
