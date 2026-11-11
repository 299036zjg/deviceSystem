# 需求与代码对照表

本文件说明设计文档中的用例、业务规则、数据表与接口分别落在哪些代码里，便于答辩时定位。

## 1. 用例对照（EM001 ~ EM014）

| 编号 | 后端实现 | Web 后台 | 小程序 |
| --- | --- | --- | --- |
| EM001 账号与权限 | `service/AuthService`（login/logout/profile/changePassword）、`service/UserService`（create/setEnabled/resetPassword）、`security/PasswordHasher`、`security/SessionStore` | `views/UserView.vue`、`views/ProfileView.vue`、`layout/AppLayout.vue`（顶部改密/退出） | `pages/login`、`pages/profile` |
| EM002 设备台账 | `service/EquipmentService`（create/update/setEnabled/history） | `views/EquipmentView.vue`（列表、编辑、停用、履历抽屉） | `pages/scan`（设备信息展示） |
| EM003 提交报修 | `service/RepairService#create` | `views/RepairCreateView.vue` | `pages/repair/create` |
| EM004 受理派工 | `service/RepairService#accept`、`#reject`；`repo/RepairRequestRepository#decide/decideWithReason` | `views/RepairListView.vue`（受理/驳回弹窗）、`views/RepairDetailView.vue` | 只读查看（`pages/repair/detail`） |
| EM005 执行任务 | `service/WorkOrderService#start`、`#submit`；`repo/WorkOrderRepository#start/submit` | `views/OrderDetailView.vue` | `pages/order/detail` |
| EM006 验收工单 | `service/WorkOrderService#acceptance`、`#pendingAcceptances`；`repo/WorkOrderRepository#complete`、`#returnToProcessing`、`repo/RepairRequestRepository#complete` | `views/AcceptanceView.vue`、`views/OrderDetailView.vue` | `pages/order/detail`（查看验收意见） |
| EM007 安排保养 | `service/WorkOrderService#createMaintenance` | `views/OrderListView.vue`（安排保养弹窗） | `pages/order/list`（保养任务同流程） |
| EM008 查询统计 | `service/StatisticsService#statistics/#overview`；`service/RepairService#list`、`WorkOrderService#list` | `views/StatisticsView.vue`、列表页筛选 | `pages/repair/list`、`pages/order/list` |
| EM009 小程序登录 | 与 EM001 共用 `AuthService`；令牌 120 分钟 | — | `pages/login`、`utils/auth.js`、`utils/request.js` |
| EM010 扫码识别 | `service/EquipmentService#scan`、`#verify` | 二维码生成页签 | `pages/scan`、`utils/equipment.js` |
| EM011 移动端报修 | `service/RepairService#create`（`source=MINI`、附件关联） | — | `pages/repair/create`、`utils/upload.js` |
| EM012 移动端工单 | `service/WorkOrderService#start/#submit`（复用 EM005 校验与状态机） | — | `pages/order/detail` |
| EM013 移动端查询 | `RepairService#mine/#detail`、`WorkOrderService#mine/#detail` | — | `pages/repair/list|detail`、`pages/order/list|detail` |
| EM014 设备二维码 | `service/QrCodeService`（ZXing，内容 `EQ:<编号>`，300×300，纠错 M）、`service/EquipmentService#buildQrItems/#qrPng` | 设备台账 → 设备二维码页签（生成/预览/下载/批量打印） | `utils/equipment.js` 解析 `EQ:` 前缀 |

## 2. 业务规则对照（BR01 ~ BR12）

| 规则 | 实现位置与做法 |
| --- | --- |
| BR01 报修状态 | `domain/RequestStatus`（待受理/已驳回/已受理/已完成）；受理→已受理，驳回→已驳回，维修工单验收通过时 `RepairRequestRepository#complete` 同步为已完成；保养不建报修单 |
| BR02 工单状态与不跳步 | `domain/OrderStatus`；开工要求负责人且状态为待处理（`WorkOrderService#start`），提交完工要求处理中（`#submit`），验收到待验收（`#acceptance`）；所有更新语句都带「当前状态」条件 |
| BR03 幂等与防重 | 报修 `(reporter_id, submit_token)` 联合唯一 + 先查后建；保养 `create_token` 唯一；受理靠 `request_id` 唯一；完工/验收靠条件更新影响行数判断，`0` 行返回 40901 |
| BR04 费用口径 | `work_order.total_cost` 为整单当前总额；`work_record.total_cost_snapshot` 为每次提交快照（只展示不累加）；统计只累加维修工单 |
| BR05 统计口径 | `StatisticsService#statistics`：设备数取全表；未完成=待处理+处理中+待验收；完成数与费用按 `completed_at` 落在日期范围 |
| BR06 只能停用 | `UserService#setEnabled`（维修人员有未完成工单、最后一个启用管理员 → 40901）、`EquipmentService#setEnabled`（待受理报修/未完成工单 → 40901）；无删除接口；历史记录保留 |
| BR07 二维码规则 | `QrCodeService#content(prefix+equipmentCode)`；`EquipmentService#update` 拒绝修改设备编号；二维码不建表 |
| BR08 图片规则 | `FileService#upload`：仅 `image/jpeg`、`image/png`，≤5 MB，`ImageIO` 读取校验并重新编码，随机文件名 + 日期目录；单次 ≤6 张；`@Scheduled` 清理未关联附件 |
| BR09 完工照片必填 | `WorkOrderService#submit`：`attachmentIds` 为空 → 40001，不改变工单状态 |
| BR10 两端同一套规则 | 小程序与 Web 均调用 `/api/v1`；操作人、部门、时间、目标状态一律由服务端令牌与会话决定（`AuthContext`），DTO 中不接收操作人字段 |
| BR11 先上传后关联 | 上传时 `request_id/record_id` 为空；业务提交在同一事务内 `FileAttachmentRepository#bindToRequest/#bindToRecord`；清理任务只删两个外键都为空的记录 |
| BR12 照片访问控制 | `FileService#assertReadable`：管理员全部可见；上传人本人；报修单报修人；处理记录操作人/工单负责人；其余 40302；图片接口必须带令牌 |

