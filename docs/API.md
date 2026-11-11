# 设备维修保养系统 接口文档（V2.0）

对应设计文档第 5 章。后端一组 REST 接口同时服务 Web 管理后台与微信小程序。

- 基础路径：`/api/v1`
- 编码：`UTF-8`；普通接口 `application/json`，上传接口 `multipart/form-data`
- 鉴权：除登录外全部接口需 `Authorization: Bearer <token>`
- 时间格式：`yyyy-MM-dd HH:mm:ss`；日期：`yyyy-MM-dd`；金额：两位小数
- 分页：`page` 从 1 开始，`size` 默认 20、最大 50
- 统一响应：`{ "code": 0, "message": "成功", "data": ... }`，列表 `data` 为 `{ items, total, page, size }`
- 状态取值：数据库与接口均使用中文状态（`待受理/已驳回/已受理/已完成`、`待处理/处理中/待验收/已完成`），查询参数同时兼容枚举名（`PENDING` 等）

## 1. 错误码

| code | 含义 | HTTP |
| --- | --- | --- |
| 0 | 成功 | 200 |
| 40001 | 参数校验失败（message 指出具体字段） | 400 |
| 40101 | 未登录或令牌失效 | 401 |
| 40301 | 角色无权执行该操作 | 403 |
| 40302 | 超出数据范围（越部门、非本人单据） | 403 |
| 40401 | 对象不存在 | 404 |
| 40901 | 状态已变化或重复提交 | 409 |
| 40902 | 设备已停用 | 409 |
| 41301 | 上传文件超过大小/数量或格式限制 | 413 |
| 42201 | 非法状态转换（如未开工直接完工） | 422 |
| 50001 | 服务器内部错误 | 500 |

## 2. 鉴权与会话

| 方法 | 路径 | 说明 | 角色 |
| --- | --- | --- | --- |
| POST | `/auth/login` | 登录，返回 `token`、`userId`、`name`、`department`、`role`、`roleLabel`、`expireAt` | 全部 |
| POST | `/auth/logout` | 退出，使当前令牌失效 | 全部 |
| GET | `/auth/profile` | 本人信息 | 全部 |
| POST | `/auth/password` | 修改密码 `{oldPassword, newPassword}`，成功后该用户全部令牌失效 | 全部 |

登录请求：`{"account":"admin","password":"admin123"}`。令牌有效期 120 分钟；账号停用、改密、密码重置、退出后立即失效。

