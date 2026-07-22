-- Formal baseline data generated from 材料与工艺费用表 on 2026-05-19.
-- This migration replaces only the old built-in sample dictionaries when they are still untouched, then upserts the formal dictionaries.

WITH stats AS (SELECT COUNT(*) AS c FROM dict_material)
DELETE FROM dict_material USING stats WHERE stats.c <= 4;
WITH stats AS (SELECT COUNT(*) AS c FROM dict_process)
DELETE FROM dict_process USING stats WHERE stats.c <= 3;
WITH stats AS (SELECT COUNT(*) AS c FROM dict_markup_fee)
DELETE FROM dict_markup_fee USING stats WHERE stats.c <= 4;
WITH stats AS (SELECT COUNT(*) AS c FROM dict_extra_fee_item)
DELETE FROM dict_extra_fee_item USING stats WHERE stats.c <= 5;

INSERT INTO dict_material (material_name, material_type, remarks, density, plate_density, plate_price, bar_density, bar_price, tube_density, tube_price)
VALUES
    ('45#', '钢铁', '', 7.83, 7.83, 7.1, 7.85, 10, 7.85, 10),
    ('Q235', '钢铁', '', 7.85, 7.85, 5, 7.85, 4.8, 7.85, 4.8),
    ('A3', '钢铁', '', 7.85, 7.85, 5, 7.85, 4.8, 7.85, 4.8),
    ('SKD11', '钢铁', '(抚顺D2)', 7.85, 7.85, 31, 7.85, 31, 7.85, 31),
    ('SKD61', '钢铁', '(抚顺电渣）', 7.85, 7.85, 36, 7.85, 36, 7.85, 36),
    ('GCR15', '钢铁', '', 7.85, 7.85, 80, 7.85, 18, 7.85, 18),
    ('15CRMO', '钢铁', '', 7.85, 7.85, 24.5, 7.85, 12, 7.85, 12),
    ('35CRMO', '钢铁', '', 7.85, 7.85, 16, 7.85, 16, 7.85, 16),
    ('38CRMOV', '钢铁', '', 7.85, 7.85, 16, 7.85, 16, 7.85, 16),
    ('42CRMO', '钢铁', '', 7.85, 7.85, 16, 7.85, 36, 7.85, 36),
    ('T11', '钢铁', '', 7.85, 7.85, 35, 7.85, 175, 7.85, 175),
    ('SK3', '钢铁', '', 7.85, 7.85, 35, 7.85, 175, 7.85, 175),
    ('W18CR4V', '钢铁', '(高速钢）', 7.85, 7.85, 110, 7.85, 110, 7.85, 110),
    ('SKH55', '钢铁', '(高速钢）', 7.85, 7.85, 158, 7.85, 136, 7.85, 136),
    ('SKH59', '钢铁', '(高速钢）', 7.85, 7.85, 158, 7.85, 158, 7.85, 158),
    ('440C', '钢铁', '(抚顺电渣）', 7.85, 7.85, 55, 7.85, 55, 7.85, 55),
    ('9MV2V', '钢铁', '', 7.85, 7.85, 24, 7.85, 24.5, 7.85, 24.5),
    ('9CRWMN', '钢铁', '', 7.85, 7.85, 22, 7.85, 24.5, 7.85, 24.5),
    ('4CR13', '钢铁', '(S136大连电炉）BAM', 7.9, 7.9, 17, 7.85, 18, 7.85, 18),
    ('4CR13', '钢铁', '(S136抚顺电渣）', 7.85, 7.85, 16, 7.85, 55, 7.85, 55),
    ('4CR13', '钢铁', '(S136 BQD订单批量）', 7.85, 7.85, 24.75, 7.85, 25, 7.85, 25),
    ('SUS303', '不锈钢', '', 7.9, 7.9, 26, 7.9, 22.5, 7.9, 22.5),
    ('SUS304', '不锈钢', '', 7.9, 7.9, 26, 7.9, 22.5, 7.9, 22.5),
    ('SUS316', '不锈钢', '', 7.9, 7.9, 26, 7.9, 22.5, 7.9, 22.5),
    ('SUS316L', '不锈钢', '', 7.9, 7.9, 26, 7.9, 22.5, 7.9, 22.5),
    ('SUS431', '不锈钢', '', 7.6, 7.6, 20, 7.9, 38, 7.9, 38),
    ('SUS430', '不锈钢', '', 7.6, 7.6, 20, 7.9, 38, 7.9, 38),
    ('KG4', '合金', '(硬质合金)', 13, 13, 680, 13, 530, 13, 530),
    ('SALY', '合金', '(日本硬质合金）', 15, 15, 920, 15, 0, 15, 0),
    ('纯钨', '合金', '', 19.6, 19.6, 1200, 19.6, 0, 19.6, 0),
    ('黄铜', '铜', '', 8.4, 8.4, 21, 8.7, 50, 8.7, 50),
    ('紫铜', '铜', '', 11, 11, 50, 9, 120, 9, 120),
    ('YH52', '铝', '(上海白铜）', 2.7, 2.7, 49, 2.7, 71, 2.7, 71),
    ('A5052', '铝', '(上海白铜）', 2.7, 2.7, 49, 2.7, NULL, 2.7, NULL),
    ('A6061', '铝', '', 2.8, 2.8, 65, 2.8, 22.8, 2.8, 22.8),
    ('A7075', '铝', '', 2.8, 2.8, 20, 2.8, 22.8, 2.8, 22.8),
    ('A5052', '铝', '', 2.8, 2.8, 20, 2.8, 22.8, 2.8, 22.8),
    ('A5083', '铝', '', 2.75, 2.75, 28, 2.75, 67, 2.75, 67),
    ('A6082', '铝', '', 2.75, 2.75, 28, 2.75, 67, 2.75, 67),
    ('A2024', '铝', '', 2.75, 2.75, 28, 2.75, 67, 2.75, 67),
    ('PEEK', '塑料', '', 1.5, 1.5, 971, 1.5, 1400, 1.5, 1400),
    ('尼龙', '塑料', '', 1.2, 1.2, 132, 1.15, 42, 1.15, 42),
    ('POM', '塑料', '', 1.6, 1.6, 21, 1.38, 16, 1.38, 16),
    ('PTFE', '塑料', '', 2.3, 2.3, 28, 2.3, 80, 2.3, 80)
