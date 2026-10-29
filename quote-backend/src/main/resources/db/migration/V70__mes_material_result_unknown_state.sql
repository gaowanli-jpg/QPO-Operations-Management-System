-- Preserve uncertainty after a MES material request has been written.
-- A response parse failure, read timeout, or identity mismatch may mean MES
-- already created the material. Such requests must remain active and be
-- reconciled/replayed with the original request identity.

SET search_path TO "${flyway:defaultSchema}";

ALTER TABLE order_delivery_ticket_mes_request
    DROP CONSTRAINT IF EXISTS chk_delivery_mes_request_status;

ALTER TABLE order_delivery_ticket_mes_request
    ADD CONSTRAINT chk_delivery_mes_request_status CHECK (
        request_status IN (
            'PROCESSING', 'CALLBACK_PENDING', 'SUCCESS', 'FAILED',
            'RESULT_UNKNOWN', 'STALE', 'IGNORED'
        )
    );

DROP INDEX IF EXISTS uk_delivery_mes_request_active_dedup;

CREATE UNIQUE INDEX uk_delivery_mes_request_active_dedup
    ON order_delivery_ticket_mes_request(dedup_key)
    WHERE request_status IN ('PROCESSING', 'CALLBACK_PENDING', 'RESULT_UNKNOWN')
      AND dedup_key IS NOT NULL
      AND BTRIM(dedup_key) <> '';
