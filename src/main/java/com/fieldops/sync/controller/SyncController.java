package com.fieldops.sync.controller;

import com.fieldops.auth.security.FieldOpsUserDetails;
import com.fieldops.sync.application.SyncService;
import com.fieldops.sync.dto.SyncPullResponse;
import com.fieldops.sync.dto.SyncPushRequest;
import com.fieldops.sync.dto.SyncPushResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/mobile/sync")
@RequiredArgsConstructor
@PreAuthorize("hasRole('TECHNICIAN')")
public class SyncController {
    private final SyncService syncService;

    @PostMapping("/push")
    public SyncPushResponse push(@Valid @RequestBody SyncPushRequest request,
                                 @AuthenticationPrincipal FieldOpsUserDetails principal,
                                 HttpServletRequest httpRequest) {
        return syncService.push(request, principal.getUser(), httpRequest.getContentLengthLong());
    }

    @GetMapping("/pull")
    public SyncPullResponse pull(@RequestParam(required = false) String cursor,
                                 @RequestParam(defaultValue = "100") int limit,
                                 @AuthenticationPrincipal FieldOpsUserDetails principal) {
        return syncService.pull(cursor, limit, principal.getUser());
    }
}
