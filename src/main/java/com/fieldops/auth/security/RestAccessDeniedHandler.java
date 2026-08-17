package com.fieldops.auth.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fieldops.shared.dto.ApiError;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/** Per spec 12.3: an authenticated user without permission gets 403 with the standard error body. */
@Component
@RequiredArgsConstructor
public class RestAccessDeniedHandler implements AccessDeniedHandler {

    private final ObjectMapper objectMapper;

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException accessDeniedException)
            throws IOException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        ApiError body = ApiError.of(HttpStatus.FORBIDDEN.value(), "Forbidden", "ACCESS_DENIED",
                "You do not have permission to perform this action", request.getRequestURI());
        objectMapper.writeValue(response.getWriter(), body);
    }
}
