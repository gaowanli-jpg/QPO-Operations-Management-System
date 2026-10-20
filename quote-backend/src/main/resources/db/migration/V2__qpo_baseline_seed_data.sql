-- QPO compressed Flyway baseline seed data.
-- Retains system data, default material type, local fee master data, roles, permissions, and interface metadata only.
-- Concrete MES/U9-sourced material, hourly process, customer, order, cache, audit, uploaded file, token, and external secret rows are intentionally not seeded.

--
-- PostgreSQL database dump
--


-- Dumped from database version 15.18 (Debian 15.18-1.pgdg13+1)
-- Dumped by pg_dump version 15.18 (Debian 15.18-1.pgdg13+1)

SET statement_timeout = 0;
SET lock_timeout = 0;
SET idle_in_transaction_session_timeout = 0;
SET client_encoding = 'UTF8';
SET standard_conforming_strings = on;
SET search_path TO "${flyway:defaultSchema}";
SET check_function_bodies = false;
SET xmloption = content;
SET client_min_messages = warning;
SET row_security = off;

--
-- Data for Name: audit_log_retention_config; Type: TABLE DATA; Schema: public; Owner: -
--

INSERT INTO audit_log_retention_config VALUES (1, false, 180, true, '02:00', NULL, 0, 'NEVER', NULL, NULL, '2026-08-10 10:07:59.996993');

--
-- Data for Name: dict_assembly_markup_fee; Type: TABLE DATA; Schema: public; Owner: -
--

INSERT INTO dict_assembly_markup_fee VALUES (1, '管理费', 5.00, 2, true);
INSERT INTO dict_assembly_markup_fee VALUES (2, '利润', 15.00, 3, true);
INSERT INTO dict_assembly_markup_fee VALUES (3, '税费', 13.00, 4, true);

--
-- Data for Name: dict_extra_fee_item; Type: TABLE DATA; Schema: public; Owner: -
--

INSERT INTO dict_extra_fee_item VALUES (6, 'EDM', 1, true, '');
INSERT INTO dict_extra_fee_item VALUES (7, '热处理', 2, true, '');
INSERT INTO dict_extra_fee_item VALUES (8, '表面处理', 3, true, '');
INSERT INTO dict_extra_fee_item VALUES (9, 'QA', 4, true, '');
INSERT INTO dict_extra_fee_item VALUES (10, '焊钳工\焊缝处理\测漏\校平', 5, true, '');

--
-- Data for Name: dict_markup_fee; Type: TABLE DATA; Schema: public; Owner: -
--

INSERT INTO dict_markup_fee VALUES (5, '运输费', 5.00, 1, true);
INSERT INTO dict_markup_fee VALUES (6, '管理费', 5.00, 2, true);
INSERT INTO dict_markup_fee VALUES (7, '利润', 15.00, 3, true);
INSERT INTO dict_markup_fee VALUES (8, '税费', 13.00, 4, true);

--
-- Data for Name: dict_material_type; Type: TABLE DATA; Schema: public; Owner: -
--

INSERT INTO dict_material_type VALUES (1, 'RAW_MATERIAL', '原材料', '当前材料主数据默认类型', true, 1, '2026-08-10 10:07:59.996993', '2026-08-10 10:07:59.996993');

--
-- Data for Name: dict_material; Type: TABLE DATA; Schema: public; Owner: -
-- Concrete material rows are sourced from MES master-data sync.
--

--
-- Data for Name: dict_process_category; Type: TABLE DATA; Schema: public; Owner: -
-- Hourly process categories are sourced from MES master-data sync.
--

--
-- Data for Name: dict_process_detail; Type: TABLE DATA; Schema: public; Owner: -
-- Process details are created by MES sync defaults or maintained after sync.
--

--
-- Data for Name: erp_user; Type: TABLE DATA; Schema: public; Owner: -
--

INSERT INTO erp_user VALUES (2, 'market', '$2a$12$N4luOAukB3cz.gKFs7WicOBlULM8Z0Ik62yOtcllvkMN6J1a77b3C', '市场人员', 'MARKET', true, '2026-08-10 10:07:59.996993', false, 0, NULL, NULL, NULL);
INSERT INTO erp_user VALUES (3, 'engineer', '$2a$12$KbOZcqCA0n1wojKWha1lwOOjD.eNCWGEm4DatYg46qcl3IEn6hzaS', '工程人员', 'ENGINEER', true, '2026-08-10 10:07:59.996993', true, 0, NULL, NULL, NULL);
INSERT INTO erp_user VALUES (1, 'admin', '$2a$12$g3DNmPIxz.Krv.UGXo/IveIWizF3YDJF/TQKPfgUzGRwIPMFk07Oa', '管理员', 'ADMIN', true, '2026-08-10 10:07:59.996993', false, 0, NULL, '2026-08-12 08:14:36.617394', NULL);

--
-- Data for Name: system_data_center_endpoint_config; Type: TABLE DATA; Schema: public; Owner: -
--

INSERT INTO system_data_center_endpoint_config VALUES (1, 'MES_MATERIAL_RESOLVE', 'MES物料号获取', 'MES', 'POST', NULL, true, 30000, 'MES_MATERIAL_V1', 'MES_MATERIAL_RESPONSE_V1', '{}', '{}', NULL, NULL, NULL, 'Configure path in system configuration.', NULL, NULL, '2026-08-10 10:07:59.996993', '2026-08-10 10:07:59.996993');
INSERT INTO system_data_center_endpoint_config VALUES (2, 'WMS_INVENTORY_ALLOCATE', 'WMS库存锁定', 'WMS', 'POST', NULL, true, 30000, 'WMS_ALLOCATION_V1', 'WMS_ALLOCATION_RESPONSE_V1', '{}', '{}', NULL, NULL, NULL, 'Configure path in system configuration.', NULL, NULL, '2026-08-10 10:07:59.996993', '2026-08-10 10:07:59.996993');
INSERT INTO system_data_center_endpoint_config VALUES (4, 'MES_PRODUCTION_DISPATCH', '下发MES生产', 'MES', 'POST', NULL, true, 30000, 'MES_PRODUCTION_DISPATCH_V1', 'MES_PRODUCTION_DISPATCH_RESPONSE_V1', '{}', '{}', NULL, NULL, NULL, 'Configure path in system configuration.', NULL, NULL, '2026-08-10 10:07:59.996993', '2026-08-10 10:07:59.996993');
INSERT INTO system_data_center_endpoint_config VALUES (5, 'U9_COMMON_ENTITY_QUERY', 'U9通用实体查询', 'U9', 'POST', '/api/v1/u9/CommonEntity/Query', true, 60000, 'U9_COMMON_ENTITY_QUERY_V1', 'U9_COMMON_ENTITY_RESPONSE_V1', '{}', '{}', NULL, NULL, NULL, 'U9销售员、部门和销售订单对账查询。', NULL, NULL, '2026-08-11 13:32:45.634787', '2026-08-11 13:32:45.634787');
INSERT INTO system_data_center_endpoint_config VALUES (3, 'U9_SALES_ORDER_SYNC', 'U9同步', 'U9', 'POST', '/api/v1/u9/SO/Create', true, 60000, 'U9_SALES_ORDER_V1', 'U9_SALES_ORDER_RESPONSE_V1', '{}', '{}', 'SUCCESS', 'Endpoint config complete; token fetched; business endpoint not called', '2026-08-11 09:37:20.206754', 'QPO ??????????? U9', NULL, NULL, '2026-08-10 10:07:59.996993', '2026-08-11 13:32:45.634787');

--
-- Data for Name: system_dept; Type: TABLE DATA; Schema: public; Owner: -
--

INSERT INTO system_dept VALUES (1, 0, 'QPO', 'QPO', NULL, 10, true, '2026-07-18 14:40:05.361037', '2026-07-18 14:40:05.361037', false);
INSERT INTO system_dept VALUES (2, 1, 'MARKET', '市场部', NULL, 20, true, '2026-07-18 14:40:05.361037', '2026-07-18 14:40:05.361037', false);
INSERT INTO system_dept VALUES (3, 1, 'ENGINEER', '工程部', NULL, 30, true, '2026-07-18 14:40:05.361037', '2026-07-18 14:40:05.361037', false);
INSERT INTO system_dept VALUES (4, 1, 'SYSTEM', '系统管理', NULL, 40, true, '2026-07-18 14:40:05.361037', '2026-07-18 14:40:05.361037', false);

--
-- Data for Name: system_file_storage_config; Type: TABLE DATA; Schema: public; Owner: -
--

INSERT INTO system_file_storage_config VALUES (1, 'Default local upload storage', 'LOCAL', true, true, '{"envKey": "ERP_UPLOAD_DIR", "rootDir": "/data/uploads"}', 'Default LOCAL storage initialized for QPO uploads; ERP_UPLOAD_DIR remains the deployment fallback.', NULL, NULL, NULL, NULL, NULL, '2026-08-10 10:07:59.996993', '2026-08-10 10:07:59.996993');

--
-- Data for Name: system_inbound_callback_config; Type: TABLE DATA; Schema: public; Owner: -
--

