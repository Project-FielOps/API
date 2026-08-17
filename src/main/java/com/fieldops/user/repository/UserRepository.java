package com.fieldops.user.repository;

import com.fieldops.user.domain.Role;
import com.fieldops.user.domain.User;
import com.fieldops.user.domain.UserStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {
    Optional<User> findByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCase(String email);

    Page<User> findByRoleAndStatus(Role role, UserStatus status, Pageable pageable);

    Page<User> findByRole(Role role, Pageable pageable);

    Page<User> findByStatus(UserStatus status, Pageable pageable);
}
