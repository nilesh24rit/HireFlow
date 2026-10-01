package com.hireflow.auth.service;

import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.hireflow.auth.dto.CreateUserRequest;
import com.hireflow.auth.dto.UpdateUserRequest;
import com.hireflow.auth.dto.UserResponse;
import com.hireflow.auth.entity.User;
import com.hireflow.auth.exception.DuplicateUserException;
import com.hireflow.auth.exception.UserNotFoundException;
import com.hireflow.auth.repository.UserRepository;

@Service
public class UserService {

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional
    public UserResponse createUser(CreateUserRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw duplicateEmail(request.email());
        }
        User user = new User();
        user.setEmail(request.email());
        user.setFirstName(request.firstName());
        user.setLastName(request.lastName());
        user.setRole(request.role());
        try {
            return toResponse(userRepository.saveAndFlush(user));
        } catch (DataIntegrityViolationException ex) {
            throw duplicateEmail(request.email());
        }
    }

    @Transactional(readOnly = true)
    public UserResponse getUserById(UUID id) {
        return toResponse(findUser(id));
    }

    @Transactional(readOnly = true)
    public UserResponse getUserByEmail(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UserNotFoundException("No user found with email '" + email + "'"));
        return toResponse(user);
    }

    @Transactional
    public UserResponse updateUser(UUID id, UpdateUserRequest request) {
        User user = findUser(id);
        user.setFirstName(request.firstName());
        user.setLastName(request.lastName());
        return toResponse(userRepository.saveAndFlush(user));
    }

    @Transactional
    public void deleteUser(UUID id) {
        User user = findUser(id);
        userRepository.delete(user);
    }

    private User findUser(UUID id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new UserNotFoundException("No user found with id '" + id + "'"));
    }

    private DuplicateUserException duplicateEmail(String email) {
        return new DuplicateUserException("A user with email '" + email + "' already exists");
    }

    private UserResponse toResponse(User user) {
        return new UserResponse(
                user.getId(),
                user.getEmail(),
                user.getFirstName(),
                user.getLastName(),
                user.getRole(),
                user.isEnabled(),
                user.getCreatedAt(),
                user.getUpdatedAt());
    }
}