INSERT INTO system_inbound_callback_config VALUES (1, 'MES_MATERIAL_CALLBACK', 'MES', 'MES物料号回调', true, true, NULL, '/api/integrations/mes/material-callback', NULL, NULL, NULL, NULL, NULL, NULL, 'Baseline callback metadata only; rotate token in system configuration after deployment.', NULL, NULL, '2026-08-10 10:07:59.996993', '2026-08-10 10:07:59.996993');

--
-- Data for Name: system_permission; Type: TABLE DATA; Schema: public; Owner: -
--

INSERT INTO system_permission VALUES (15, 109, 'MENU', 'system:post:view', '岗位管理', NULL, NULL, 'Postcard', 840, false, true, '2026-07-18 14:40:05.361037', '2026-07-20 17:16:12.342032', true);
INSERT INTO system_permission VALUES (36, 34, 'MENU', 'engineer:pricing:view', '逐件核价', '/pricing', 'ItemPricing', 'Cpu', 220, false, true, '2026-07-18 14:40:05.361037', '2026-07-18 19:47:07.918045', true);
INSERT INTO system_permission VALUES (17, 0, 'MENU', 'system:audit:view', '操作日志', '/audit-logs', 'AuditLogManage', 'DocumentChecked', 850, true, true, '2026-07-18 14:40:05.361037', '2026-07-18 19:47:07.918045', true);
INSERT INTO system_permission VALUES (40, 0, 'MENU', 'engineer:dict:view', '材料工艺', '/material-process', 'MaterialProcessManage', 'Collection', 230, true, true, '2026-07-18 14:40:05.361037', '2026-07-18 19:47:07.918045', true);
INSERT INTO system_permission VALUES (34, 0, 'MENU', 'engineer:setup:view', '零件核价', '/engineering', 'GlobalSetup', 'Tools', 210, true, true, '2026-07-18 14:40:05.361037', '2026-07-18 19:47:07.918045', true);
INSERT INTO system_permission VALUES (29, 0, 'MENU', 'market:release:view', '议价提单', '/release', 'MarketRelease', 'Finished', 140, true, true, '2026-07-18 14:40:05.361037', '2026-07-18 19:47:07.918045', true);
INSERT INTO system_permission VALUES (26, 0, 'MENU', 'market:quote-process:view', '报价处理', '/quote-process', 'QuoteProcess', 'Money', 130, true, true, '2026-07-18 14:40:05.361037', '2026-07-18 19:47:07.918045', true);
INSERT INTO system_permission VALUES (21, 20, 'BUTTON', 'market:rfq:import', '导入 RFQ', NULL, NULL, NULL, 111, false, true, '2026-07-18 14:40:05.361037', '2026-07-18 19:47:07.918045', true);
INSERT INTO system_permission VALUES (13, 109, 'MENU', 'system:dept:view', '部门管理', NULL, NULL, 'OfficeBuilding', 831, false, true, '2026-07-18 14:40:05.361037', '2026-07-20 17:16:12.342032', true);
INSERT INTO system_permission VALUES (32, 0, 'MENU', 'market:template:view', '报价模板', '/quote-template', 'QuoteTemplateManage', 'Files', 160, true, true, '2026-07-18 14:40:05.361037', '2026-07-31 16:37:16.449366', true);
INSERT INTO system_permission VALUES (23, 0, 'MENU', 'market:order:view', '询价订单', '/orders', 'OrderManage', 'Tickets', 120, true, true, '2026-07-18 14:40:05.361037', '2026-07-18 19:47:07.918045', true);
INSERT INTO system_permission VALUES (59, 0, 'MENU', 'market:history-order:view', '历史订单兼容权限（已废弃）', '/history-orders', NULL, 'Files', 125, false, true, '2026-07-18 19:47:07.918045', '2026-08-01 17:12:56.886985', false);
INSERT INTO system_permission VALUES (7, 0, 'MENU', 'system:role:view', '角色权限', '/roles', 'SystemRoleManage', 'Lock', 820, true, true, '2026-07-18 14:40:05.361037', '2026-07-18 19:47:07.918045', true);
INSERT INTO system_permission VALUES (1, 0, 'MENU', 'system:user:view', '用户管理', '/users', 'UserManage', 'User', 810, true, true, '2026-07-18 14:40:05.361037', '2026-07-18 19:47:07.918045', true);
INSERT INTO system_permission VALUES (20, 0, 'MENU', 'market:rfq:view', '新单导入', '/market', 'MarketDashboard', 'Upload', 110, true, true, '2026-07-18 14:40:05.361037', '2026-07-18 19:47:07.918045', true);
INSERT INTO system_permission VALUES (24, 23, 'BUTTON', 'market:order:supplement-drawing', '补传图纸', NULL, NULL, NULL, 121, false, true, '2026-07-18 14:40:05.361037', '2026-07-18 19:47:07.918045', true);
INSERT INTO system_permission VALUES (27, 26, 'BUTTON', 'market:quote-process:update-lead-time', '维护报价交期', NULL, NULL, NULL, 131, false, true, '2026-07-18 14:40:05.361037', '2026-07-18 19:47:07.918045', true);
INSERT INTO system_permission VALUES (28, 26, 'BUTTON', 'market:quote-process:export', '导出报价单', NULL, NULL, NULL, 132, false, true, '2026-07-18 14:40:05.361037', '2026-07-18 19:47:07.918045', true);
INSERT INTO system_permission VALUES (74, 26, 'API', 'market:quote-process:customer-select', '报价处理选择客户', NULL, NULL, NULL, 133, false, true, '2026-07-18 19:47:07.918045', '2026-07-18 19:47:07.918045', true);
INSERT INTO system_permission VALUES (75, 26, 'API', 'market:quote-process:order-select', '报价处理选择订单', NULL, NULL, NULL, 134, false, true, '2026-07-18 19:47:07.918045', '2026-07-18 19:47:07.918045', true);
INSERT INTO system_permission VALUES (76, 26, 'API', 'market:quote-process:task-view', '报价处理查看异步任务', NULL, NULL, NULL, 135, false, true, '2026-07-18 19:47:07.918045', '2026-07-18 19:47:07.918045', true);
INSERT INTO system_permission VALUES (30, 29, 'BUTTON', 'market:release:create', '生成提单', NULL, NULL, NULL, 141, false, true, '2026-07-18 14:40:05.361037', '2026-07-18 19:47:07.918045', true);
INSERT INTO system_permission VALUES (79, 29, 'API', 'market:release:customer-select', '议价提单选择客户', NULL, NULL, NULL, 143, false, true, '2026-07-18 19:47:07.918045', '2026-07-18 19:47:07.918045', true);
INSERT INTO system_permission VALUES (80, 29, 'API', 'market:release:order-select', '议价提单选择订单', NULL, NULL, NULL, 144, false, true, '2026-07-18 19:47:07.918045', '2026-07-18 19:47:07.918045', true);
INSERT INTO system_permission VALUES (81, 29, 'API', 'market:release:task-view', '议价提单查看异步任务', NULL, NULL, NULL, 145, false, true, '2026-07-18 19:47:07.918045', '2026-07-18 19:47:07.918045', true);
INSERT INTO system_permission VALUES (35, 34, 'BUTTON', 'engineer:setup:save-config', '保存全局设定', NULL, NULL, NULL, 211, false, true, '2026-07-18 14:40:05.361037', '2026-07-18 19:47:07.918045', true);
INSERT INTO system_permission VALUES (84, 34, 'API', 'engineer:setup:customer-select', '零件核价选择客户', NULL, NULL, NULL, 212, false, true, '2026-07-18 19:47:07.918045', '2026-07-18 19:47:07.918045', true);
INSERT INTO system_permission VALUES (85, 34, 'API', 'engineer:setup:order-select', '零件核价选择订单', NULL, NULL, NULL, 213, false, true, '2026-07-18 19:47:07.918045', '2026-07-18 19:47:07.918045', true);
INSERT INTO system_permission VALUES (86, 34, 'API', 'engineer:setup:file-preview', '零件核价预览图纸', NULL, NULL, NULL, 214, false, true, '2026-07-18 19:47:07.918045', '2026-07-18 19:47:07.918045', true);
INSERT INTO system_permission VALUES (37, 36, 'BUTTON', 'engineer:pricing:save-item', '保存单件核价', NULL, NULL, NULL, 221, false, true, '2026-07-18 14:40:05.361037', '2026-07-18 19:47:07.918045', true);
INSERT INTO system_permission VALUES (38, 36, 'BUTTON', 'engineer:pricing:refresh', '一键刷新核价', NULL, NULL, NULL, 222, false, true, '2026-07-18 14:40:05.361037', '2026-07-18 19:47:07.918045', true);
INSERT INTO system_permission VALUES (39, 36, 'BUTTON', 'engineer:pricing:complete-order', '完成整单报价', NULL, NULL, NULL, 223, false, true, '2026-07-18 14:40:05.361037', '2026-07-18 19:47:07.918045', true);
INSERT INTO system_permission VALUES (90, 36, 'API', 'engineer:pricing:task-view', '逐件核价查看异步任务', NULL, NULL, NULL, 224, false, true, '2026-07-18 19:47:07.918045', '2026-07-18 19:47:07.918045', true);
INSERT INTO system_permission VALUES (41, 40, 'BUTTON', 'engineer:dict:material-manage', '维护材料', NULL, NULL, NULL, 231, false, true, '2026-07-18 14:40:05.361037', '2026-07-18 19:47:07.918045', true);
INSERT INTO system_permission VALUES (42, 40, 'BUTTON', 'engineer:dict:process-manage', '维护工艺', NULL, NULL, NULL, 232, false, true, '2026-07-18 14:40:05.361037', '2026-07-18 19:47:07.918045', true);
INSERT INTO system_permission VALUES (43, 40, 'BUTTON', 'engineer:dict:fee-manage', '维护费用', NULL, NULL, NULL, 233, false, true, '2026-07-18 14:40:05.361037', '2026-07-18 19:47:07.918045', true);
INSERT INTO system_permission VALUES (2, 1, 'BUTTON', 'system:user:create', '新增用户', NULL, NULL, NULL, 811, false, true, '2026-07-18 14:40:05.361037', '2026-07-18 19:47:07.918045', true);
INSERT INTO system_permission VALUES (3, 1, 'BUTTON', 'system:user:update', '编辑用户', NULL, NULL, NULL, 812, false, true, '2026-07-18 14:40:05.361037', '2026-07-18 19:47:07.918045', true);
INSERT INTO system_permission VALUES (4, 1, 'BUTTON', 'system:user:disable', '启停用户', NULL, NULL, NULL, 813, false, true, '2026-07-18 14:40:05.361037', '2026-07-18 19:47:07.918045', true);
INSERT INTO system_permission VALUES (5, 1, 'BUTTON', 'system:user:reset-password', '重置密码', NULL, NULL, NULL, 814, false, true, '2026-07-18 14:40:05.361037', '2026-07-18 19:47:07.918045', true);
INSERT INTO system_permission VALUES (6, 1, 'BUTTON', 'system:user:delete', '删除用户', NULL, NULL, NULL, 815, false, true, '2026-07-18 14:40:05.361037', '2026-07-18 19:47:07.918045', true);
INSERT INTO system_permission VALUES (8, 7, 'BUTTON', 'system:role:create', '新增角色', NULL, NULL, NULL, 821, false, true, '2026-07-18 14:40:05.361037', '2026-07-18 19:47:07.918045', true);
INSERT INTO system_permission VALUES (9, 7, 'BUTTON', 'system:role:update', '编辑角色', NULL, NULL, NULL, 822, false, true, '2026-07-18 14:40:05.361037', '2026-07-18 19:47:07.918045', true);
INSERT INTO system_permission VALUES (10, 7, 'BUTTON', 'system:role:delete', '删除角色', NULL, NULL, NULL, 823, false, true, '2026-07-18 14:40:05.361037', '2026-07-18 19:47:07.918045', true);
INSERT INTO system_permission VALUES (11, 7, 'BUTTON', 'system:role:assign-permission', '分配角色权限', NULL, NULL, NULL, 824, false, true, '2026-07-18 14:40:05.361037', '2026-07-18 19:47:07.918045', true);
INSERT INTO system_permission VALUES (12, 7, 'BUTTON', 'system:role:assign-data-scope', '分配数据权限', NULL, NULL, NULL, 825, false, true, '2026-07-18 14:40:05.361037', '2026-07-18 19:47:07.918045', true);
INSERT INTO system_permission VALUES (18, 17, 'BUTTON', 'system:audit:export', '导出操作日志', NULL, NULL, NULL, 851, false, true, '2026-07-18 14:40:05.361037', '2026-07-18 19:47:07.918045', true);
INSERT INTO system_permission VALUES (19, 17, 'BUTTON', 'system:audit:retention', '配置日志保留策略', NULL, NULL, NULL, 852, false, true, '2026-07-18 14:40:05.361037', '2026-07-18 19:47:07.918045', true);
INSERT INTO system_permission VALUES (46, 0, 'API', 'common:file:preview', '预览文件', NULL, NULL, NULL, 912, false, true, '2026-07-18 14:40:05.361037', '2026-07-18 19:47:07.918045', false);
INSERT INTO system_permission VALUES (47, 0, 'API', 'common:task:view', '查看异步任务', NULL, NULL, NULL, 913, false, true, '2026-07-18 14:40:05.361037', '2026-07-18 19:47:07.918045', false);
INSERT INTO system_permission VALUES (108, 26, 'BUTTON', 'market:quote-process:save-quote', '保存报价单', NULL, NULL, NULL, 136, false, true, '2026-07-18 23:06:55.530972', '2026-07-18 23:06:55.530972', true);
INSERT INTO system_permission VALUES (109, 0, 'MENU', 'system:org:view', '组织档案', '/organization', 'SystemOrgManage', 'OfficeBuilding', 830, true, true, '2026-07-20 17:16:12.342032', '2026-07-20 17:16:12.342032', true);
INSERT INTO system_permission VALUES (16, 15, 'BUTTON', 'system:post:manage', '维护岗位', NULL, NULL, NULL, 841, false, true, '2026-07-18 14:40:05.361037', '2026-07-20 17:16:12.342032', true);
INSERT INTO system_permission VALUES (14, 13, 'BUTTON', 'system:dept:manage', '维护部门', NULL, NULL, NULL, 832, false, true, '2026-07-18 14:40:05.361037', '2026-07-20 17:16:12.342032', true);
INSERT INTO system_permission VALUES (112, 36, 'BUTTON', 'engineer:pricing:export-sheet', '生成核价单', NULL, NULL, NULL, 225, false, true, '2026-07-21 08:42:30.060493', '2026-07-21 08:42:30.060493', true);
INSERT INTO system_permission VALUES (113, 23, 'BUTTON', 'market:order:restore', '恢复废弃订单', NULL, NULL, NULL, 131, false, true, '2026-07-21 10:42:31.76266', '2026-07-21 10:42:31.76266', true);
INSERT INTO system_permission VALUES (114, 23, 'BUTTON', 'market:order:discard', '废弃订单', NULL, NULL, NULL, 130, false, true, '2026-07-21 10:42:31.76266', '2026-07-21 10:42:31.76266', true);
INSERT INTO system_permission VALUES (115, 23, 'BUTTON', 'market:order:rollback-priced', '已核价退回未核价', NULL, NULL, NULL, 129, false, true, '2026-07-21 10:42:31.76266', '2026-07-21 10:42:31.76266', true);
INSERT INTO system_permission VALUES (116, 23, 'BUTTON', 'market:order:rollback-quoted', '已报价退回已核价', NULL, NULL, NULL, 128, false, true, '2026-07-21 10:42:31.76266', '2026-07-21 10:42:31.76266', true);
INSERT INTO system_permission VALUES (22, 20, 'BUTTON', 'market:rfq:upload-drawing', '上传图纸', NULL, NULL, NULL, 114, false, true, '2026-07-18 14:40:05.361037', '2026-07-21 14:28:04.467909', true);
INSERT INTO system_permission VALUES (64, 20, 'API', 'market:rfq:customer-select', '新单导入选择客户', NULL, NULL, NULL, 115, false, true, '2026-07-18 19:47:07.918045', '2026-07-21 14:28:04.467909', true);
INSERT INTO system_permission VALUES (65, 20, 'API', 'market:rfq:file-preview', '新单导入预览文件', NULL, NULL, NULL, 116, false, true, '2026-07-18 19:47:07.918045', '2026-07-21 14:28:04.467909', true);
INSERT INTO system_permission VALUES (33, 32, 'BUTTON', 'market:template:upload', '上传报价模板', NULL, NULL, NULL, 161, false, true, '2026-07-18 14:40:05.361037', '2026-07-31 16:37:16.449366', true);
INSERT INTO system_permission VALUES (68, 23, 'API', 'market:order:customer-select', '询价订单选择客户', NULL, NULL, NULL, 123, false, true, '2026-07-18 19:47:07.918045', '2026-07-18 19:47:07.918045', true);
INSERT INTO system_permission VALUES (69, 23, 'API', 'market:order:file-preview', '询价订单预览文件', NULL, NULL, NULL, 124, false, true, '2026-07-18 19:47:07.918045', '2026-07-18 19:47:07.918045', true);
INSERT INTO system_permission VALUES (25, 23, 'BUTTON', 'market:order:delete', '删除订单兼容接口（已废弃）', NULL, NULL, NULL, 122, false, true, '2026-07-18 14:40:05.361037', '2026-08-01 17:12:56.886985', false);
INSERT INTO system_permission VALUES (70, 59, 'API', 'market:history-order:customer-select', '历史订单客户选择兼容接口（已废弃）', NULL, NULL, NULL, 126, false, true, '2026-07-18 19:47:07.918045', '2026-08-01 17:12:56.886985', false);
INSERT INTO system_permission VALUES (71, 59, 'API', 'market:history-order:file-preview', '历史订单文件预览兼容接口（已废弃）', NULL, NULL, NULL, 127, false, true, '2026-07-18 19:47:07.918045', '2026-08-01 17:12:56.886985', false);
INSERT INTO system_permission VALUES (118, NULL, 'MENU', 'market:delivered-item:view', '已提零件', '/delivered-items', 'DeliveredItemManage', 'Files', 126, true, true, '2026-07-21 16:27:49.696304', '2026-07-21 23:16:37.136114', true);
INSERT INTO system_permission VALUES (122, NULL, 'MENU', 'market:locked-item:view', '锁库零件', '/locked-items', 'LockedItemManage', 'Files', 127, true, true, '2026-07-21 20:30:51.620799', '2026-07-21 23:16:37.136114', true);
INSERT INTO system_permission VALUES (120, 118, 'BUTTON', 'market:delivered-item:mes-sync', '获取MES物料号', NULL, NULL, NULL, 128, false, true, '2026-07-21 18:56:59.71753', '2026-07-21 23:16:37.136114', true);
INSERT INTO system_permission VALUES (121, 118, 'BUTTON', 'market:delivered-item:wms-allocate', '提交WMS锁库', NULL, NULL, NULL, 129, false, true, '2026-07-21 20:30:51.620799', '2026-07-21 23:16:37.136114', true);
INSERT INTO system_permission VALUES (119, 118, 'API', 'market:delivered-item:file-preview', '已提零件预览图纸', NULL, NULL, NULL, 127, false, true, '2026-07-21 16:27:49.696304', '2026-07-21 23:16:37.136114', true);
INSERT INTO system_permission VALUES (123, 118, 'API', 'market:delivered-item:customer-select', '已提零件选择客户', NULL, NULL, NULL, 130, false, true, '2026-07-22 13:43:18.831924', '2026-07-22 13:43:18.831924', true);
INSERT INTO system_permission VALUES (124, 122, 'API', 'market:locked-item:customer-select', '锁库零件选择客户', NULL, NULL, NULL, 128, false, true, '2026-07-22 13:43:18.831924', '2026-07-22 13:43:18.831924', true);
INSERT INTO system_permission VALUES (129, 20, 'BUTTON', 'market:rfq:import-sales-order', '导入销售订单', NULL, NULL, NULL, 117, false, true, '2026-07-24 13:20:42.148611', '2026-07-24 13:20:42.148611', true);
INSERT INTO system_permission VALUES (130, 0, 'MENU', 'system:customer:view', '客户资料', '/customers', 'CustomerManage', 'Users', 845, true, true, '2026-07-30 09:43:27.610615', '2026-07-30 09:43:27.610615', true);
INSERT INTO system_permission VALUES (134, 0, 'MENU', 'system:config:view', '系统配置', '/system-config', 'SystemConfigManage', 'Setting', 849, true, true, '2026-07-30 10:58:35.896155', '2026-07-30 10:58:35.896155', true);
INSERT INTO system_permission VALUES (135, 134, 'BUTTON', 'system:config:storage:activate', '激活文件存储配置', NULL, NULL, NULL, 8493, false, true, '2026-07-30 10:58:35.896155', '2026-07-30 10:58:35.896155', true);
INSERT INTO system_permission VALUES (136, 134, 'BUTTON', 'system:config:storage:test', '测试文件存储配置', NULL, NULL, NULL, 8492, false, true, '2026-07-30 10:58:35.896155', '2026-07-30 10:58:35.896155', true);
INSERT INTO system_permission VALUES (137, 134, 'BUTTON', 'system:config:storage:update', '维护文件存储配置', NULL, NULL, NULL, 8491, false, true, '2026-07-30 10:58:35.896155', '2026-07-30 10:58:35.896155', true);
INSERT INTO system_permission VALUES (146, 0, 'MENU', 'market:u9-sync:view', 'U9同步', '/u9-sync', 'SalesOrderU9Sync', 'Document', 146, true, true, '2026-07-31 16:37:16.449366', '2026-07-31 16:37:16.449366', true);
INSERT INTO system_permission VALUES (147, 146, 'BUTTON', 'market:u9-sync:submit', '同步U9', NULL, NULL, NULL, 149, false, true, '2026-07-31 16:37:16.449366', '2026-07-31 16:37:16.449366', true);
INSERT INTO system_permission VALUES (148, 146, 'API', 'market:u9-sync:order-select', 'U9同步选择销售订单', NULL, NULL, NULL, 148, false, true, '2026-07-31 16:37:16.449366', '2026-07-31 16:37:16.449366', true);
INSERT INTO system_permission VALUES (149, 146, 'API', 'market:u9-sync:customer-select', 'U9同步选择客户', NULL, NULL, NULL, 147, false, true, '2026-07-31 16:37:16.449366', '2026-07-31 16:37:16.449366', true);
INSERT INTO system_permission VALUES (125, 0, 'MENU', 'market:delivery-document:view', '提单表据', '/delivery-documents', 'DeliveryDocumentManage', 'Document', 150, true, true, '2026-07-23 13:29:34.176325', '2026-07-31 16:37:16.449366', true);
INSERT INTO system_permission VALUES (126, 125, 'BUTTON', 'market:delivery-document:export', '导出提单表据', NULL, NULL, NULL, 153, false, true, '2026-07-23 13:29:34.176325', '2026-07-31 16:37:16.449366', true);
INSERT INTO system_permission VALUES (127, 125, 'API', 'market:delivery-document:order-select', '提单表据选择订单', NULL, NULL, NULL, 152, false, true, '2026-07-23 13:29:34.176325', '2026-07-31 16:37:16.449366', true);
INSERT INTO system_permission VALUES (128, 125, 'API', 'market:delivery-document:customer-select', '提单表据选择客户', NULL, NULL, NULL, 151, false, true, '2026-07-23 13:29:34.176325', '2026-07-31 16:37:16.449366', true);
INSERT INTO system_permission VALUES (150, 0, 'MENU', 'market:sales-order:view', '销售订单', '/sales-orders', 'SalesOrderManage', 'Files', 125, true, true, '2026-08-01 16:24:58.217822', '2026-08-01 16:24:58.217822', true);
INSERT INTO system_permission VALUES (151, 150, 'BUTTON', 'market:sales-order:restore', '恢复销售订单', NULL, NULL, NULL, 130, false, true, '2026-08-01 16:24:58.217822', '2026-08-01 16:24:58.217822', true);
INSERT INTO system_permission VALUES (152, 150, 'BUTTON', 'market:sales-order:discard', '废弃销售订单', NULL, NULL, NULL, 129, false, true, '2026-08-01 16:24:58.217822', '2026-08-01 16:24:58.217822', true);
INSERT INTO system_permission VALUES (153, 150, 'BUTTON', 'market:sales-order:supplement-drawing', '销售订单补传图纸', NULL, NULL, NULL, 128, false, true, '2026-08-01 16:24:58.217822', '2026-08-01 16:24:58.217822', true);
INSERT INTO system_permission VALUES (154, 150, 'API', 'market:sales-order:file-preview', '销售订单预览文件', NULL, NULL, NULL, 127, false, true, '2026-08-01 16:24:58.217822', '2026-08-01 16:24:58.217822', true);
INSERT INTO system_permission VALUES (155, 150, 'API', 'market:sales-order:customer-select', '销售订单选择客户', NULL, NULL, NULL, 126, false, true, '2026-08-01 16:24:58.217822', '2026-08-01 16:24:58.217822', true);
INSERT INTO system_permission VALUES (157, 134, 'BUTTON', 'system:config:inbound:token-rotate', '轮换入站回调Token', NULL, NULL, NULL, 8498, false, true, '2026-08-03 10:53:19.365404', '2026-08-03 10:53:19.365404', true);
INSERT INTO system_permission VALUES (158, 134, 'BUTTON', 'system:config:inbound:update', '维护入站接口配置', NULL, NULL, NULL, 8497, false, true, '2026-08-03 10:53:19.365404', '2026-08-03 10:53:19.365404', true);
INSERT INTO system_permission VALUES (138, 134, 'BUTTON', 'system:config:integration:activate', '激活出站接口/数据中心配置', NULL, NULL, NULL, 8496, false, true, '2026-07-30 22:18:03.611528', '2026-08-03 12:01:06.83654', true);
INSERT INTO system_permission VALUES (139, 134, 'BUTTON', 'system:config:integration:test', '测试出站接口/数据中心配置', NULL, NULL, NULL, 8495, false, true, '2026-07-30 22:18:03.611528', '2026-08-03 12:01:06.83654', true);
INSERT INTO system_permission VALUES (140, 134, 'BUTTON', 'system:config:integration:update', '维护出站接口/数据中心配置', NULL, NULL, NULL, 8494, false, true, '2026-07-30 22:18:03.611528', '2026-08-03 12:01:06.83654', true);
INSERT INTO system_permission VALUES (159, NULL, 'MENU', 'market:production-item:view', '待产零件', '/production-items', 'ProductionItemManage', NULL, 127, true, true, '2026-08-05 09:40:39.761693', '2026-08-05 09:40:39.761693', true);
INSERT INTO system_permission VALUES (160, 159, 'BUTTON', 'market:production-item:mes-production-submit', '待产零件下发MES生产', NULL, NULL, NULL, 129, false, true, '2026-08-05 09:40:39.761693', '2026-08-05 09:40:39.761693', true);
INSERT INTO system_permission VALUES (161, 159, 'API', 'market:production-item:customer-select', '待产零件选择客户', NULL, NULL, NULL, 128, false, true, '2026-08-05 09:40:39.761693', '2026-08-05 09:40:39.761693', true);
INSERT INTO system_permission VALUES (156, 122, 'BUTTON', 'market:locked-item:mes-production-submit', '锁库零件下发MES生产', NULL, NULL, NULL, 129, false, false, '2026-08-02 23:35:01.275108', '2026-08-05 09:40:39.761693', false);
INSERT INTO system_permission VALUES (131, 130, 'BUTTON', 'system:customer:delete', '删除客户（已下线）', NULL, NULL, NULL, 848, false, false, '2026-07-30 09:43:27.610615', '2026-08-11 18:29:24.080487', false);
INSERT INTO system_permission VALUES (132, 130, 'BUTTON', 'system:customer:update', '编辑客户（已下线）', NULL, NULL, NULL, 847, false, false, '2026-07-30 09:43:27.610615', '2026-08-11 18:29:24.080487', false);
INSERT INTO system_permission VALUES (133, 130, 'BUTTON', 'system:customer:create', '新增客户（已下线）', NULL, NULL, NULL, 846, false, false, '2026-07-30 09:43:27.610615', '2026-08-11 18:29:24.080487', false);
INSERT INTO system_permission VALUES (162, 130, 'BUTTON', 'system:customer:sync', '同步U9客户', NULL, NULL, NULL, 846, false, true, '2026-08-11 18:29:24.080487', '2026-08-11 18:29:24.080487', true);

