package com.fieldops.client.controller;

import com.fieldops.client.application.ClientService;
import com.fieldops.client.domain.Client;
import com.fieldops.client.domain.ClientStatus;
import com.fieldops.client.dto.ClientResponse;
import com.fieldops.client.dto.ClientUpsertRequest;
import com.fieldops.client.dto.UpdateStatusRequest;
import com.fieldops.client.mapper.ClientMapper;
import com.fieldops.shared.dto.PageResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.UUID;

/** Clients are managed by ADMIN/SUPERVISOR; every other authenticated role can read for lookups. */
@RestController
@RequestMapping("/api/v1/clients")
@RequiredArgsConstructor
public class ClientController {

    private final ClientService clientService;
    private final ClientMapper clientMapper;

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','SUPERVISOR')")
    public ResponseEntity<ClientResponse> create(@Valid @RequestBody ClientUpsertRequest request) {
        Client created = clientService.create(request);
        return ResponseEntity.created(URI.create("/api/v1/clients/" + created.getId()))
                .body(clientMapper.toResponse(created));
    }

    @GetMapping
    public PageResponse<ClientResponse> list(@RequestParam(required = false) ClientStatus status,
                                              @RequestParam(required = false) String name,
                                              Pageable pageable) {
        return PageResponse.of(clientService.list(status, name, pageable).map(clientMapper::toResponse));
    }

    @GetMapping("/{id}")
    public ClientResponse get(@PathVariable UUID id) {
        return clientMapper.toResponse(clientService.getById(id));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','SUPERVISOR')")
    public ClientResponse update(@PathVariable UUID id, @Valid @RequestBody ClientUpsertRequest request) {
        return clientMapper.toResponse(clientService.update(id, request));
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('ADMIN','SUPERVISOR')")
    public ClientResponse updateStatus(@PathVariable UUID id, @Valid @RequestBody UpdateStatusRequest request) {
        return clientMapper.toResponse(clientService.updateStatus(id, request.status()));
    }
}
