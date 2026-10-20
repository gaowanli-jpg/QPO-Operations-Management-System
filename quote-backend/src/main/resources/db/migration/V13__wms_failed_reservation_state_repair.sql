-- Repair historical WMS batches that were marked FAILED before child rows were closed.

SET search_path TO "${flyway:defaultSchema}";

UPDATE wms_reservation_item i
SET status = 'FAILED',
    updated_at = CURRENT_TIMESTAMP
FROM wms_reservation_batch b
WHERE i.batch_id = b.id
  AND b.status = 'FAILED'
  AND i.status <> 'FAILED';

UPDATE wms_reservation_ticket t
SET status = 'FAILED',
    message = COALESCE(NULLIF(t.message, ''), NULLIF(b.response_msg, ''), 'WMS reservation failed'),
    updated_at = CURRENT_TIMESTAMP
FROM wms_reservation_batch b
WHERE t.batch_id = b.id
  AND b.status = 'FAILED'
  AND t.status <> 'FAILED';

UPDATE order_delivery_ticket odt
SET wms_allocate_status = 'FAILED',
    wms_allocate_message = COALESCE(
            NULLIF(odt.wms_allocate_message, ''),
            NULLIF(t.message, ''),
            NULLIF(b.response_msg, ''),
            'WMS reservation failed'
        )
FROM wms_reservation_ticket t
JOIN wms_reservation_batch b ON b.id = t.batch_id
WHERE odt.id = t.ticket_id
  AND b.status = 'FAILED'
  AND COALESCE(odt.wms_allocate_status, 'PENDING') <> 'SUCCESS';