--
-- Data for Name: system_post; Type: TABLE DATA; Schema: public; Owner: -
--

INSERT INTO system_post VALUES (1, 'MARKET_SPECIALIST', '市场专员', 10, true, '2026-07-18 14:40:05.361037', '2026-07-18 14:40:05.361037', false);
INSERT INTO system_post VALUES (2, 'MARKET_MANAGER', '市场主管', 20, true, '2026-07-18 14:40:05.361037', '2026-07-18 14:40:05.361037', false);
INSERT INTO system_post VALUES (3, 'ENGINEER_QUOTER', '工程核价员', 30, true, '2026-07-18 14:40:05.361037', '2026-07-18 14:40:05.361037', false);
INSERT INTO system_post VALUES (4, 'ENGINEER_MANAGER', '工程主管', 40, true, '2026-07-18 14:40:05.361037', '2026-07-18 14:40:05.361037', false);
INSERT INTO system_post VALUES (5, 'SYSTEM_ADMIN', '系统管理员', 50, true, '2026-07-18 14:40:05.361037', '2026-07-18 14:40:05.361037', false);

--
-- Data for Name: system_role; Type: TABLE DATA; Schema: public; Owner: -
--

INSERT INTO system_role VALUES (3, 'MARKET_MANAGER', '市场主管', '市场全功能与部门数据', false, 'ALL', true, 30, '2026-07-18 14:40:05.361037', '2026-07-18 14:40:05.361037', false);
INSERT INTO system_role VALUES (4, 'ENGINEER_USER', '工程核价员', '工程核价主流程默认角色', false, 'ALL', true, 40, '2026-07-18 14:40:05.361037', '2026-07-18 14:40:05.361037', false);
INSERT INTO system_role VALUES (5, 'ENGINEER_MANAGER', '工程主管', '工程全功能与字典维护', false, 'ALL', true, 50, '2026-07-18 14:40:05.361037', '2026-07-18 14:40:05.361037', false);
INSERT INTO system_role VALUES (6, 'AUDITOR', '审计员', '操作日志只读和导出', false, 'ALL', true, 60, '2026-07-18 14:40:05.361037', '2026-07-18 14:40:05.361037', false);
INSERT INTO system_role VALUES (7, 'READONLY', '只读查看员', '业务数据只读查看', false, 'ALL', true, 70, '2026-07-18 14:40:05.361037', '2026-07-18 14:40:05.361037', false);
INSERT INTO system_role VALUES (2, 'MARKET_USER', '市场专员', '市场主流程默认角色', false, 'ALL', true, 20, '2026-07-18 14:40:05.361037', '2026-07-20 16:01:52.883134', false);
INSERT INTO system_role VALUES (1, 'SUPER_ADMIN', '超级管理员', '全部功能、全部数据', true, 'ALL', true, 10, '2026-07-18 14:40:05.361037', '2026-07-21 10:37:36.390871', false);

