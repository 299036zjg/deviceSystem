-- =====================================================================
-- 设备维修保养系统 演示数据脚本
-- 初始账号（首次部署后请立即修改密码）：
--   admin / admin123     管理员   张伟   设备科
--   tech1 / tech123      维修人员 李强   维修部
--   tech2 / tech123      维修人员 王磊   维修部
--   tech3 / tech123      维修人员 孙浩   维修部
--   rep1  / rep12345     报修人   陈静   生产一部
--   rep2  / rep12345     报修人   赵敏   生产二部
-- 说明：脚本使用固定主键 + INSERT IGNORE，可重复执行，不会产生重复数据
-- =====================================================================

-- 用户（密码为 PBKDF2-SHA256 带盐摘要）
INSERT IGNORE INTO `user` (user_id, account, password_hash, name, department, role, enabled, created_at) VALUES
(1, 'admin', 'pbkdf2_sha256$120000$duhEfD75ZcXTElglhPTa2w==$w+h/8w/FgUiG0SjwaI3Os5f4zLjG5zx+vh40M5IchPE=', '张伟', '设备科', 'ADMIN', 1, DATE_SUB(NOW(), INTERVAL 60 DAY)),
(2, 'tech1', 'pbkdf2_sha256$120000$MvBw2xLjWrMYbNa/W94zBQ==$u13BNYYJmv/0M4uhxCg5mCsWQCWGlalh/73djz1QtIc=', '李强', '维修部', 'TECHNICIAN', 1, DATE_SUB(NOW(), INTERVAL 60 DAY)),
(3, 'tech2', 'pbkdf2_sha256$120000$DbhspeNvoII9I3Dv+PiQ/Q==$oIsPzyWIXS6SckCAI37vlrGm1CpN/qtpc30g/bMrDBs=', '王磊', '维修部', 'TECHNICIAN', 1, DATE_SUB(NOW(), INTERVAL 60 DAY)),
(4, 'rep1', 'pbkdf2_sha256$120000$THBxbDz7gOBjxWZ3dyKCng==$9De75K0dR5HP+L71HLVvbjZcMumGAuK7urfEAcn0gOE=', '陈静', '生产一部', 'REPORTER', 1, DATE_SUB(NOW(), INTERVAL 60 DAY)),
(5, 'rep2', 'pbkdf2_sha256$120000$NUy/BP8ZyJIiv6T8XActHw==$GmQM4GxAMnlO5WZPOHNbU2ooFpMSs+KSgdWRkftmDNM=', '赵敏', '生产二部', 'REPORTER', 1, DATE_SUB(NOW(), INTERVAL 60 DAY)),
(6, 'tech3', 'pbkdf2_sha256$120000$Ffo9vwglG4pf6ObP3G5csw==$kKVTkKRMwqkDNux/yILAZUCQt3ZdtAiLwggheQS71ik=', '孙浩', '维修部', 'TECHNICIAN', 1, DATE_SUB(NOW(), INTERVAL 60 DAY));

