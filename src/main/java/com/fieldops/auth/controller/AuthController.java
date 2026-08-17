package com.fieldops.auth.controller;

import com.fieldops.auth.application.AuthService;
import com.fieldops.auth.dto.LoginRequest;
import com.fieldops.auth.dto.RefreshRequest;
import com.fieldops.auth.dto.TokenResponse;
import com.fieldops.auth.security.FieldOpsUserDetails;
import com.fieldops.user.dto.UserResponse;
import com.fieldops.user.mapper.UserMapper;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final UserMapper userMapper;

    @PostMapping("/login")
    public TokenResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request.email(), request.password());
    }

    @PostMapping("/refresh")
    public TokenResponse refresh(@Valid @RequestBody RefreshRequest request) {
        return authService.refresh(request.refreshToken());
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout() {
        // Stateless JWT: the client discards its tokens. No server-side session to invalidate.
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    public UserResponse me(@AuthenticationPrincipal FieldOpsUserDetails principal) {
        return userMapper.toResponse(principal.getUser());
    }
}
