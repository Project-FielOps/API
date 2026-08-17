package com.fieldops.auth.application;

import com.fieldops.auth.dto.TokenResponse;
import com.fieldops.auth.security.FieldOpsUserDetails;
import com.fieldops.auth.security.JwtProperties;
import com.fieldops.auth.security.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final JwtProperties jwtProperties;
    private final UserDetailsService userDetailsService;

    public TokenResponse login(String email, String password) {
        try {
            var authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(email, password));
            FieldOpsUserDetails principal = (FieldOpsUserDetails) authentication.getPrincipal();
            return issueTokens(principal);
        } catch (DisabledException | LockedException e) {
            throw new BadCredentialsException("Account is not active");
        }
    }

    public TokenResponse refresh(String refreshToken) {
        if (!jwtService.isValid(refreshToken) || !jwtService.isRefreshToken(refreshToken)) {
            throw new BadCredentialsException("Invalid or expired refresh token");
        }
        String email = jwtService.extractEmail(refreshToken);
        FieldOpsUserDetails principal = (FieldOpsUserDetails) userDetailsService.loadUserByUsername(email);
        if (!principal.isEnabled() || !principal.isAccountNonLocked()) {
            throw new BadCredentialsException("Account is not active");
        }
        return issueTokens(principal);
    }

    private TokenResponse issueTokens(FieldOpsUserDetails principal) {
        String access = jwtService.generateAccessToken(principal);
        String refresh = jwtService.generateRefreshToken(principal);
        return TokenResponse.of(access, refresh, jwtProperties.accessTokenTtlMinutes());
    }
}
