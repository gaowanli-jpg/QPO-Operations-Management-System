UPDATE order_rfq
SET status = '待工程部评估'
WHERE status = '部分已报价';
