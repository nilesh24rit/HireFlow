package com.hireflow.auth.service;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.hireflow.auth.dto.CreateUserRequest;
import com.hireflow.auth.dto.UpdateUserRequest;
import com.hireflow.auth.dto.UserResponse;
import com.hireflow.auth.entity.User;
import com.hireflow.auth.entity.UserRole;
import com.hireflow.auth.exception.DuplicateResourceException;
import com.hireflow.auth.exception.ResourceNotFoundException;
import com.hireflow.auth.repository.UserRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    private static final String RAW_PASSWORD = "Sup3r-Secret!";

    @Mock
    private UserRepository userRepository;

    private PasswordEncoder passwordEncoder;
    private UserService userService;

    @BeforeEach
    void setUp() {
        passwordEncoder = new BCryptPasswordEncoder();
        userService = new UserService(userRepository, passwordEncoder);
    }

    @Test
    void createsUserSuccessfully() {
        CreateUserRequest request = new CreateUserRequest(
                "jane@example.com", "Jane", "Doe", RAW_PASSWORD, UserRole.CANDIDATE);
        when(userRepository.existsByEmail("jane@example.com")).thenReturn(false);
        when(userRepository.saveAndFlush(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UserResponse response = userService.createUser(request);

        assertThat(response.email()).isEqualTo("jane@example.com");
        assertThat(response.firstName()).isEqualTo("Jane");
        assertThat(response.lastName()).isEqualTo("Doe");
        assertThat(response.role()).isEqualTo(UserRole.CANDIDATE);
        assertThat(response.enabled()).isTrue();
        verify(userRepository).existsByEmail("jane@example.com");
        verify(userRepository).saveAndFlush(any(User.class));
    }

    @Test
    void storesOnlyABcryptHashOfThePassword() {
        CreateUserRequest request = new CreateUserRequest(
                "jane@example.com", "Jane", "Doe", RAW_PASSWORD, UserRole.CANDIDATE);
        when(userRepository.existsByEmail("jane@example.com")).thenReturn(false);
        when(userRepository.saveAndFlush(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        userService.createUser(request);

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).saveAndFlush(saved.capture());
        String storedHash = saved.getValue().getPasswordHash();
        assertThat(storedHash).startsWith("$2").isNotEqualTo(RAW_PASSWORD).doesNotContain(RAW_PASSWORD);
        assertThat(passwordEncoder.matches(RAW_PASSWORD, storedHash)).isTrue();
    }

    @Test
    void rejectsDuplicateEmailBeforeSaving() {
        CreateUserRequest request = new CreateUserRequest(
                "jane@example.com", "Jane", "Doe", RAW_PASSWORD, UserRole.CANDIDATE);
        when(userRepository.existsByEmail("jane@example.com")).thenReturn(true);

        assertThatThrownBy(() -> userService.createUser(request))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessageContaining("jane@example.com");

        verify(userRepository, never()).saveAndFlush(any(User.class));
    }

    @Test
    void propagatesDatabaseConstraintViolationForGlobalHandling() {
        CreateUserRequest request = new CreateUserRequest(
                "jane@example.com", "Jane", "Doe", RAW_PASSWORD, UserRole.CANDIDATE);
        when(userRepository.existsByEmail("jane@example.com")).thenReturn(false);
        when(userRepository.saveAndFlush(any(User.class)))
                .thenThrow(new DataIntegrityViolationException("uk_users_email"));

        assertThatThrownBy(() -> userService.createUser(request))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void findsUserById() {
        UUID id = UUID.randomUUID();
        when(userRepository.findById(id)).thenReturn(Optional.of(user()));

        UserResponse response = userService.getUserById(id);

        assertThat(response.email()).isEqualTo("jane@example.com");
        assertThat(response.firstName()).isEqualTo("Jane");
        assertThat(response.role()).isEqualTo(UserRole.CANDIDATE);
    }

    @Test
    void throwsWhenUserNotFoundById() {
        UUID id = UUID.randomUUID();
        when(userRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.getUserById(id))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining(id.toString());
    }

    @Test
    void findsUserByEmail() {
        when(userRepository.findByEmail("jane@example.com")).thenReturn(Optional.of(user()));

        UserResponse response = userService.getUserByEmail("jane@example.com");

        assertThat(response.email()).isEqualTo("jane@example.com");
        assertThat(response.lastName()).isEqualTo("Doe");
        assertThat(response.role()).isEqualTo(UserRole.CANDIDATE);
    }

    @Test
    void throwsWhenUserNotFoundByEmail() {
        when(userRepository.findByEmail("missing@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.getUserByEmail("missing@example.com"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("missing@example.com");
    }

    @Test
    void updatesUserProfile() {
        UUID id = UUID.randomUUID();
        User user = user();
        when(userRepository.findById(id)).thenReturn(Optional.of(user));
        when(userRepository.saveAndFlush(user)).thenReturn(user);

        UserResponse response = userService.updateUser(id, new UpdateUserRequest("Janet", "Smith"));

        assertThat(response.firstName()).isEqualTo("Janet");
        assertThat(response.lastName()).isEqualTo("Smith");
        assertThat(response.email()).isEqualTo("jane@example.com");
        assertThat(user.getFirstName()).isEqualTo("Janet");
    }

    @Test
    void throwsWhenUpdatingMissingUser() {
        UUID id = UUID.randomUUID();
        when(userRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.updateUser(id, new UpdateUserRequest("Janet", "Smith")))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void deletesUser() {
        UUID id = UUID.randomUUID();
        User user = user();
        when(userRepository.findById(id)).thenReturn(Optional.of(user));

        userService.deleteUser(id);

        verify(userRepository).delete(user);
    }

    @Test
    void throwsWhenDeletingMissingUser() {
        UUID id = UUID.randomUUID();
        when(userRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.deleteUser(id)).isInstanceOf(ResourceNotFoundException.class);
        verify(userRepository, never()).delete(any(User.class));
    }

    private User user() {
        User user = new User();
        user.setEmail("jane@example.com");
        user.setFirstName("Jane");
        user.setLastName("Doe");
        user.setRole(UserRole.CANDIDATE);
        return user;
    }
}
