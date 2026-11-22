package com.fieldops.user.controller;

import com.fieldops.shared.dto.PageResponse;
import com.fieldops.user.application.UserService;
import com.fieldops.user.domain.Role;
import com.fieldops.user.domain.UserStatus;
import com.fieldops.user.dto.UserResponse;
import com.fieldops.user.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestParam;

@RestController
@RequestMapping("/api/v1/users/assignable")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN', 'SUPERVISOR')")
public class AssignableUserController {

    private final UserService userService;
    private final UserMapper userMapper;

    @GetMapping
    public PageResponse<UserResponse> list(@RequestParam(defaultValue = "TECHNICIAN") Role role,
                                           @RequestParam(required = false) UserStatus status,
                                           Pageable pageable) {
        if (role != Role.TECHNICIAN && role != Role.SUPERVISOR) {
            throw new IllegalArgumentException("Only technicians and supervisors can be assigned");
        }
        return PageResponse.of(userService.listAssignable(role, status, pageable).map(userMapper::toResponse));
    }
}