-- 设备（20 台，覆盖 4 个部门，二维码内容为 EQ:<设备编号>）
INSERT IGNORE INTO equipment (equipment_id, equipment_code, equipment_name, category, model, department, location, enabled, created_at) VALUES
(1,  'SB-001', '数控车床',       '机加工设备', 'CK6140',   '生产一部', '一号车间 A 区', 1, DATE_SUB(NOW(), INTERVAL 50 DAY)),
(2,  'SB-002', '注塑机',         '成型设备',   'HTF160',   '生产一部', '一号车间 B 区', 1, DATE_SUB(NOW(), INTERVAL 50 DAY)),
(3,  'SB-003', '空气压缩机',     '动力设备',   'GA37',     '生产一部', '一号车间动力间', 1, DATE_SUB(NOW(), INTERVAL 50 DAY)),
(4,  'SB-004', '激光切割机',     '机加工设备', 'LF3015',   '生产一部', '一号车间 C 区', 1, DATE_SUB(NOW(), INTERVAL 50 DAY)),
(5,  'SB-005', '装配线输送带',   '输送设备',   'XL-200',   '生产二部', '二号车间流水线 1', 1, DATE_SUB(NOW(), INTERVAL 50 DAY)),
(6,  'SB-006', '焊接机器人',     '自动化设备', 'KR16',     '生产二部', '二号车间焊接工位', 1, DATE_SUB(NOW(), INTERVAL 50 DAY)),
(7,  'SB-007', '冲压机',         '成型设备',   'J23-63',   '生产二部', '二号车间 D 区', 1, DATE_SUB(NOW(), INTERVAL 50 DAY)),
(8,  'SB-008', '三坐标测量仪',   '检测设备',   'Global S', '质检部',   '质检实验室', 1, DATE_SUB(NOW(), INTERVAL 50 DAY)),
(9,  'SB-009', '压力检测台',     '检测设备',   'YL-500',   '质检部',   '质检实验室', 1, DATE_SUB(NOW(), INTERVAL 50 DAY)),
(10, 'SB-010', '工业烘箱',       '热处理设备', 'HG-101',   '生产一部', '一号车间 D 区', 1, DATE_SUB(NOW(), INTERVAL 50 DAY)),
(11, 'SB-011', '抛光机',         '表面处理',   'PG-300',   '生产二部', '二号车间 E 区', 1, DATE_SUB(NOW(), INTERVAL 50 DAY)),
(12, 'SB-012', '包装机',         '包装设备',   'BZ-50',    '生产二部', '二号车间包装线', 1, DATE_SUB(NOW(), INTERVAL 50 DAY)),
(13, 'SB-013', '电力变压器',     '动力设备',   'S11-500',  '动力车间', '配电房', 1, DATE_SUB(NOW(), INTERVAL 50 DAY)),
(14, 'SB-014', '冷却塔',         '动力设备',   'LBC-100',  '动力车间', '厂区屋面', 1, DATE_SUB(NOW(), INTERVAL 50 DAY)),
(15, 'SB-015', '空气处理机组',   '暖通设备',   'AHU-30',   '动力车间', '中央空调机房', 1, DATE_SUB(NOW(), INTERVAL 50 DAY)),
(16, 'SB-016', '水泵机组',       '动力设备',   'IS100-80', '动力车间', '水泵房', 1, DATE_SUB(NOW(), INTERVAL 50 DAY)),
(17, 'SB-017', '除尘设备',       '环保设备',   'CC-200',   '生产一部', '一号车间除尘间', 1, DATE_SUB(NOW(), INTERVAL 50 DAY)),
(18, 'SB-018', '叉车充电桩',     '辅助设备',   'CD-48',    '生产二部', '二号车间物流区', 1, DATE_SUB(NOW(), INTERVAL 50 DAY)),
(19, 'SB-019', '数控铣床',       '机加工设备', 'XK714',    '生产一部', '一号车间 A 区', 1, DATE_SUB(NOW(), INTERVAL 50 DAY)),
(20, 'SB-020', '空压站干燥机',   '动力设备',   'RD-60',    '动力车间', '空压站', 1, DATE_SUB(NOW(), INTERVAL 50 DAY));

-- 报修单（1 张已完成、1 张已受理、1 张待受理）
INSERT IGNORE INTO repair_request (request_id, request_no, equipment_id, reporter_id, description, fault_type, urgency, discovered_at, submitted_at, status, decision_reason, decided_by, decided_at, submit_token, source) VALUES
(1, 'BX20260001', 1, 4, '车床主轴异响，加工表面出现振纹，怀疑主轴轴承磨损。', '机械故障', '较高', DATE_SUB(NOW(), INTERVAL 12 DAY), DATE_SUB(NOW(), INTERVAL 12 DAY), '已完成', NULL, 1, DATE_SUB(NOW(), INTERVAL 11 DAY), 'demo-token-0001', 'WEB'),
(2, 'BX20260002', 5, 5, '输送带在运行中频繁跑偏，物料堆积在皮带侧面，需现场调整张紧装置。', '机械故障', '一般', DATE_SUB(NOW(), INTERVAL 2 DAY), DATE_SUB(NOW(), INTERVAL 2 DAY), '已受理', NULL, 1, DATE_SUB(NOW(), INTERVAL 2 DAY), 'demo-token-0002', 'MINI'),
(3, 'BX20260003', 3, 4, '空压机压力上不去，压力表停在 0.5MPa，运行声音变大。', '机械故障', '紧急', DATE_SUB(NOW(), INTERVAL 3 HOUR), DATE_SUB(NOW(), INTERVAL 3 HOUR), '待受理', NULL, NULL, NULL, 'demo-token-0003', 'MINI');

