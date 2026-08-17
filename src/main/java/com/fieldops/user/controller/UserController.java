package com.fieldops.user.controller;

import com.fieldops.shared.dto.PageResponse;
import com.fieldops.user.application.UserService;
import com.fieldops.user.domain.Role;
import com.fieldops.user.domain.User;
import com.fieldops.user.domain.UserStatus;
import com.fieldops.user.dto.CreateUserRequest;
import com.fieldops.user.dto.UpdateUserRequest;
import com.fieldops.user.dto.UpdateUserStatusRequest;
import com.fieldops.user.dto.UserResponse;
import com.fieldops.user.mapper.UserMapper;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.UUID;

/** Only ADMIN manages the user directory, per spec section 10.6 and the admin-web endpoint permissions. */
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class UserController {

    private final UserService userService;
    private final UserMapper userMapper;

    @PostMapping
    public ResponseEntity<UserResponse> create(@Valid @RequestBody CreateUserRequest request) {
        User created = userService.create(request);
        UserResponse body = userMapper.toResponse(created);
        return ResponseEntity.created(URI.create("/api/v1/users/" + created.getId())).body(body);
    }

    @GetMapping
    public PageResponse<UserResponse> list(@RequestParam(required = false) Role role,
                                            @RequestParam(required = false) UserStatus status,
                                            Pageable pageable) {
        return PageResponse.of(userService.list(role, status, pageable).map(userMapper::toResponse));
    }

    @GetMapping("/{id}")
    public UserResponse get(@PathVariable UUID id) {
        return userMapper.toResponse(userService.getById(id));
    }

    @PutMapping("/{id}")
    public UserResponse update(@PathVariable UUID id, @Valid @RequestBody UpdateUserRequest request) {
        return userMapper.toResponse(userService.update(id, request));
    }

    @PatchMapping("/{id}/status")
    public UserResponse updateStatus(@PathVariable UUID id, @Valid @RequestBody UpdateUserStatusRequest request) {
        return userMapper.toResponse(userService.updateStatus(id, request.status()));
    }
}