--
-- Data for Name: system_role_permission; Type: TABLE DATA; Schema: public; Owner: -
--

INSERT INTO system_role_permission VALUES (2, 20, '2026-07-20 16:03:12.292792');
INSERT INTO system_role_permission VALUES (2, 21, '2026-07-20 16:03:12.292792');
INSERT INTO system_role_permission VALUES (2, 22, '2026-07-20 16:03:12.292792');
INSERT INTO system_role_permission VALUES (2, 64, '2026-07-20 16:03:12.292792');
INSERT INTO system_role_permission VALUES (2, 65, '2026-07-20 16:03:12.292792');
INSERT INTO system_role_permission VALUES (2, 23, '2026-07-20 16:03:12.292792');
INSERT INTO system_role_permission VALUES (2, 24, '2026-07-20 16:03:12.292792');
INSERT INTO system_role_permission VALUES (2, 68, '2026-07-20 16:03:12.292792');
INSERT INTO system_role_permission VALUES (2, 69, '2026-07-20 16:03:12.292792');
INSERT INTO system_role_permission VALUES (2, 59, '2026-07-20 16:03:12.292792');
INSERT INTO system_role_permission VALUES (2, 70, '2026-07-20 16:03:12.292792');
INSERT INTO system_role_permission VALUES (2, 71, '2026-07-20 16:03:12.292792');
INSERT INTO system_role_permission VALUES (2, 26, '2026-07-20 16:03:12.292792');
INSERT INTO system_role_permission VALUES (2, 27, '2026-07-20 16:03:12.292792');
INSERT INTO system_role_permission VALUES (2, 28, '2026-07-20 16:03:12.292792');
INSERT INTO system_role_permission VALUES (2, 74, '2026-07-20 16:03:12.292792');
INSERT INTO system_role_permission VALUES (2, 75, '2026-07-20 16:03:12.292792');
INSERT INTO system_role_permission VALUES (2, 76, '2026-07-20 16:03:12.292792');
INSERT INTO system_role_permission VALUES (2, 108, '2026-07-20 16:03:12.292792');
INSERT INTO system_role_permission VALUES (2, 29, '2026-07-20 16:03:12.292792');
INSERT INTO system_role_permission VALUES (2, 30, '2026-07-20 16:03:12.292792');
INSERT INTO system_role_permission VALUES (2, 79, '2026-07-20 16:03:12.292792');
INSERT INTO system_role_permission VALUES (2, 80, '2026-07-20 16:03:12.292792');
INSERT INTO system_role_permission VALUES (2, 81, '2026-07-20 16:03:12.292792');
INSERT INTO system_role_permission VALUES (2, 46, '2026-07-20 16:03:12.292792');
INSERT INTO system_role_permission VALUES (3, 120, '2026-07-21 18:56:59.71753');
INSERT INTO system_role_permission VALUES (2, 120, '2026-07-21 18:56:59.71753');
INSERT INTO system_role_permission VALUES (1, 120, '2026-07-21 18:56:59.71753');
INSERT INTO system_role_permission VALUES (3, 123, '2026-07-22 13:43:18.831924');
INSERT INTO system_role_permission VALUES (3, 124, '2026-07-22 13:43:18.831924');
INSERT INTO system_role_permission VALUES (7, 123, '2026-07-22 13:43:18.831924');
INSERT INTO system_role_permission VALUES (7, 124, '2026-07-22 13:43:18.831924');
INSERT INTO system_role_permission VALUES (2, 123, '2026-07-22 13:43:18.831924');
INSERT INTO system_role_permission VALUES (2, 124, '2026-07-22 13:43:18.831924');
INSERT INTO system_role_permission VALUES (1, 123, '2026-07-22 13:43:18.831924');
INSERT INTO system_role_permission VALUES (1, 124, '2026-07-22 13:43:18.831924');
INSERT INTO system_role_permission VALUES (3, 125, '2026-07-23 13:29:34.176325');
INSERT INTO system_role_permission VALUES (3, 127, '2026-07-23 13:29:34.176325');
INSERT INTO system_role_permission VALUES (3, 128, '2026-07-23 13:29:34.176325');
INSERT INTO system_role_permission VALUES (7, 125, '2026-07-23 13:29:34.176325');
INSERT INTO system_role_permission VALUES (7, 127, '2026-07-23 13:29:34.176325');
INSERT INTO system_role_permission VALUES (7, 128, '2026-07-23 13:29:34.176325');
INSERT INTO system_role_permission VALUES (2, 125, '2026-07-23 13:29:34.176325');
INSERT INTO system_role_permission VALUES (2, 127, '2026-07-23 13:29:34.176325');
INSERT INTO system_role_permission VALUES (2, 128, '2026-07-23 13:29:34.176325');
INSERT INTO system_role_permission VALUES (1, 125, '2026-07-23 13:29:34.176325');
INSERT INTO system_role_permission VALUES (1, 127, '2026-07-23 13:29:34.176325');
INSERT INTO system_role_permission VALUES (1, 128, '2026-07-23 13:29:34.176325');
INSERT INTO system_role_permission VALUES (3, 126, '2026-07-23 13:29:34.176325');
INSERT INTO system_role_permission VALUES (2, 126, '2026-07-23 13:29:34.176325');
INSERT INTO system_role_permission VALUES (1, 126, '2026-07-23 13:29:34.176325');
INSERT INTO system_role_permission VALUES (3, 129, '2026-07-24 13:20:42.148611');
INSERT INTO system_role_permission VALUES (2, 129, '2026-07-24 13:20:42.148611');
INSERT INTO system_role_permission VALUES (1, 129, '2026-07-24 13:20:42.148611');
INSERT INTO system_role_permission VALUES (3, 130, '2026-07-30 09:43:27.610615');
INSERT INTO system_role_permission VALUES (1, 130, '2026-07-30 09:43:27.610615');
INSERT INTO system_role_permission VALUES (3, 20, '2026-07-18 14:40:05.361037');
INSERT INTO system_role_permission VALUES (3, 21, '2026-07-18 14:40:05.361037');
INSERT INTO system_role_permission VALUES (3, 22, '2026-07-18 14:40:05.361037');
INSERT INTO system_role_permission VALUES (3, 23, '2026-07-18 14:40:05.361037');
INSERT INTO system_role_permission VALUES (3, 24, '2026-07-18 14:40:05.361037');
INSERT INTO system_role_permission VALUES (3, 25, '2026-07-18 14:40:05.361037');
INSERT INTO system_role_permission VALUES (3, 26, '2026-07-18 14:40:05.361037');
INSERT INTO system_role_permission VALUES (3, 27, '2026-07-18 14:40:05.361037');
INSERT INTO system_role_permission VALUES (3, 28, '2026-07-18 14:40:05.361037');
INSERT INTO system_role_permission VALUES (3, 29, '2026-07-18 14:40:05.361037');
INSERT INTO system_role_permission VALUES (3, 30, '2026-07-18 14:40:05.361037');
INSERT INTO system_role_permission VALUES (2, 47, '2026-07-20 16:03:12.292792');
INSERT INTO system_role_permission VALUES (3, 113, '2026-07-21 10:42:31.76266');
INSERT INTO system_role_permission VALUES (3, 46, '2026-07-18 14:40:05.361037');
INSERT INTO system_role_permission VALUES (3, 47, '2026-07-18 14:40:05.361037');
INSERT INTO system_role_permission VALUES (4, 34, '2026-07-18 14:40:05.361037');
INSERT INTO system_role_permission VALUES (4, 35, '2026-07-18 14:40:05.361037');
INSERT INTO system_role_permission VALUES (4, 36, '2026-07-18 14:40:05.361037');
INSERT INTO system_role_permission VALUES (4, 37, '2026-07-18 14:40:05.361037');
INSERT INTO system_role_permission VALUES (4, 38, '2026-07-18 14:40:05.361037');
INSERT INTO system_role_permission VALUES (4, 39, '2026-07-18 14:40:05.361037');
INSERT INTO system_role_permission VALUES (4, 40, '2026-07-18 14:40:05.361037');
INSERT INTO system_role_permission VALUES (3, 114, '2026-07-21 10:42:31.76266');
INSERT INTO system_role_permission VALUES (4, 46, '2026-07-18 14:40:05.361037');
INSERT INTO system_role_permission VALUES (4, 47, '2026-07-18 14:40:05.361037');
INSERT INTO system_role_permission VALUES (5, 34, '2026-07-18 14:40:05.361037');
INSERT INTO system_role_permission VALUES (5, 35, '2026-07-18 14:40:05.361037');
INSERT INTO system_role_permission VALUES (5, 36, '2026-07-18 14:40:05.361037');
INSERT INTO system_role_permission VALUES (5, 37, '2026-07-18 14:40:05.361037');
INSERT INTO system_role_permission VALUES (5, 38, '2026-07-18 14:40:05.361037');
INSERT INTO system_role_permission VALUES (5, 39, '2026-07-18 14:40:05.361037');
INSERT INTO system_role_permission VALUES (5, 40, '2026-07-18 14:40:05.361037');
INSERT INTO system_role_permission VALUES (5, 41, '2026-07-18 14:40:05.361037');
INSERT INTO system_role_permission VALUES (5, 42, '2026-07-18 14:40:05.361037');
INSERT INTO system_role_permission VALUES (5, 43, '2026-07-18 14:40:05.361037');
INSERT INTO system_role_permission VALUES (3, 115, '2026-07-21 10:42:31.76266');
INSERT INTO system_role_permission VALUES (5, 46, '2026-07-18 14:40:05.361037');
INSERT INTO system_role_permission VALUES (5, 47, '2026-07-18 14:40:05.361037');
INSERT INTO system_role_permission VALUES (6, 17, '2026-07-18 14:40:05.361037');
INSERT INTO system_role_permission VALUES (6, 18, '2026-07-18 14:40:05.361037');
INSERT INTO system_role_permission VALUES (7, 20, '2026-07-18 14:40:05.361037');
INSERT INTO system_role_permission VALUES (7, 23, '2026-07-18 14:40:05.361037');
INSERT INTO system_role_permission VALUES (7, 26, '2026-07-18 14:40:05.361037');
INSERT INTO system_role_permission VALUES (7, 29, '2026-07-18 14:40:05.361037');
INSERT INTO system_role_permission VALUES (7, 34, '2026-07-18 14:40:05.361037');
INSERT INTO system_role_permission VALUES (7, 36, '2026-07-18 14:40:05.361037');
INSERT INTO system_role_permission VALUES (7, 40, '2026-07-18 14:40:05.361037');
INSERT INTO system_role_permission VALUES (3, 116, '2026-07-21 10:42:31.76266');
INSERT INTO system_role_permission VALUES (7, 46, '2026-07-18 14:40:05.361037');
INSERT INTO system_role_permission VALUES (7, 47, '2026-07-18 14:40:05.361037');
INSERT INTO system_role_permission VALUES (1, 138, '2026-07-30 22:18:03.611528');
INSERT INTO system_role_permission VALUES (1, 139, '2026-07-30 22:18:03.611528');
INSERT INTO system_role_permission VALUES (1, 140, '2026-07-30 22:18:03.611528');
INSERT INTO system_role_permission VALUES (3, 32, '2026-07-18 19:47:07.918045');
INSERT INTO system_role_permission VALUES (3, 59, '2026-07-18 19:47:07.918045');
INSERT INTO system_role_permission VALUES (7, 59, '2026-07-18 19:47:07.918045');
INSERT INTO system_role_permission VALUES (3, 64, '2026-07-18 19:47:07.918045');
INSERT INTO system_role_permission VALUES (7, 64, '2026-07-18 19:47:07.918045');
INSERT INTO system_role_permission VALUES (3, 65, '2026-07-18 19:47:07.918045');
INSERT INTO system_role_permission VALUES (7, 65, '2026-07-18 19:47:07.918045');
INSERT INTO system_role_permission VALUES (3, 68, '2026-07-18 19:47:07.918045');
INSERT INTO system_role_permission VALUES (7, 68, '2026-07-18 19:47:07.918045');
INSERT INTO system_role_permission VALUES (3, 69, '2026-07-18 19:47:07.918045');
INSERT INTO system_role_permission VALUES (7, 69, '2026-07-18 19:47:07.918045');
INSERT INTO system_role_permission VALUES (3, 150, '2026-08-01 16:24:58.217822');
INSERT INTO system_role_permission VALUES (3, 70, '2026-07-18 19:47:07.918045');
INSERT INTO system_role_permission VALUES (7, 70, '2026-07-18 19:47:07.918045');
INSERT INTO system_role_permission VALUES (3, 154, '2026-08-01 16:24:58.217822');
INSERT INTO system_role_permission VALUES (3, 71, '2026-07-18 19:47:07.918045');
INSERT INTO system_role_permission VALUES (7, 71, '2026-07-18 19:47:07.918045');
INSERT INTO system_role_permission VALUES (3, 155, '2026-08-01 16:24:58.217822');
INSERT INTO system_role_permission VALUES (3, 74, '2026-07-18 19:47:07.918045');
INSERT INTO system_role_permission VALUES (7, 74, '2026-07-18 19:47:07.918045');
INSERT INTO system_role_permission VALUES (7, 150, '2026-08-01 16:24:58.217822');
INSERT INTO system_role_permission VALUES (7, 154, '2026-08-01 16:24:58.217822');
INSERT INTO system_role_permission VALUES (3, 118, '2026-07-21 16:27:49.696304');
INSERT INTO system_role_permission VALUES (3, 75, '2026-07-18 19:47:07.918045');
INSERT INTO system_role_permission VALUES (7, 75, '2026-07-18 19:47:07.918045');
INSERT INTO system_role_permission VALUES (3, 119, '2026-07-21 16:27:49.696304');
INSERT INTO system_role_permission VALUES (3, 76, '2026-07-18 19:47:07.918045');
INSERT INTO system_role_permission VALUES (7, 76, '2026-07-18 19:47:07.918045');
INSERT INTO system_role_permission VALUES (7, 118, '2026-07-21 16:27:49.696304');
INSERT INTO system_role_permission VALUES (3, 79, '2026-07-18 19:47:07.918045');
INSERT INTO system_role_permission VALUES (7, 79, '2026-07-18 19:47:07.918045');
INSERT INTO system_role_permission VALUES (7, 119, '2026-07-21 16:27:49.696304');
INSERT INTO system_role_permission VALUES (3, 80, '2026-07-18 19:47:07.918045');
INSERT INTO system_role_permission VALUES (7, 80, '2026-07-18 19:47:07.918045');
INSERT INTO system_role_permission VALUES (2, 118, '2026-07-21 16:27:49.696304');
INSERT INTO system_role_permission VALUES (3, 81, '2026-07-18 19:47:07.918045');
INSERT INTO system_role_permission VALUES (7, 81, '2026-07-18 19:47:07.918045');
INSERT INTO system_role_permission VALUES (3, 33, '2026-07-18 19:47:07.918045');
INSERT INTO system_role_permission VALUES (4, 84, '2026-07-18 19:47:07.918045');
INSERT INTO system_role_permission VALUES (5, 84, '2026-07-18 19:47:07.918045');
INSERT INTO system_role_permission VALUES (7, 84, '2026-07-18 19:47:07.918045');
INSERT INTO system_role_permission VALUES (4, 85, '2026-07-18 19:47:07.918045');
INSERT INTO system_role_permission VALUES (5, 85, '2026-07-18 19:47:07.918045');
INSERT INTO system_role_permission VALUES (7, 85, '2026-07-18 19:47:07.918045');
INSERT INTO system_role_permission VALUES (4, 86, '2026-07-18 19:47:07.918045');
INSERT INTO system_role_permission VALUES (5, 86, '2026-07-18 19:47:07.918045');
INSERT INTO system_role_permission VALUES (7, 86, '2026-07-18 19:47:07.918045');
INSERT INTO system_role_permission VALUES (4, 90, '2026-07-18 19:47:07.918045');
INSERT INTO system_role_permission VALUES (5, 90, '2026-07-18 19:47:07.918045');
INSERT INTO system_role_permission VALUES (7, 90, '2026-07-18 19:47:07.918045');
INSERT INTO system_role_permission VALUES (2, 119, '2026-07-21 16:27:49.696304');
INSERT INTO system_role_permission VALUES (1, 118, '2026-07-21 16:27:49.696304');
INSERT INTO system_role_permission VALUES (3, 108, '2026-07-18 23:06:55.530972');
INSERT INTO system_role_permission VALUES (1, 119, '2026-07-21 16:27:49.696304');
INSERT INTO system_role_permission VALUES (3, 121, '2026-07-21 20:30:51.620799');
INSERT INTO system_role_permission VALUES (2, 121, '2026-07-21 20:30:51.620799');
INSERT INTO system_role_permission VALUES (1, 121, '2026-07-21 20:30:51.620799');
INSERT INTO system_role_permission VALUES (3, 122, '2026-07-21 20:30:51.620799');
INSERT INTO system_role_permission VALUES (7, 122, '2026-07-21 20:30:51.620799');
INSERT INTO system_role_permission VALUES (2, 122, '2026-07-21 20:30:51.620799');
INSERT INTO system_role_permission VALUES (1, 122, '2026-07-21 20:30:51.620799');
INSERT INTO system_role_permission VALUES (1, 134, '2026-07-30 10:58:35.896155');
INSERT INTO system_role_permission VALUES (1, 135, '2026-07-30 10:58:35.896155');
INSERT INTO system_role_permission VALUES (1, 136, '2026-07-30 10:58:35.896155');
INSERT INTO system_role_permission VALUES (1, 137, '2026-07-30 10:58:35.896155');
INSERT INTO system_role_permission VALUES (3, 146, '2026-07-31 16:37:16.449366');
INSERT INTO system_role_permission VALUES (3, 148, '2026-07-31 16:37:16.449366');
INSERT INTO system_role_permission VALUES (3, 149, '2026-07-31 16:37:16.449366');
INSERT INTO system_role_permission VALUES (7, 146, '2026-07-31 16:37:16.449366');
INSERT INTO system_role_permission VALUES (7, 148, '2026-07-31 16:37:16.449366');
INSERT INTO system_role_permission VALUES (7, 149, '2026-07-31 16:37:16.449366');
INSERT INTO system_role_permission VALUES (2, 146, '2026-07-31 16:37:16.449366');
INSERT INTO system_role_permission VALUES (2, 148, '2026-07-31 16:37:16.449366');
INSERT INTO system_role_permission VALUES (2, 149, '2026-07-31 16:37:16.449366');
INSERT INTO system_role_permission VALUES (1, 146, '2026-07-31 16:37:16.449366');
INSERT INTO system_role_permission VALUES (1, 148, '2026-07-31 16:37:16.449366');
INSERT INTO system_role_permission VALUES (1, 149, '2026-07-31 16:37:16.449366');
INSERT INTO system_role_permission VALUES (3, 147, '2026-07-31 16:37:16.449366');
INSERT INTO system_role_permission VALUES (2, 147, '2026-07-31 16:37:16.449366');
INSERT INTO system_role_permission VALUES (1, 147, '2026-07-31 16:37:16.449366');
INSERT INTO system_role_permission VALUES (7, 155, '2026-08-01 16:24:58.217822');
INSERT INTO system_role_permission VALUES (2, 150, '2026-08-01 16:24:58.217822');
INSERT INTO system_role_permission VALUES (4, 112, '2026-07-21 08:42:30.060493');
INSERT INTO system_role_permission VALUES (5, 112, '2026-07-21 08:42:30.060493');
INSERT INTO system_role_permission VALUES (2, 154, '2026-08-01 16:24:58.217822');
INSERT INTO system_role_permission VALUES (2, 155, '2026-08-01 16:24:58.217822');
INSERT INTO system_role_permission VALUES (1, 150, '2026-08-01 16:24:58.217822');
INSERT INTO system_role_permission VALUES (1, 20, '2026-07-21 14:04:56.230529');
INSERT INTO system_role_permission VALUES (1, 21, '2026-07-21 14:04:56.230529');
INSERT INTO system_role_permission VALUES (1, 22, '2026-07-21 14:04:56.230529');
INSERT INTO system_role_permission VALUES (1, 64, '2026-07-21 14:04:56.230529');
INSERT INTO system_role_permission VALUES (1, 65, '2026-07-21 14:04:56.230529');
INSERT INTO system_role_permission VALUES (1, 23, '2026-07-21 14:04:56.230529');
INSERT INTO system_role_permission VALUES (1, 24, '2026-07-21 14:04:56.230529');
INSERT INTO system_role_permission VALUES (1, 25, '2026-07-21 14:04:56.230529');
INSERT INTO system_role_permission VALUES (1, 68, '2026-07-21 14:04:56.230529');
INSERT INTO system_role_permission VALUES (1, 69, '2026-07-21 14:04:56.230529');
INSERT INTO system_role_permission VALUES (1, 59, '2026-07-21 14:04:56.230529');
INSERT INTO system_role_permission VALUES (1, 70, '2026-07-21 14:04:56.230529');
INSERT INTO system_role_permission VALUES (1, 71, '2026-07-21 14:04:56.230529');
INSERT INTO system_role_permission VALUES (1, 116, '2026-07-21 14:04:56.230529');
INSERT INTO system_role_permission VALUES (1, 115, '2026-07-21 14:04:56.230529');
INSERT INTO system_role_permission VALUES (1, 26, '2026-07-21 14:04:56.230529');
INSERT INTO system_role_permission VALUES (1, 114, '2026-07-21 14:04:56.230529');
INSERT INTO system_role_permission VALUES (1, 27, '2026-07-21 14:04:56.230529');
INSERT INTO system_role_permission VALUES (1, 113, '2026-07-21 14:04:56.230529');
INSERT INTO system_role_permission VALUES (1, 28, '2026-07-21 14:04:56.230529');
INSERT INTO system_role_permission VALUES (1, 74, '2026-07-21 14:04:56.230529');
INSERT INTO system_role_permission VALUES (1, 75, '2026-07-21 14:04:56.230529');
INSERT INTO system_role_permission VALUES (1, 76, '2026-07-21 14:04:56.230529');
INSERT INTO system_role_permission VALUES (1, 108, '2026-07-21 14:04:56.230529');
INSERT INTO system_role_permission VALUES (1, 29, '2026-07-21 14:04:56.230529');
INSERT INTO system_role_permission VALUES (1, 30, '2026-07-21 14:04:56.230529');
INSERT INTO system_role_permission VALUES (1, 79, '2026-07-21 14:04:56.230529');
INSERT INTO system_role_permission VALUES (1, 80, '2026-07-21 14:04:56.230529');
INSERT INTO system_role_permission VALUES (1, 81, '2026-07-21 14:04:56.230529');
INSERT INTO system_role_permission VALUES (1, 32, '2026-07-21 14:04:56.230529');
INSERT INTO system_role_permission VALUES (1, 33, '2026-07-21 14:04:56.230529');
INSERT INTO system_role_permission VALUES (1, 34, '2026-07-21 14:04:56.230529');
INSERT INTO system_role_permission VALUES (1, 35, '2026-07-21 14:04:56.230529');
INSERT INTO system_role_permission VALUES (1, 84, '2026-07-21 14:04:56.230529');
INSERT INTO system_role_permission VALUES (1, 85, '2026-07-21 14:04:56.230529');
INSERT INTO system_role_permission VALUES (1, 86, '2026-07-21 14:04:56.230529');
INSERT INTO system_role_permission VALUES (1, 36, '2026-07-21 14:04:56.230529');
INSERT INTO system_role_permission VALUES (1, 37, '2026-07-21 14:04:56.230529');
INSERT INTO system_role_permission VALUES (1, 38, '2026-07-21 14:04:56.230529');
INSERT INTO system_role_permission VALUES (1, 39, '2026-07-21 14:04:56.230529');
INSERT INTO system_role_permission VALUES (1, 90, '2026-07-21 14:04:56.230529');
INSERT INTO system_role_permission VALUES (1, 112, '2026-07-21 14:04:56.230529');
INSERT INTO system_role_permission VALUES (1, 40, '2026-07-21 14:04:56.230529');
INSERT INTO system_role_permission VALUES (1, 41, '2026-07-21 14:04:56.230529');
INSERT INTO system_role_permission VALUES (1, 42, '2026-07-21 14:04:56.230529');
INSERT INTO system_role_permission VALUES (1, 43, '2026-07-21 14:04:56.230529');
INSERT INTO system_role_permission VALUES (1, 1, '2026-07-21 14:04:56.230529');
INSERT INTO system_role_permission VALUES (1, 2, '2026-07-21 14:04:56.230529');
INSERT INTO system_role_permission VALUES (1, 3, '2026-07-21 14:04:56.230529');
INSERT INTO system_role_permission VALUES (1, 4, '2026-07-21 14:04:56.230529');
INSERT INTO system_role_permission VALUES (1, 5, '2026-07-21 14:04:56.230529');
INSERT INTO system_role_permission VALUES (1, 6, '2026-07-21 14:04:56.230529');
INSERT INTO system_role_permission VALUES (1, 7, '2026-07-21 14:04:56.230529');
INSERT INTO system_role_permission VALUES (1, 8, '2026-07-21 14:04:56.230529');
INSERT INTO system_role_permission VALUES (1, 9, '2026-07-21 14:04:56.230529');
INSERT INTO system_role_permission VALUES (1, 10, '2026-07-21 14:04:56.230529');
INSERT INTO system_role_permission VALUES (1, 11, '2026-07-21 14:04:56.230529');
INSERT INTO system_role_permission VALUES (1, 12, '2026-07-21 14:04:56.230529');
INSERT INTO system_role_permission VALUES (1, 109, '2026-07-21 14:04:56.230529');
INSERT INTO system_role_permission VALUES (1, 13, '2026-07-21 14:04:56.230529');
INSERT INTO system_role_permission VALUES (1, 14, '2026-07-21 14:04:56.230529');
INSERT INTO system_role_permission VALUES (1, 15, '2026-07-21 14:04:56.230529');
INSERT INTO system_role_permission VALUES (1, 16, '2026-07-21 14:04:56.230529');
INSERT INTO system_role_permission VALUES (1, 17, '2026-07-21 14:04:56.230529');
INSERT INTO system_role_permission VALUES (1, 18, '2026-07-21 14:04:56.230529');
INSERT INTO system_role_permission VALUES (1, 19, '2026-07-21 14:04:56.230529');
INSERT INTO system_role_permission VALUES (1, 46, '2026-07-21 14:04:56.230529');
INSERT INTO system_role_permission VALUES (1, 47, '2026-07-21 14:04:56.230529');
INSERT INTO system_role_permission VALUES (1, 154, '2026-08-01 16:24:58.217822');
INSERT INTO system_role_permission VALUES (1, 155, '2026-08-01 16:24:58.217822');
INSERT INTO system_role_permission VALUES (3, 153, '2026-08-01 16:24:58.217822');
INSERT INTO system_role_permission VALUES (2, 153, '2026-08-01 16:24:58.217822');
INSERT INTO system_role_permission VALUES (1, 153, '2026-08-01 16:24:58.217822');
INSERT INTO system_role_permission VALUES (3, 151, '2026-08-01 16:24:58.217822');
INSERT INTO system_role_permission VALUES (3, 152, '2026-08-01 16:24:58.217822');
INSERT INTO system_role_permission VALUES (1, 151, '2026-08-01 16:24:58.217822');
INSERT INTO system_role_permission VALUES (1, 152, '2026-08-01 16:24:58.217822');
INSERT INTO system_role_permission VALUES (1, 157, '2026-08-03 10:53:19.365404');
INSERT INTO system_role_permission VALUES (1, 158, '2026-08-03 10:53:19.365404');
INSERT INTO system_role_permission VALUES (3, 159, '2026-08-05 09:40:39.761693');
INSERT INTO system_role_permission VALUES (3, 161, '2026-08-05 09:40:39.761693');
INSERT INTO system_role_permission VALUES (7, 159, '2026-08-05 09:40:39.761693');
INSERT INTO system_role_permission VALUES (7, 161, '2026-08-05 09:40:39.761693');
INSERT INTO system_role_permission VALUES (2, 159, '2026-08-05 09:40:39.761693');
INSERT INTO system_role_permission VALUES (2, 161, '2026-08-05 09:40:39.761693');
INSERT INTO system_role_permission VALUES (1, 159, '2026-08-05 09:40:39.761693');
INSERT INTO system_role_permission VALUES (1, 161, '2026-08-05 09:40:39.761693');
INSERT INTO system_role_permission VALUES (3, 160, '2026-08-05 09:40:39.761693');
INSERT INTO system_role_permission VALUES (2, 160, '2026-08-05 09:40:39.761693');
INSERT INTO system_role_permission VALUES (1, 160, '2026-08-05 09:40:39.761693');
INSERT INTO system_role_permission VALUES (1, 162, '2026-08-11 18:29:24.080487');

