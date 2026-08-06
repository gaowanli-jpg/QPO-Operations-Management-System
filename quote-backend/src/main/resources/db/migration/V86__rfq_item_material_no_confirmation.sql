ALTER TABLE item_quote_version
    ADD COLUMN IF NOT EXISTS material_no VARCHAR(120),
    ADD COLUMN IF NOT EXISTS material_no_status VARCHAR(30) NOT NULL DEFAULT '未确认',
    ADD COLUMN IF NOT EXISTS material_no_source VARCHAR(30),
    ADD COLUMN IF NOT EXISTS material_no_source_ticket_id BIGINT,
    ADD COLUMN IF NOT EXISTS material_no_synced_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS material_no_message TEXT;

UPDATE item_quote_version
SET material_no_status = '未确认'
WHERE material_no_status IS NULL OR BTRIM(material_no_status) = '';

CREATE INDEX IF NOT EXISTS idx_item_quote_version_material_status
    ON item_quote_version(material_no_status, rfq_id, id);

CREATE INDEX IF NOT EXISTS idx_item_quote_version_material_no_id
    ON item_quote_version(material_no, id);

CREATE INDEX IF NOT EXISTS idx_item_quote_version_material_source_ticket
    ON item_quote_version(material_no_source_ticket_id)
    WHERE material_no_source_ticket_id IS NOT NULL;

COMMENT ON COLUMN item_quote_version.material_no IS 'MES confirmed material number for RFQ item. Empty means no confirmed material number yet.';
COMMENT ON COLUMN item_quote_version.material_no_status IS 'Material number confirmation status for RFQ U9 readiness: 未确认, 已确认, 获取失败.';
COMMENT ON COLUMN item_quote_version.material_no_source IS 'Material number confirmation source, for example MES_TICKET after delivery ticket material sync.';
COMMENT ON COLUMN item_quote_version.material_no_source_ticket_id IS 'Source order_delivery_ticket.id that confirmed and wrote back the RFQ item material number.';
COMMENT ON COLUMN item_quote_version.material_no_synced_at IS 'Time when confirmed material number was written back to item_quote_version.';
COMMENT ON COLUMN item_quote_version.material_no_message IS 'Short material number confirmation summary; do not store full external responses.';
