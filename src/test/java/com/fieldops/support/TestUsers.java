package com.fieldops.support;

import com.fieldops.user.domain.Role;
import com.fieldops.user.domain.User;
import com.fieldops.user.domain.UserStatus;
import com.fieldops.user.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/** Bootstraps users directly through the repository, since only ADMIN can call POST /users and the test profile has no seed data. */
@Component
public class TestUsers {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    public static final String RAW_PASSWORD = "Password@123";

    public User create(String email, Role role) {
        User user = new User();
        user.setName(role.name() + " Test User");
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(RAW_PASSWORD));
        user.setRole(role);
        user.setStatus(UserStatus.ACTIVE);
        return userRepository.save(user);
    }

    public User createInactive(String email, Role role) {
        User user = create(email, role);
        user.setStatus(UserStatus.INACTIVE);
        return userRepository.save(user);
    }
}
