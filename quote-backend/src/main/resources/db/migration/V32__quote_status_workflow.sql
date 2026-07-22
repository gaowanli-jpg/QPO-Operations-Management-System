UPDATE order_rfq
SET status = '未核价'
WHERE status = '待工程部评估';

UPDATE item_quote_version
SET status = '未核价'
WHERE status = '待核价';
