# 设备维修保养系统（Equipment Maintenance System）

软件工程课程设计 · 第 16 组 · 版本 V2.0

按《设备维修系统_设计文档_最终版.docx》（需求规格说明书 V2.0）实现的两端一后端系统：

| 端 | 技术 | 说明 |
| --- | --- | --- |
| 后端 | Java 17 + Spring Boot 3.3.5 + Spring Data JPA + MySQL 8 | 统一 REST 接口 `/api/v1`，令牌鉴权、角色与数据范围校验、状态机、事务、图片附件、设备二维码、统计 |
| Web 管理后台 | Vue 3 + Element Plus + Vite 5 + Pinia | 设备台账与二维码、报修受理派工、工单与保养、验收、统计、账号管理；构建产物由后端静态托管 |
| 微信小程序 | 微信原生框架（WXML/WXSS/JS） | 报修人扫码报修与进度查询、维修人员工单处理与完工上传 |

## 一、目录结构

```
设备维修系统/
├── backend/                        Spring Boot 后端（含 Web 构建产物与建表脚本）
│   ├── pom.xml
│   ├── src/main/java/com/sems/
│   │   ├── common/                 统一响应、错误码、异常、分页
│   │   ├── security/               令牌会话、鉴权拦截器、密码摘要（PBKDF2）
│   │   ├── domain/                 角色、状态与业务字典枚举
│   │   ├── entity/                 7 张业务表实体
│   │   ├── repo/                   Spring Data 仓储与条件更新
│   │   ├── service/                账号、设备、报修、工单、验收、附件、统计、二维码
│   │   └── web/                    控制器与 DTO
│   ├── src/main/resources/
│   │   ├── application.yml         数据库、会话、存储、附件与二维码配置
│   │   ├── db/schema.sql           建表脚本（7 张表与索引）
│   │   ├── db/demo-data.sql        演示数据（6 个账号、20 台设备、历史单据）
│   │   └── static/                 Web 后台构建产物（由 Vite 输出）
│   └── scripts/
│       ├── smoke_test.py           端到端冒烟测试（116 项，覆盖 AT01~AT12）
│       ├── test-report.md          最近一次测试报告（自动生成）
│       └── reset-demo.sql          清空业务数据并恢复演示数据
├── web/                            Web 管理后台源码（Vue 3 + Element Plus）
│   └── src/{api,components,layout,router,stores,styles,views}
├── miniprogram/                    微信小程序源码（微信开发者工具直接打开）
│   ├── app.js / app.json / app.wxss / project.config.json
│   ├── utils/                      请求、鉴权、上传、扫码、格式化、字典
│   └── pages/                      登录、首页、扫码、报修、工单、个人中心
└── docs/
    ├── API.md                      接口文档（两端共用契约）
    ├── DEPLOY.md                   部署与配置说明
    ├── TEST.md                     测试说明与用例
    ├── IMPLEMENTATION.md           需求/业务规则与代码对照表
    └── DEMO.md                     答辩演示脚本（固定演示数据与操作顺序）
```

## 二、快速启动

### 1. 环境要求

- JDK 17（本项目在 JDK 17.0.20 验证；**IDE 项目 SDK 也请设为 17**，用 JDK 23+ 时注解处理注意事项见 `docs/DEPLOY.md` 常见问题）
- Maven 3.9+（验证版本 3.9.4）
- MySQL 8.0+（验证版本 8.4）
- Node 18+（仅修改/重新构建 Web 前端时需要，验证版本 Node 24 + Vite 5.4）
- 微信开发者工具（查看小程序时需要）

### 2. 数据库

默认连接 `localhost:3306`，库名 `sems`，账号 `root` / `ZJGhxy520.`（见 `backend/src/main/resources/application.yml`）。

- 库不存在时 JDBC 参数 `createDatabaseIfNotExist=true` 会自动创建；
- 启动时自动执行 `db/schema.sql`（`CREATE TABLE IF NOT EXISTS`，可重复执行）与 `db/demo-data.sql`（固定主键 + `INSERT IGNORE`，不会重复插入）。

**请把 `application.yml` 中的 `username/password` 改成你本机的 MySQL 账号**，或直接命令行覆盖：

```bash
java -jar target/sems.jar --spring.datasource.username=root --spring.datasource.password=你的密码
```

### 3. 构建与启动后端（同时托管 Web 后台）

```bash
cd backend
mvn -DskipTests package
java -jar target/sems.jar
```

启动后访问 <http://localhost:8080/>，用演示账号登录。`target/sems.jar` 已包含 Web 静态资源，**演示时只需启动这一个进程**。

### 4. 修改 Web 前端（可选）

```bash
cd web
pnpm install          # 或 npm install
pnpm dev              # 开发模式，5173 端口，代理 /api → 8080
pnpm build            # 构建到 backend/src/main/resources/static，重新打包 jar 生效
```

