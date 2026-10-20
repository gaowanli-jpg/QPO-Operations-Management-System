-- Track the item status that existed before a row-level discard action.
-- This lets RFQ order detail restore one part without rolling back the order header.

ALTER TABLE item_quote_version
    ADD COLUMN IF NOT EXISTS previous_status_before_discard VARCHAR(50);

COMMENT ON COLUMN item_quote_version.previous_status_before_discard IS '零件废弃前状态，用于询价订单详情零件级恢复';
