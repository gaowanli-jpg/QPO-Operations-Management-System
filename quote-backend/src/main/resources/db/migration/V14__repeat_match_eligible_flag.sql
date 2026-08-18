ALTER TABLE item_quote_version
    ADD COLUMN IF NOT EXISTS repeat_match_eligible boolean DEFAULT false NOT NULL;

UPDATE item_quote_version item
SET repeat_match_eligible = TRUE
WHERE item.repeat_match_eligible IS FALSE
  AND (
      item.status = '已提单'
      OR EXISTS (
          SELECT 1
          FROM order_delivery_ticket ticket
          WHERE ticket.rfq_id = item.rfq_id
            AND ticket.item_id = item.id
      )
  );

DROP INDEX IF EXISTS idx_item_quote_version_repeat_released_key;

CREATE INDEX IF NOT EXISTS idx_item_quote_version_repeat_eligible_key
    ON item_quote_version USING btree (part_no, version, id DESC)
    WHERE repeat_match_eligible IS TRUE
      AND tax_included_unit_price IS NOT NULL
      AND material_data IS NOT NULL
      AND material_data <> '{}'::jsonb
      AND process_route IS NOT NULL
      AND process_route <> '[]'::jsonb;
