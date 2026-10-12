package com.hireflow.auth.service;

import java.util.UUID;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.hireflow.auth.dto.CreateUserRequest;
import com.hireflow.auth.dto.UpdateUserRequest;
import com.hireflow.auth.dto.UpdateUserRoleRequest;
import com.hireflow.auth.dto.UserResponse;
import com.hireflow.auth.entity.User;
import com.hireflow.auth.entity.UserRole;
import com.hireflow.auth.exception.DuplicateResourceException;
import com.hireflow.auth.exception.ResourceNotFoundException;
import com.hireflow.auth.repository.UserRepository;

/**
 * User management for auth-service, with the HireFlow authorization rules enforced
 * next to the operations they protect ({@code @PreAuthorize} on the service methods,
 * not scattered across controllers).
 *
 * <p>The permission matrix for {@code /api/users}:</p>
 *
 * <ul>
 *   <li><b>Create</b> — any authenticated caller. Registration semantics: the payload
 *       carries no role at all ({@link CreateUserRequest} has no such field), so every
 *       account is born with the fixed default {@link UserRole#CANDIDATE}. There is no
 *       privilege to escalate on this path because there is no input that could carry
 *       one; granting anything else happens only through {@link #assignUserRole}.</li>
 *   <li><b>Read / update by id</b> — an administrator, or the owner of the record: the
 *       authenticated principal (the user id the JWT was issued for) must equal the path
 *       id. The comparison is expressed as {@code #id.toString() == authentication.name}
 *       because the bearer filter authenticates with the user-id string as principal and
 *       {@code getName()} resolves to exactly that string.</li>
 *   <li><b>Read by email</b> — administrator only. A directory lookup by email cannot be
 *       ownership-checked against a principal that carries an id, and it would otherwise
 *       become an account-enumeration oracle for any authenticated caller.</li>
 *   <li><b>Delete</b> — administrator only.</li>
 *   <li><b>Role assignment</b> — administrator only, through {@link #assignUserRole} and
 *       its dedicated {@link UpdateUserRoleRequest} payload. This is the single code path
 *       in the whole service that writes {@code User.role}; create, profile update and
 *       Google sign-up cannot set it.</li>
 * </ul>
 *
 * <p><b>Trusted roles, bounded staleness.</b> The rules evaluate the authority the token
 * carried when it was signed, and that authority always originates from persisted data
 * (login reads {@code User.role} from the database at token issuance). A role change is
 * persisted immediately and appears in every token issued from that moment on; tokens
 * already in flight keep the role they were signed with until they expire. The token TTL
 * is therefore the invalidation window — there is no session or revocation store to
 * update, and no refresh tokens that could outlive it.</p>
 */
@Service
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * Creates a user account with the fixed default role.
     *
     * <p>Authorization here is authentication-only (the filter chain requires a bearer
     * token for {@code POST /api/users}); the escalation guard is structural: the request
     * DTO has no role field, so none can be smuggled in, and {@link UserRole#CANDIDATE}
     * is assigned by this method rather than by anything the caller sends. Even an
     * administrator creating a user gets a CANDIDATE account — roles are granted only
     * through {@link #assignUserRole}.</p>
     *
     * @param request validated registration payload without a role
     * @return the created user with its default role
     */
    @Transactional
    public UserResponse createUser(CreateUserRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw duplicateEmail(request.email());
        }
        User user = new User();
        user.setEmail(request.email());
        user.setFirstName(request.firstName());
        user.setLastName(request.lastName());
        // Only the BCrypt hash is persisted; the raw password is discarded immediately
        // and is never included in the response mapping below.
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        // Registration default: role is assigned by the server, never taken from input.
        user.setRole(UserRole.CANDIDATE);
        return toResponse(userRepository.saveAndFlush(user));
    }

    /**
     * Returns a user record to an administrator or to the user it belongs to.
     *
     * @param id id of the requested user
     * @return the requested user
     */
    @Transactional(readOnly = true)
    @PreAuthorize("hasRole('ADMIN') or #id.toString() == authentication.name")
    public UserResponse getUserById(UUID id) {
        return toResponse(findUser(id));
    }

    /**
     * Returns a user record by email; administrator only.
     *
     * @param email email address to look up
     * @return the user registered with that address
     */
    @Transactional(readOnly = true)
    @PreAuthorize("hasRole('ADMIN')")
    public UserResponse getUserByEmail(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("No user found with email '" + email + "'"));
        return toResponse(user);
    }

    /**
     * Updates the profile of a user; administrator or the user it belongs to.
     *
     * <p>{@link UpdateUserRequest} carries first and last name only — email, role and
     * enabled are not accepted here, so an owner editing their own record can never
     * change their authority.</p>
     *
     * @param id      id of the user to update
     * @param request validated profile payload
     * @return the updated user
     */
    @Transactional
    @PreAuthorize("hasRole('ADMIN') or #id.toString() == authentication.name")
    public UserResponse updateUser(UUID id, UpdateUserRequest request) {
        User user = findUser(id);
        user.setFirstName(request.firstName());
        user.setLastName(request.lastName());
        return toResponse(userRepository.saveAndFlush(user));
    }

    /**
     * Deletes a user; administrator only.
     *
     * @param id id of the user to delete
     */
    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public void deleteUser(UUID id) {
        User user = findUser(id);
        userRepository.delete(user);
    }

    /**
     * Assigns a role to a user; administrator only.
     *
     * <p>This is the only role-writing path in the service. The change is persisted
     * immediately: subsequent logins mint tokens carrying the new role, while tokens
     * issued earlier keep the old one until they expire (the token TTL is the
     * invalidation window).</p>
     *
     * @param id      id of the user whose role changes
     * @param request validated role payload
     * @return the user with the newly assigned role
     */
    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public UserResponse assignUserRole(UUID id, UpdateUserRoleRequest request) {
        User user = findUser(id);
        user.setRole(request.role());
        return toResponse(userRepository.saveAndFlush(user));
    }

    private User findUser(UUID id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("No user found with id '" + id + "'"));
    }

    private DuplicateResourceException duplicateEmail(String email) {
        return new DuplicateResourceException("A user with email '" + email + "' already exists");
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
