ALTER TABLE wms_reservation_ticket
    ADD COLUMN allocation_id INTEGER REFERENCES order_delivery_ticket_allocation(id);

ALTER TABLE wms_reservation_ticket
    DROP CONSTRAINT uk_wms_reservation_ticket_ticket;

CREATE UNIQUE INDEX uk_wms_reservation_ticket_legacy_ticket
    ON wms_reservation_ticket(batch_id, ticket_id)
    WHERE allocation_id IS NULL;

CREATE UNIQUE INDEX uk_wms_reservation_ticket_allocation
    ON wms_reservation_ticket(batch_id, allocation_id)
    WHERE allocation_id IS NOT NULL;

DROP INDEX uk_wms_reservation_active_ticket;

CREATE UNIQUE INDEX uk_wms_reservation_active_ticket
    ON wms_reservation_ticket(ticket_id)
    WHERE allocation_id IS NULL
      AND status IN ('RESERVED', 'SUBMITTING', 'RESULT_UNKNOWN', 'RESPONSE_RECEIVED', 'APPLY_FAILED');

CREATE INDEX idx_wms_reservation_ticket_allocation
    ON wms_reservation_ticket(allocation_id, id)
    WHERE allocation_id IS NOT NULL;

CREATE UNIQUE INDEX uk_wms_reservation_active_allocation
    ON wms_reservation_ticket(allocation_id)
    WHERE allocation_id IS NOT NULL
      AND status IN ('RESERVED', 'SUBMITTING', 'RESULT_UNKNOWN', 'RESPONSE_RECEIVED', 'APPLY_FAILED');
