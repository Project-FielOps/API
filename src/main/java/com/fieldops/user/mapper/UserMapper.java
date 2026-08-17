package com.fieldops.user.mapper;

import com.fieldops.user.domain.User;
import com.fieldops.user.dto.UserResponse;
import org.springframework.stereotype.Component;

@Component
public class UserMapper {
    public UserResponse toResponse(User u) {
        return new UserResponse(u.getId(), u.getName(), u.getEmail(), u.getRole(), u.getStatus(),
                u.getPhone(), u.getCreatedAt(), u.getUpdatedAt(), u.getVersion());
    }
}