ON CONFLICT (material_name, remarks) DO UPDATE SET
    material_type = EXCLUDED.material_type,
    density = EXCLUDED.density,
    plate_density = EXCLUDED.plate_density,
    plate_price = EXCLUDED.plate_price,
    bar_density = EXCLUDED.bar_density,
    bar_price = EXCLUDED.bar_price,
    tube_density = EXCLUDED.tube_density,
    tube_price = EXCLUDED.tube_price;

INSERT INTO dict_process (process_name, calc_type, sort_no, price_1_5, price_5_10, price_10_50, price_50_100, price_100_500, price_500_1000, price_1000_3000, price_3000_plus)
VALUES
    ('铣', '工时', 1, 70, 70, 70, 70, 70, 70, 70, 70),
    ('CNC1', '工时', 2, 120, 120, 120, 120, 120, 120, 120, 120),
    ('CNC-5', '工时', 3, 300, 300, 300, 300, 300, 300, 300, 300),
    ('CNC-D', '工时', 4, 300, 300, 300, 300, 300, 300, 300, 300),
    ('车', '工时', 5, 70, 70, 70, 70, 70, 70, 70, 70),
    ('CNC车', '工时', 6, 120, 120, 120, 120, 120, 120, 120, 120),
    ('平磨', '工时', 7, 80, 80, 80, 80, 80, 80, 80, 80),
    ('圆磨', '工时', 8, 100, 100, 100, 100, 100, 100, 100, 100),
    ('大水磨', '工时', 9, 120, 120, 120, 120, 120, 120, 120, 120),
    ('W/C快', '工时', 10, 60, 60, 60, 60, 60, 60, 60, 60),
    ('W/C慢', '工时', 11, 150, 150, 150, 150, 150, 150, 150, 150),
    ('W/C中', '工时', 12, 80, 80, 80, 80, 80, 80, 80, 80),
    ('国产EDM', '工时', 13, 120, 120, 120, 120, 120, 120, 120, 120),
    ('攻丝机', '工时', 14, 50, 50, 50, 50, 50, 50, 50, 50),
    ('钳', '工时', 15, 50, 50, 50, 50, 50, 50, 50, 50),
    ('抛光', '工时', 16, 60, 60, 60, 60, 60, 60, 60, 60),
    ('清洗', '工时', 17, 50, 50, 50, 50, 50, 50, 50, 50),
    ('QA', '工时', 18, 80, 80, 80, 80, 80, 80, 80, 80),
    ('下料', '工时', 19, 50, 50, 50, 50, 50, 50, 50, 50),
    ('无心磨', '工时', 20, 80, 80, 80, 80, 80, 80, 80, 80),
    ('走芯机', '工时', 21, 80, 80, 80, 80, 80, 80, 80, 80),
    ('EDD', '工时', 22, 120, 120, 120, 120, 120, 120, 120, 120)
ON CONFLICT (process_name) DO UPDATE SET
    calc_type = '工时',
    sort_no = EXCLUDED.sort_no,
    price_1_5 = EXCLUDED.price_1_5,
    price_5_10 = EXCLUDED.price_5_10,
    price_10_50 = EXCLUDED.price_10_50,
    price_50_100 = EXCLUDED.price_50_100,
    price_100_500 = EXCLUDED.price_100_500,
    price_500_1000 = EXCLUDED.price_500_1000,
    price_1000_3000 = EXCLUDED.price_1000_3000,
    price_3000_plus = EXCLUDED.price_3000_plus;

INSERT INTO dict_markup_fee (fee_name, rate_percent, sort_no, enabled)
VALUES
    ('运输费', 5.00, 1, TRUE),
    ('管理费', 5.00, 2, TRUE),
    ('利润', 15.00, 3, TRUE),
    ('税费', 13.00, 4, TRUE)
ON CONFLICT (fee_name) DO UPDATE SET
    rate_percent = EXCLUDED.rate_percent,
    sort_no = EXCLUDED.sort_no,
    enabled = EXCLUDED.enabled;

INSERT INTO dict_extra_fee_item (fee_name, sort_no, enabled, remarks)
VALUES
    ('EDM', 1, TRUE, ''),
    ('热处理', 2, TRUE, ''),
    ('表面处理', 3, TRUE, ''),
    ('QA', 4, TRUE, ''),
    ('焊钳工\焊缝处理\测漏\校平', 5, TRUE, '')
ON CONFLICT (fee_name) DO UPDATE SET
    sort_no = EXCLUDED.sort_no,
    enabled = EXCLUDED.enabled,
    remarks = EXCLUDED.remarks;