## 3. 数据表对照

| 设计文档表 | 实体 | 建表脚本 |
| --- | --- | --- |
| 4.1 user | `entity/User` | `db/schema.sql` `user` |
| 4.2 equipment | `entity/Equipment` | `equipment` |
| 4.3 repair_request | `entity/RepairRequest` | `repair_request` |
| 4.4 work_order | `entity/WorkOrder` | `work_order` |
| 4.5 work_record | `entity/WorkRecord` | `work_record` |
| 4.6 acceptance | `entity/Acceptance` | `acceptance` |
| 4.7 file_attachment | `entity/FileAttachment` | `file_attachment` |

唯一约束与索引按 4.8 落地：`account`、`equipment_code`、`request_no`、`order_no`、`request_id`、`create_token` 唯一；`(reporter_id, submit_token)` 联合唯一；`(owner_id, status)`、`(biz_type, request_id)`、`record_id`、`(status, completed_at)` 等索引。

## 4. 接口对照

见 [`API.md`](API.md)。相较设计文档 5.3/5.4，实现上补充了以下**管理端既有接口**（文档 5.4 说明「EM001~EM008 对应的既有接口保持不变」，未逐条列出）：

| 路径 | 用途 |
| --- | --- |
| `GET/POST /users`、`POST /users/{id}/disable|enable`、`POST /users/{id}/reset-password`、`GET /users/technicians` | 账号管理与派工下拉 |
| `GET/POST/PUT /equipments`、`POST /equipments/{id}/disable|enable`、`GET /equipments/{id}/history` | 设备台账与履历 |
| `GET /repair-requests` | 报修列表（管理员全部 / 报修人本人） |
| `GET /work-orders` | 工单列表（管理员全部 / 维修人员本人） |
| `GET /statistics`、`GET /dashboard`、`GET /dictionaries` | 统计、首页数字与业务字典 |

## 5. 与设计文档的差异说明（均为实现必要或补充，不影响验收点）

| 项目 | 设计文档 | 实现 | 原因 |
| --- | --- | --- | --- |
| 报修故障类型、紧急程度 | 4.3 表未列出，但 EM011 要求输入并校验 | 增加 `repair_request.fault_type`、`urgency` 两列 | 用例要求「故障类型或紧急程度不在规定取值内时拒绝保存」必须有存储字段 |
| 受理信息 | 4.3 表未列出 | 增加 `decided_by`、`decided_at` | 便于展示“谁在何时受理/驳回”，与 EM004 的异常提示一致 |
| 处理记录内容 | 4.5 仅 `result` | 增加 `cause`、`process`、`replaced_parts` | EM012 明确要求分别填写故障原因、维修过程、更换零部件，`result` 必填、其余选填 |
| 密码重置后的改密要求 | EM001 2.e「重新登录后须修改本人密码」 | 增加 `user.must_change_password`；登录/个人信息接口返回该标记，Web 顶部提示、小程序个人中心提示（不强制拦截） | 文档未给出对应错误码，采用提示而非阻断，避免与 5.6 错误码表冲突 |
| 附件外键 | 4.7「REPAIR 时 request_id 必填」 | 上传阶段两外键为空，业务提交时在同一事务绑定 | BR11 要求“先上传后关联”，绑定前必然为空 |
| 状态存储 | 4.3/4.4 以中文状态存储 | 数据库存中文；接口同时返回 `status`（中文）与 `statusCode`（枚举名），查询参数两种写法都接受 | 与文档存储一致，同时便于两端按码判断 |
| 会话 | 3.5.4「运行期会话对象，不作为新增业务表」 | 内存 `SessionStore`，重启后失效 | 与文档一致，演示前重启需重新登录 |
| 错误码 | 5.6 固定列表 | 未新增错误码，全部按文档使用 | — |
