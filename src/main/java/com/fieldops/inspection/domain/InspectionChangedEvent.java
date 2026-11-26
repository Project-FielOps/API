package com.fieldops.inspection.domain;

import java.util.UUID;

/**
 * Publicado (dentro da mesma transação) sempre que uma inspeção muda de estado ou de técnico.
 * O módulo de sincronização transforma o evento em uma entrada do feed {@code /mobile/sync/pull}
 * do técnico afetado, para que o aparelho descubra atribuições, cancelamentos e revisões.
 *
 * @param technicianId técnico que deve receber a alteração no feed
 * @param event        ASSIGNED, UNASSIGNED, CANCELED, STARTED, SUBMITTED, REOPENED_FOR_CORRECTION,
 *                     UNDER_REVIEW, APPROVED ou REJECTED
 */
public record InspectionChangedEvent(UUID inspectionId, UUID technicianId, InspectionStatus status, String event) {
}
