package com.fieldops.user.application;

import com.fieldops.shared.exception.ConflictException;
import com.fieldops.shared.exception.NotFoundException;
import com.fieldops.user.domain.Role;
import com.fieldops.user.domain.User;
import com.fieldops.user.domain.UserStatus;
import com.fieldops.user.dto.CreateUserRequest;
import com.fieldops.user.dto.UpdateUserRequest;
import com.fieldops.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public User create(CreateUserRequest request) {
        if (userRepository.existsByEmailIgnoreCase(request.email())) {
            throw new ConflictException("A user with email '" + request.email() + "' already exists");
        }
        User user = new User();
        user.setName(request.name());
        user.setEmail(request.email().toLowerCase());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setRole(request.role());
        user.setPhone(request.phone());
        user.setStatus(UserStatus.ACTIVE);
        return userRepository.save(user);
    }

    @Transactional(readOnly = true)
    public User getById(UUID id) {
        return userRepository.findById(id).orElseThrow(() -> NotFoundException.of("User", id));
    }

    @Transactional(readOnly = true)
    public Page<User> list(Role role, UserStatus status, Pageable pageable) {
        if (role != null && status != null) {
            return userRepository.findByRoleAndStatus(role, status, pageable);
        } else if (role != null) {
            return userRepository.findByRole(role, pageable);
        } else if (status != null) {
            return userRepository.findByStatus(status, pageable);
        }
        return userRepository.findAll(pageable);
    }

    public User update(UUID id, UpdateUserRequest request) {
        User user = getById(id);
        user.setName(request.name());
        user.setRole(request.role());
        user.setPhone(request.phone());
        return userRepository.save(user);
    }

    public User updateStatus(UUID id, UserStatus status) {
        User user = getById(id);
        user.setStatus(status);
        return userRepository.save(user);
    }
}
