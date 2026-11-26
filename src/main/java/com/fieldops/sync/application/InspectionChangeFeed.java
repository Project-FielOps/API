package com.fieldops.sync.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fieldops.inspection.domain.InspectionChangedEvent;
import com.fieldops.sync.domain.SyncChange;
import com.fieldops.sync.repository.SyncChangeRepository;
import com.fieldops.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Leva para o feed {@code /mobile/sync/pull} do técnico tudo o que muda numa inspeção dele —
 * inclusive o que acontece na web (atribuição, cancelamento, revisão). Roda de forma síncrona,
 * dentro da transação que alterou a inspeção: ou a mudança e a entrada do feed são gravadas
 * juntas, ou nenhuma das duas.
 */
@Component
@RequiredArgsConstructor
public class InspectionChangeFeed {

    private final SyncChangeRepository changeRepository;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;

    @EventListener
    public void on(InspectionChangedEvent event) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("event", event.event());
        payload.put("status", event.status());
        SyncChange change = new SyncChange();
        change.setUser(userRepository.getReferenceById(event.technicianId()));
        change.setEntityType("Inspection");
        change.setEntityId(event.inspectionId());
        try {
            change.setPayload(objectMapper.writeValueAsString(payload));
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Could not serialize inspection change", ex);
        }
        changeRepository.save(change);
    }
}
