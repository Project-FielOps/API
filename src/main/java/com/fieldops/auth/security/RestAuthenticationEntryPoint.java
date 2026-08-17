package com.fieldops.auth.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fieldops.shared.dto.ApiError;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Per spec 12.3: a request with no/invalid credentials must get 401, not Spring Security's
 * default 403. Without this, {@code anyRequest().authenticated()} falls back to
 * Http403ForbiddenEntryPoint for unauthenticated requests.
 */
@Component
@RequiredArgsConstructor
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException authException)
            throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        ApiError body = ApiError.of(HttpStatus.UNAUTHORIZED.value(), "Unauthorized", "AUTHENTICATION_REQUIRED",
                "Authentication is required to access this resource", request.getRequestURI());
        objectMapper.writeValue(response.getWriter(), body);
    }
}
