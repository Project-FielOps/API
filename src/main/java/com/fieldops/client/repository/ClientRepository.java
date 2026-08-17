package com.fieldops.client.repository;

import com.fieldops.client.domain.Client;
import com.fieldops.client.domain.ClientStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ClientRepository extends JpaRepository<Client, UUID> {
    Page<Client> findByStatus(ClientStatus status, Pageable pageable);

    Page<Client> findByNameContainingIgnoreCase(String name, Pageable pageable);
}
