package com.fieldops.client.mapper;

import com.fieldops.client.domain.Client;
import com.fieldops.client.dto.ClientResponse;
import org.springframework.stereotype.Component;

@Component
public class ClientMapper {
    public ClientResponse toResponse(Client c) {
        return new ClientResponse(c.getId(), c.getName(), c.getLegalName(), c.getDocument(), c.getEmail(),
                c.getPhone(), c.getStatus(), c.getCreatedAt(), c.getUpdatedAt(), c.getVersion());
    }
}