-- 工单（维修 ×2、保养 ×2）
INSERT IGNORE INTO work_order (order_id, order_no, order_type, equipment_id, owner_id, request_id, task_content, planned_date, status, total_cost, created_at, started_at, completed_at, create_token) VALUES
(1, 'GD20260001', 'REPAIR',      1, 2, 1,    '车床主轴异响，加工表面出现振纹，怀疑主轴轴承磨损。', DATE_SUB(CURDATE(), INTERVAL 11 DAY), '已完成', 850.00, DATE_SUB(NOW(), INTERVAL 11 DAY), DATE_SUB(NOW(), INTERVAL 11 DAY), DATE_SUB(NOW(), INTERVAL 10 DAY), NULL),
(2, 'GD20260002', 'REPAIR',      5, 3, 2,    '输送带在运行中频繁跑偏，物料堆积在皮带侧面，需现场调整张紧装置。', DATE_SUB(CURDATE(), INTERVAL 1 DAY), '处理中', 0.00, DATE_SUB(NOW(), INTERVAL 2 DAY), DATE_SUB(NOW(), INTERVAL 1 DAY), NULL, NULL),
(3, 'GD20260003', 'MAINTENANCE', 8, 2, NULL, '三坐标测量仪季度保养：清洁导轨、更换测头润滑油、校验重复定位精度。', DATE_SUB(CURDATE(), INTERVAL 18 DAY), '已完成', 300.00, DATE_SUB(NOW(), INTERVAL 20 DAY), DATE_SUB(NOW(), INTERVAL 18 DAY), DATE_SUB(NOW(), INTERVAL 18 DAY), 'demo-maintain-0001'),
(4, 'GD20260004', 'MAINTENANCE', 13, 3, NULL, '配电房变压器月度巡检：检查油温、紧固接线端子、清理通风口灰尘。', DATE_SUB(CURDATE(), INTERVAL 5 DAY), '待处理', 0.00, DATE_SUB(NOW(), INTERVAL 6 DAY), NULL, NULL, 'demo-maintain-0002');

-- 处理记录
INSERT IGNORE INTO work_record (record_id, order_id, operator_id, cause, process, result, replaced_parts, total_cost_snapshot, submitted_at) VALUES
(1, 1, 2, '主轴前轴承滚道点蚀，润滑脂干涸。', '拆解主轴箱，清洗轴承座，更换前轴承并重新加注润滑脂，空运转 2 小时无异响。', '主轴异响消除，试加工 10 件产品表面粗糙度合格。', '7014C 角接触轴承 ×2', 850.00, DATE_SUB(NOW(), INTERVAL 10 DAY)),
(2, 3, 2, '导轨润滑不足，测头油膜老化。', '清洁三坐标导轨与气浮面，更换测头润滑油，按标准件校验重复定位精度。', '季度保养完成，重复定位精度 0.002mm，满足要求。', NULL, 300.00, DATE_SUB(NOW(), INTERVAL 18 DAY));

-- 验收记录
INSERT IGNORE INTO acceptance (acceptance_id, order_id, inspector_id, result, opinion, accepted_at) VALUES
(1, 1, 1, 'PASS', '异响已消除，试加工件抽检合格，验收通过。', DATE_SUB(NOW(), INTERVAL 10 DAY)),
(2, 3, 1, 'PASS', '保养项目齐全，精度校验记录完整。', DATE_SUB(NOW(), INTERVAL 18 DAY));
