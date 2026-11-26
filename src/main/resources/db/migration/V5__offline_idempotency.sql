-- Idempotência das escritas feitas offline pelo aplicativo mobile.
-- O operationId gerado no aparelho identifica cada criação: reenviar a mesma operação
-- devolve o registro já existente em vez de duplicar a NC ou a evidência.

ALTER TABLE non_conformity ADD COLUMN client_operation_id UUID;
CREATE UNIQUE INDEX uk_non_conformity_client_operation
    ON non_conformity (created_by, client_operation_id)
    WHERE client_operation_id IS NOT NULL;

ALTER TABLE evidence ADD COLUMN client_operation_id UUID;
CREATE UNIQUE INDEX uk_evidence_client_operation
    ON evidence (created_by, client_operation_id)
    WHERE client_operation_id IS NOT NULL;
