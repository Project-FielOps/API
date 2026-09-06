package com.fieldops.user.controller;

import com.fieldops.shared.dto.PageResponse;
import com.fieldops.user.application.UserService;
import com.fieldops.user.dto.UserResponse;
import com.fieldops.user.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users/assignable")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN', 'SUPERVISOR')")
public class AssignableUserController {

    private final UserService userService;
    private final UserMapper userMapper;

    @GetMapping
    public PageResponse<UserResponse> list(Pageable pageable) {
        return PageResponse.of(userService.listAssignableTechnicians(pageable).map(userMapper::toResponse));
    }
}
