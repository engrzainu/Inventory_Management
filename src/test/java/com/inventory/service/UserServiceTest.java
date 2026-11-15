package com.inventory.service;

import com.inventory.dto.UserCreateRequest;
import com.inventory.dto.UserUpdateRequest;
import com.inventory.model.AppUser;
import com.inventory.model.Role;
import com.inventory.repository.AppUserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class UserServiceTest {
    AppUserRepository users = mock(AppUserRepository.class);
    BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
    UserService service = new UserService(users, encoder);

    private AppUser user(long id, String name, Role role) {
        AppUser u = new AppUser(name, encoder.encode("password1"), role);
        ReflectionTestUtils.setField(u, "id", id);
        when(users.findById(id)).thenReturn(Optional.of(u));
        return u;
    }

    @Test
    void createsAccountantWithHashedPassword() {
        when(users.existsByUsernameIgnoreCase("ada")).thenReturn(false);
        when(users.save(any(AppUser.class))).thenAnswer(i -> i.getArgument(0));
        var view = service.create(new UserCreateRequest("ada", "secret-pass", Role.ACCOUNTANT));
        assertEquals(Role.ACCOUNTANT, view.role());
        verify(users).save(argThat(u -> !u.getPasswordHash().equals("secret-pass") && encoder.matches("secret-pass", u.getPasswordHash())));
    }

    @Test
    void duplicateUsernameIsRejected() {
        when(users.existsByUsernameIgnoreCase("ada")).thenReturn(true);
        assertThrows(IllegalArgumentException.class, () -> service.create(new UserCreateRequest("ada", "secret-pass", Role.ADMIN)));
    }

    @Test
    void adminCannotDemoteOrDisableThemselves() {
        user(1, "boss", Role.ADMIN);
        when(users.countByRoleAndEnabledTrue(Role.ADMIN)).thenReturn(2L);
        assertThrows(IllegalArgumentException.class, () -> service.update(1L, new UserUpdateRequest(Role.ACCOUNTANT, null, null), "boss"));
        assertThrows(IllegalArgumentException.class, () -> service.update(1L, new UserUpdateRequest(null, false, null), "BOSS"));
        assertThrows(IllegalArgumentException.class, () -> service.delete(1L, "boss"));
    }

    @Test
    void lastActiveAdminCannotBeRemoved() {
        user(2, "other", Role.ADMIN);
        when(users.countByRoleAndEnabledTrue(Role.ADMIN)).thenReturn(1L);
        assertThrows(IllegalArgumentException.class, () -> service.update(2L, new UserUpdateRequest(Role.ACCOUNTANT, null, null), "boss"));
        assertThrows(IllegalArgumentException.class, () -> service.delete(2L, "boss"));
    }

    @Test
    void roleAndPasswordCanBeChangedForAnotherUser() {
        AppUser u = user(3, "ada", Role.ACCOUNTANT);
        service.update(3L, new UserUpdateRequest(Role.ADMIN, null, "new-password"), "boss");
        assertEquals(Role.ADMIN, u.getRole());
        assertTrue(encoder.matches("new-password", u.getPasswordHash()));
    }
}
