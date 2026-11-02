-- 销售订单废弃撤销 MES 需求任务时，台账行要留撤销结果。
-- V6 的 chk_mes_production_batch_item_status 只允许
-- RESERVED / SUBMITTING / SUCCESS / FAILED / RESULT_UNKNOWN / SKIPPED，
-- 写 CANCELLED / CANCEL_FAILED 会直接违反约束，导致留痕失败（异常被吞，只落 warn）。
-- 这里重建约束，新值是旧集合的超集，不影响既有数据。

ALTER TABLE mes_production_batch_item
    DROP CONSTRAINT IF EXISTS chk_mes_production_batch_item_status;

ALTER TABLE mes_production_batch_item
    ADD CONSTRAINT chk_mes_production_batch_item_status CHECK (status IN (
        'RESERVED',
        'SUBMITTING',
        'SUCCESS',
        'FAILED',
        'RESULT_UNKNOWN',
        'SKIPPED',
        'CANCELLED',
        'CANCEL_FAILED'
    ));