### 5. 小程序

1. 微信开发者工具 → 导入项目 → 目录选择 `miniprogram/`，AppID 选“测试号/游客模式”；
2. 详情 → 本地设置 → 勾选“不校验合法域名、web-view（业务域名）、TLS 版本以及 HTTPS 证书”；
3. `miniprogram/utils/config.js` 中 `baseUrl` 默认 `http://localhost:8080/api/v1`；真机调试时改成电脑局域网 IP（如 `http://192.168.1.10:8080/api/v1`）。

## 三、演示账号

| 账号 | 初始密码 | 角色 | 姓名 | 部门 |
| --- | --- | --- | --- | --- |
| admin | admin123 | 管理员 | 张伟 | 设备科 |
| tech1 | tech123 | 维修人员 | 李强 | 维修部 |
| tech2 | tech123 | 维修人员 | 王磊 | 维修部 |
| tech3 | tech123 | 维修人员 | 孙浩 | 维修部 |
| rep1 | rep12345 | 报修人 | 陈静 | 生产一部 |
| rep2 | rep12345 | 报修人 | 赵敏 | 生产二部 |

密码规则：8~64 位且同时包含字母和数字；数据库只保存 PBKDF2-SHA256 带盐摘要。

## 四、功能实现对照（EM001 ~ EM014）

| 编号 | 功能 | 后端 | Web 后台 | 小程序 |
| --- | --- | --- | --- | --- |
| EM001 | 账号与权限 | `AuthService`/`UserService` | 账号管理、个人中心、修改密码 | 登录、个人中心、改密 |
| EM002 | 设备台账 | `EquipmentService` | 设备台账（新增/编辑/停用/履历） | 设备信息只读展示 |
| EM003 | 提交故障报修 | `RepairService.create` | 提交报修（含照片上传） | 扫码报修（含拍照上传） |
| EM004 | 受理派工 | `RepairService.accept/reject` | 报修管理（受理/驳回） | 只读查看进度 |
| EM005 | 执行任务 | `WorkOrderService.start/submit` | 工单详情（开工/提交完工） | 开工、填写、上传完工照片 |
| EM006 | 验收工单 | `WorkOrderService.acceptance` | 待验收、验收通过/退回 | 查看验收意见并整改 |
| EM007 | 安排保养 | `WorkOrderService.createMaintenance` | 工单管理 → 安排保养 | 我的工单（保养任务同流程） |
| EM008 | 查询统计 | `StatisticsService` | 查询统计、设备履历 | 我的报修 / 我的工单 |
| EM009 | 小程序登录 | 同一套 `AuthService` | — | 账号登录、角色入口、退出 |
| EM010 | 扫码识别设备 | `EquipmentService.scan/verify` | 二维码生成与打印 | `wx.scanCode` 扫码与到场核对 |
| EM011 | 移动端报修 | `RepairService.create`（source=MINI） | — | 报修表单 + 最多 6 张现场照片 |
| EM012 | 移动端工单处理 | `WorkOrderService` | — | 开工、维修过程、完工照片、提交 |
| EM013 | 移动端查询 | `RepairService.detail`/`WorkOrderService.detail` | — | 我的报修 / 我的工单详情 |
| EM014 | 设备二维码 | `QrCodeService`（ZXing，`EQ:设备编号`） | 设备二维码页签（生成/预览/下载/批量打印） | 扫码解析 |

业务规则 BR01~BR12 的代码落点见 [`docs/IMPLEMENTATION.md`](docs/IMPLEMENTATION.md)。

## 五、测试

后端接口冒烟测试（覆盖 AT01~AT12，116 项）：

```bash
# 后端已启动的前提下
python backend/scripts/smoke_test.py
```

最近一次结果：**116 项全部通过**，详见 [`backend/scripts/test-report.md`](backend/scripts/test-report.md)。
Web 后台另做了 21 项浏览器端回归（登录、菜单、设备增改与二维码、受理、工单详情、统计、账号、报修提交、开工、完工、验收），无控制台报错。
测试说明与用例表见 [`docs/TEST.md`](docs/TEST.md)。

## 六、说明与范围

- 按设计文档 1.3，设备采购、备件库存、知识库、报表导出、自动周期调度、消息推送、微信一键登录、定位签到、线上支付不在范围内；
- 二维码内容固定为 `EQ:<设备编号>`，设备编号创建后不可修改；设备名称、型号、部门、位置变更后无需重新生成二维码；
- 图片保存在 `backend/data/uploads/`（按 `yyyy/MM` 分目录），数据库只存相对路径与元数据；未被单据引用的附件 24 小时后由清理任务回收；
- 会话为内存令牌（有效期 120 分钟），重启后端后需要重新登录；
- 更细的部署、备份与接口说明见 `docs/`。
