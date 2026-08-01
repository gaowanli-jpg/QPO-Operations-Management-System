UPDATE system_permission
SET permission_name = '询价订单'
WHERE permission_code = 'market:order:view';

UPDATE system_permission
SET permission_name = '询价订单选择客户'
WHERE permission_code = 'market:order:customer-select';

UPDATE system_permission
SET permission_name = '询价订单预览文件'
WHERE permission_code = 'market:order:file-preview';