## 3. 账号管理（管理员）

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/users?keyword=&role=&department=&enabled=&page=&size=` | 账号列表 |
| GET | `/users/technicians` | 启用的维修人员（派工下拉） |
| POST | `/users` | 新增：`{account,name,department,role,password}`，`role` 取 `ADMIN/REPORTER/TECHNICIAN` |
| POST | `/users/{id}/disable` | 停用（有未完成工单的维修人员、最后一个启用管理员不可停用） |
| POST | `/users/{id}/enable` | 启用 |
| POST | `/users/{id}/reset-password` | 重置密码 `{password}`，规则：8~64 位且含字母和数字 |

## 4. 设备台账与二维码

| 方法 | 路径 | 说明 | 角色 |
| --- | --- | --- | --- |
| GET | `/equipments?keyword=&department=&enabled=&page=&size=` | 设备列表（报修人仅本部门启用设备） | 全部 |
| GET | `/equipments/{id}` | 设备详情 | 全部 |
| GET | `/equipments/{id}/history` | 设备履历：`{equipment, repairs[], orders[]}` | 全部 |
| POST | `/equipments` | 新增设备 `{equipmentCode,equipmentName,category,model,department,location}` | 管理员 |
| PUT | `/equipments/{id}` | 编辑设备（设备编号不可改） | 管理员 |
| POST | `/equipments/{id}/disable` / `enable` | 停用/启用（有业务未完成不能停用） | 管理员 |
| GET | `/equipments/by-qr?content=EQ:SB-001` | 扫码查询设备，返回 `{equipment, canReport, message}` | 报修人、维修人员 |
| GET | `/equipments/verify?orderId=&content=` | 到场核对设备，返回 `{matched, message, orderEquipment, scannedEquipment}` | 维修人员 |
| GET | `/equipments/{id}/qrcode` | 二维码 PNG（300×300，纠错级别 M） | 管理员 |
| POST | `/equipments/qrcodes` | 批量生成 `{equipmentIds:[...]}`，≤50 台，返回 `[{equipmentId,equipmentCode,content,imageUrl,...}]` | 管理员 |

## 5. 故障报修

| 方法 | 路径 | 说明 | 角色 |
| --- | --- | --- | --- |
| POST | `/repair-requests` | 提交报修 | 报修人 |
| GET | `/repair-requests?keyword=&status=&equipmentId=&from=&to=&page=&size=` | 报修列表 | 管理员、报修人（本人） |
| GET | `/repair-requests/mine?status=&page=&size=` | 本人报修 | 报修人 |
| GET | `/repair-requests/{id}` | 报修详情（含照片、处理记录、验收记录；报修人不含费用） | 管理员、报修人（本人） |
| GET | `/repair-requests/{id}/attachments` | 报修照片 | 管理员、报修人（本人） |
| POST | `/repair-requests/{id}/accept` | 受理派工 `{ownerId, plannedDate, opinion}`，同事务创建维修工单 | 管理员 |
| POST | `/repair-requests/{id}/reject` | 驳回 `{reason}` | 管理员 |

提交报修请求体：

```json
{
  "equipmentId": 2,
  "equipmentCode": "SB-002",
  "description": "注塑机液压系统压力不足……",
  "faultType": "液压气动故障",
  "urgency": "较高",
  "discoveredAt": "2026-11-11 09:30:00",
  "attachmentIds": [12, 13],
  "submitToken": "8f3c...-一次性标识",
  "source": "MINI"
}
```

- `equipmentId` 与 `equipmentCode` 二选一；报修人、部门由令牌确定
- `faultType` 取字典值，`urgency` 取 `一般/较高/紧急`
- 同一报修人相同 `submitToken` 重试返回原单据（幂等）
- `source` 取 `WEB` 或 `MINI`，仅用于追溯统计

## 6. 工单、保养、完工与验收

| 方法 | 路径 | 说明 | 角色 |
| --- | --- | --- | --- |
| GET | `/work-orders?keyword=&status=&type=&ownerId=&equipmentId=&from=&to=&page=&size=` | 工单列表（维修人员仅本人） | 管理员、维修人员 |
| GET | `/work-orders/mine?status=&page=&size=` | 本人工单 | 维修人员 |
| GET | `/work-orders/{id}` | 工单详情：`{summary, records[], acceptances[], repairPhotos[], workPhotos[]}` | 管理员、本单负责人 |
| GET | `/work-orders/{id}/attachments` | 工单照片 | 管理员、本单负责人 |
| POST | `/work-orders` | 安排保养 `{equipmentId, taskContent, plannedDate, ownerId, createToken}` | 管理员 |
| POST | `/work-orders/{id}/start` | 开工（可带 `{equipmentCode}` 现场核对） | 维修人员（负责人） |
| POST | `/work-orders/{id}/submit` | 提交完工 | 维修人员（负责人） |
| POST | `/work-orders/{id}/acceptance` | 验收 `{result: PASS/RETURN, opinion}` | 管理员 |
| GET | `/acceptances/pending?page=&size=` | 待验收工单列表 | 管理员 |

提交完工请求体：

```json
{
  "cause": "液压油缸密封圈老化导致内泄",
  "process": "拆检油缸，更换密封圈，补压试机",
  "result": "压力恢复至 16MPa，试生产 20 件合格",
  "replacedParts": "密封圈套件 ×1",
  "totalCost": 120.50,
  "attachmentIds": [21],
  "submitToken": "…"
}
```

- `result` 必填；`totalCost` ≥ 0 且 ≤ 99 999 999.99，最多两位小数，为整单当前总额（不是累加）
- `attachmentIds` 至少 1 张、最多 6 张（BR09：无完工照片不能提交完工）
- 退回整改后再次提交会追加新的处理记录，`totalCost` 覆盖为新的总额

## 7. 图片上传与访问

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `/files` | `multipart/form-data`：`file`、`bizType`（`REPAIR` 故障现场 / `WORK` 维修完成）、`sortNo` |
| GET | `/files/{attachmentId}` | 按附件标识鉴权访问；`?width=200` 取缩略图 |

- 仅允许 JPG/PNG，单张 ≤ 5 MB；服务端重新编码并随机命名，按 `yyyy/MM` 分目录存储
- 上传返回 `attachmentId` 与 `url`；业务提交时通过 `attachmentIds` 关联，关联与业务提交同一事务
- 上传成功但业务未提交的附件保留 24 小时，由清理任务回收
- 访问控制：管理员全部可见；报修人见本人报修照片；维修人员见本人工单处理记录照片

## 8. 统计与字典

| 方法 | 路径 | 说明 | 角色 |
| --- | --- | --- | --- |
| GET | `/statistics?from=&to=` | `{equipmentCount, unfinishedOrderCount, doneRepairCount, doneMaintenanceCount, repairCostTotal}` | 管理员 |
| GET | `/dashboard` | 按角色返回首页数字 | 全部 |
| GET | `/dictionaries` | 故障类型、紧急程度、各状态码与中文标签 | 全部 |

统计口径（BR05）：设备总数为全部台账；未完成工单含待处理、处理中、待验收；完成数量与维修费用按完成日期范围统计，保养费用不计入维修费用。

## 9. 前端联调提示

- Web 后台图片：用带 `Authorization` 头的请求获取二进制后转 `blob:` URL（图片接口需要鉴权，不能直接放进 `<img src>`）
- 40101 时应清除本地令牌并回到登录页；40301 按角色隐藏入口；40302 提示无权查看
- 小程序：`wx.request` 需在微信公众平台配置合法域名，本地联调可勾选“不校验合法域名”
