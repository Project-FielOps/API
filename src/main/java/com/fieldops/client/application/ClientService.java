package com.fieldops.client.application;

import com.fieldops.client.domain.Client;
import com.fieldops.client.domain.ClientStatus;
import com.fieldops.client.dto.ClientUpsertRequest;
import com.fieldops.client.repository.ClientRepository;
import com.fieldops.shared.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class ClientService {

    private final ClientRepository clientRepository;

    public Client create(ClientUpsertRequest request) {
        Client client = new Client();
        apply(client, request);
        client.setStatus(ClientStatus.ACTIVE);
        return clientRepository.save(client);
    }

    @Transactional(readOnly = true)
    public Client getById(UUID id) {
        return clientRepository.findById(id).orElseThrow(() -> NotFoundException.of("Client", id));
    }

    @Transactional(readOnly = true)
    public Page<Client> list(ClientStatus status, String name, Pageable pageable) {
        if (name != null && !name.isBlank()) {
            return clientRepository.findByNameContainingIgnoreCase(name, pageable);
        }
        if (status != null) {
            return clientRepository.findByStatus(status, pageable);
        }
        return clientRepository.findAll(pageable);
    }

    public Client update(UUID id, ClientUpsertRequest request) {
        Client client = getById(id);
        apply(client, request);
        return clientRepository.save(client);
    }

    public Client updateStatus(UUID id, ClientStatus status) {
        Client client = getById(id);
        client.setStatus(status);
        return clientRepository.save(client);
    }

    private void apply(Client client, ClientUpsertRequest request) {
        client.setName(request.name());
        client.setLegalName(request.legalName());
        client.setDocument(request.document());
        client.setEmail(request.email());
        client.setPhone(request.phone());
    }
}