--
-- Data for Name: system_user; Type: TABLE DATA; Schema: public; Owner: -
--

INSERT INTO system_user VALUES (1, 3, 'engineer', '$2a$12$KbOZcqCA0n1wojKWha1lwOOjD.eNCWGEm4DatYg46qcl3IEn6hzaS', '工程人员', NULL, NULL, NULL, 3, 3, 'ENGINEER', true, true, 0, NULL, NULL, NULL, NULL, NULL, '2026-08-10 10:07:59.996993', '2026-08-10 10:07:59.996993', false);
INSERT INTO system_user VALUES (2, 2, 'market', '$2a$12$N4luOAukB3cz.gKFs7WicOBlULM8Z0Ik62yOtcllvkMN6J1a77b3C', '市场人员', NULL, NULL, NULL, 2, 1, 'MARKET', true, false, 0, NULL, NULL, NULL, NULL, NULL, '2026-08-10 10:07:59.996993', '2026-08-10 10:07:59.996993', false);
INSERT INTO system_user VALUES (3, 1, 'admin', '$2a$12$g3DNmPIxz.Krv.UGXo/IveIWizF3YDJF/TQKPfgUzGRwIPMFk07Oa', '管理员', NULL, NULL, NULL, 4, 5, 'ADMIN', true, false, 0, NULL, NULL, NULL, NULL, NULL, '2026-08-10 10:07:59.996993', '2026-08-10 10:07:59.996993', false);

