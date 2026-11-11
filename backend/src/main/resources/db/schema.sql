-- =====================================================================
-- 设备维修保养系统 建表脚本（MySQL 8.0+ / utf8mb4）
-- 对应设计文档 4.1 ~ 4.8 节
-- 脚本可重复执行（CREATE TABLE IF NOT EXISTS），不会破坏已有数据
-- =====================================================================

-- 4.1 用户表
CREATE TABLE IF NOT EXISTS `user` (
    user_id       BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    account       VARCHAR(32)  NOT NULL COMMENT '登录账号：字母、数字或下划线',
    password_hash VARCHAR(255) NOT NULL COMMENT '带盐密码摘要（PBKDF2-SHA256）',
    name          VARCHAR(50)  NOT NULL COMMENT '姓名',
    department    VARCHAR(50)  NOT NULL COMMENT '所属部门',
    role          VARCHAR(16)  NOT NULL COMMENT 'ADMIN / REPORTER / TECHNICIAN',
    enabled       TINYINT(1)   NOT NULL DEFAULT 1 COMMENT '是否启用',
    must_change_password TINYINT(1) NOT NULL DEFAULT 0 COMMENT '管理员重置密码后为 1，用户改密后置 0',
    created_at    DATETIME     NOT NULL COMMENT '创建时间',
    PRIMARY KEY (user_id),
    UNIQUE KEY uk_user_account (account)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '用户表';

-- 4.2 设备表
CREATE TABLE IF NOT EXISTS equipment (
    equipment_id   BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    equipment_code VARCHAR(32)  NOT NULL COMMENT '唯一设备编号，二维码内容 EQ:<编号>',
    equipment_name VARCHAR(100) NOT NULL COMMENT '设备名称',
    category       VARCHAR(50)  NULL COMMENT '分类',
    model          VARCHAR(100) NULL COMMENT '型号',
    department     VARCHAR(50)  NOT NULL COMMENT '所属部门（报修权限匹配依据）',
    location       VARCHAR(100) NOT NULL COMMENT '使用位置',
    enabled        TINYINT(1)   NOT NULL DEFAULT 1 COMMENT '是否启用',
    created_at     DATETIME     NOT NULL COMMENT '创建时间',
    PRIMARY KEY (equipment_id),
    UNIQUE KEY uk_equipment_code (equipment_code),
    KEY idx_equipment_department (department, enabled)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '设备表';

-- 4.3 报修表
CREATE TABLE IF NOT EXISTS repair_request (
    request_id      BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    request_no      VARCHAR(32)   NOT NULL COMMENT '唯一报修编号',
    equipment_id    BIGINT        NOT NULL COMMENT '设备外键',
    reporter_id     BIGINT        NOT NULL COMMENT '报修人外键',
    description     VARCHAR(1000) NOT NULL COMMENT '故障描述：1~1000 字符',
    fault_type      VARCHAR(32)   NULL COMMENT '故障类型（EM011）',
    urgency         VARCHAR(16)   NULL COMMENT '紧急程度（EM011）',
    discovered_at   DATETIME      NOT NULL COMMENT '发现时间，不得晚于提交时间',
    submitted_at    DATETIME      NOT NULL COMMENT '提交时间（服务器记录）',
    status          VARCHAR(16)   NOT NULL COMMENT '待受理/已驳回/已受理/已完成',
    decision_reason VARCHAR(500)  NULL COMMENT '驳回原因，驳回时必填',
    decided_by      BIGINT        NULL COMMENT '受理或驳回管理员',
    decided_at      DATETIME      NULL COMMENT '受理或驳回时间',
    submit_token    VARCHAR(64)   NOT NULL COMMENT '一次性提交标识，与报修人联合唯一',
    source          VARCHAR(12)   NOT NULL COMMENT '提交终端：WEB / MINI',
    PRIMARY KEY (request_id),
    UNIQUE KEY uk_repair_request_no (request_no),
    UNIQUE KEY uk_repair_submit (reporter_id, submit_token),
    KEY idx_repair_reporter (reporter_id, status),
    KEY idx_repair_equipment (equipment_id, status),
    KEY idx_repair_status_time (status, submitted_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '报修表';

-- 4.4 工单表
CREATE TABLE IF NOT EXISTS work_order (
    order_id      BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    order_no      VARCHAR(32)   NOT NULL COMMENT '唯一工单编号',
    order_type    VARCHAR(12)   NOT NULL COMMENT 'REPAIR / MAINTENANCE',
    equipment_id  BIGINT        NOT NULL COMMENT '设备外键',
    owner_id      BIGINT        NOT NULL COMMENT '维修负责人外键',
    request_id    BIGINT        NULL COMMENT '来源报修：维修必填且唯一，保养为空',
    task_content  VARCHAR(1000) NOT NULL COMMENT '故障描述或保养内容',
    planned_date  DATE          NOT NULL COMMENT '计划处理或保养日期',
    status        VARCHAR(16)   NOT NULL COMMENT '待处理/处理中/待验收/已完成',
    total_cost    DECIMAL(10,2) NOT NULL DEFAULT 0.00 COMMENT '当前总费用，非负，上限 99999999.99',
    created_at    DATETIME      NOT NULL COMMENT '创建时间',
    started_at    DATETIME      NULL COMMENT '开工时间',
    completed_at  DATETIME      NULL COMMENT '验收通过时间',
    create_token  VARCHAR(64)   NULL COMMENT '保养创建一次性标识，唯一',
    PRIMARY KEY (order_id),
    UNIQUE KEY uk_work_order_no (order_no),
    UNIQUE KEY uk_work_order_request (request_id),
    UNIQUE KEY uk_work_order_create_token (create_token),
    KEY idx_work_order_owner (owner_id, status),
    KEY idx_work_order_equipment (equipment_id, status),
    KEY idx_work_order_completed (status, completed_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '工单表';

-- 4.5 处理记录表
CREATE TABLE IF NOT EXISTS work_record (
    record_id           BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    order_id            BIGINT        NOT NULL COMMENT '工单外键',
    operator_id         BIGINT        NOT NULL COMMENT '本次提交负责人外键',
    cause               VARCHAR(500)  NULL COMMENT '故障原因（维修）',
    process             VARCHAR(1000) NULL COMMENT '维修过程（维修）',
    result              VARCHAR(1500) NOT NULL COMMENT '处理结果 / 保养完成内容',
    replaced_parts      VARCHAR(500)  NULL COMMENT '更换零部件说明',
    total_cost_snapshot DECIMAL(10,2) NOT NULL COMMENT '提交时的本单总费用',
    submitted_at        DATETIME      NOT NULL COMMENT '服务器提交时间',
    PRIMARY KEY (record_id),
    KEY idx_work_record_order (order_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '处理记录表';

-- 4.6 验收记录表
CREATE TABLE IF NOT EXISTS acceptance (
    acceptance_id BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    order_id      BIGINT       NOT NULL COMMENT '工单外键',
    inspector_id  BIGINT       NOT NULL COMMENT '管理员用户外键',
    result        VARCHAR(10)  NOT NULL COMMENT 'PASS / RETURN',
    opinion       VARCHAR(500) NULL COMMENT '验收意见，退回时必填',
    accepted_at   DATETIME     NOT NULL COMMENT '服务器验收时间',
    PRIMARY KEY (acceptance_id),
    KEY idx_acceptance_order (order_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '验收记录表';

-- 4.7 图片附件表
CREATE TABLE IF NOT EXISTS file_attachment (
    attachment_id BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    biz_type      VARCHAR(12)  NOT NULL COMMENT 'REPAIR 故障照片 / WORK 完工照片',
    request_id    BIGINT       NULL COMMENT '报修单外键，REPAIR 且已关联时必填',
    record_id     BIGINT       NULL COMMENT '处理记录外键，WORK 且已关联时必填',
    file_name     VARCHAR(255) NOT NULL COMMENT '原始文件名，仅用于展示',
    file_path     VARCHAR(255) NOT NULL COMMENT '相对存储路径',
    content_type  VARCHAR(50)  NOT NULL COMMENT 'image/jpeg 或 image/png',
    file_size     BIGINT       NOT NULL COMMENT '字节数，不超过 5242880',
    uploader_id   BIGINT       NOT NULL COMMENT '上传人外键',
    uploaded_at   DATETIME     NOT NULL COMMENT '上传时间',
    sort_no       INT          NOT NULL COMMENT '展示顺序，从 1 开始',
    PRIMARY KEY (attachment_id),
    KEY idx_attachment_repair (biz_type, request_id),
    KEY idx_attachment_record (record_id),
    KEY idx_attachment_unbound (uploaded_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '图片附件表';