--
-- Data for Name: system_user_role; Type: TABLE DATA; Schema: public; Owner: -
--

INSERT INTO system_user_role VALUES (1, 4, '2026-08-10 10:07:59.996993');
INSERT INTO system_user_role VALUES (2, 2, '2026-08-10 10:07:59.996993');
INSERT INTO system_user_role VALUES (3, 1, '2026-08-10 10:07:59.996993');

--
-- Name: dict_assembly_markup_fee_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('dict_assembly_markup_fee_id_seq', 3306, true);

--
-- Name: dict_extra_fee_item_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('dict_extra_fee_item_id_seq', 10, true);

--
-- Name: dict_markup_fee_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('dict_markup_fee_id_seq', 290, true);

--
-- Name: dict_material_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('dict_material_id_seq', 1, false);

--
-- Name: dict_material_type_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('dict_material_type_id_seq', 1, true);

--
-- Name: dict_process_category_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('dict_process_category_id_seq', 1, false);

--
-- Name: dict_process_detail_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('dict_process_detail_id_seq', 1, false);

--
-- Name: erp_user_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('erp_user_id_seq', 3, true);

--
-- Name: system_data_center_endpoint_config_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('system_data_center_endpoint_config_id_seq', 5, true);

--
-- Name: system_dept_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('system_dept_id_seq', 4, true);

--
-- Name: system_file_storage_config_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('system_file_storage_config_id_seq', 234, true);

--
-- Name: system_inbound_callback_config_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('system_inbound_callback_config_id_seq', 1, true);

--
-- Name: system_permission_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('system_permission_id_seq', 162, true);

--
-- Name: system_post_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('system_post_id_seq', 5, true);

--
-- Name: system_role_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('system_role_id_seq', 1198, true);

--
-- Name: system_user_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('system_user_id_seq', 346, true);

--
-- PostgreSQL database dump complete
--
