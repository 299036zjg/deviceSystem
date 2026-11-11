# 部署与配置说明

## 1. 环境要求

| 组件 | 版本要求 | 本项目验证版本 |
| --- | --- | --- |
| JDK | 17+ | 17.0.20 |
| Maven | 3.6+ | 3.9.4 |
| MySQL | 8.0+ | 8.4 |
| Node.js（仅构建 Web） | 18+ | 24.21 |
| 浏览器 | Chrome / Edge 最新版 | Chrome 1440×900 验证 |
| 微信开发者工具 | 最新稳定版 | 未在本机验证（见第 6 节） |

## 2. 数据库准备

### 2.1 连接配置

`backend/src/main/resources/application.yml`：

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/sems?createDatabaseIfNotExist=true&useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true&useSSL=false
    username: root
    password: ZJGhxy520.
```

- 库名可改（同时修改 URL 中的 `sems`），连接用户需要 `CREATE`、`CREATE DATABASE` 权限以便自动建库建表；
- 生产环境建议单独建库账号，只授予 `sems` 库的 DML/DDL 权限；
- `characterEncoding` 必须写 `UTF-8`（写 `utf8mb4` 会被 JDBC 驱动拒绝）。

### 2.2 初始化

首次启动自动完成：

1. `createDatabaseIfNotExist=true` 创建 `sems` 库；
2. 执行 `db/schema.sql` 建 7 张表与索引（`CREATE TABLE IF NOT EXISTS`，可重复执行）；
3. 执行 `db/demo-data.sql` 写入演示数据（固定主键 + `INSERT IGNORE`，重复执行不会产生重复行）。

手动初始化（可选）：

```bash
mysql -u root -p -e "CREATE DATABASE IF NOT EXISTS sems DEFAULT CHARACTER SET utf8mb4"
mysql -u root -p sems < backend/src/main/resources/db/schema.sql
mysql -u root -p sems < backend/src/main/resources/db/demo-data.sql
```

### 2.3 重置演示数据

```bash
mysql -u root -p < backend/scripts/reset-demo.sql   # DROP + CREATE 库
rm -rf backend/data/uploads                          # 清空图片（Windows: Remove-Item -Recurse -Force）
# 重新启动后端，自动重建表结构与演示数据
```

## 3. 后端构建与启动

```bash
cd backend
mvn -DskipTests package       # 生成 target/sems.jar（约 50 MB，内含 Web 静态资源）
java -jar target/sems.jar
```

- 默认端口 8080，可用 `--server.port=9090` 覆盖；
- 首次启动约 8 秒，日志：控制台 + `backend/logs/sems.log`；
- 健康检查：`GET http://localhost:8080/api/v1/dictionaries`（需登录，因此可用登录接口自测）。

### 3.1 配置项（`application.yml` → `sems.*`）

| 配置 | 默认值 | 说明 |
| --- | --- | --- |
| `sems.session.ttl-minutes` | 120 | 令牌有效期，重启后端会话全部失效 |
| `sems.storage.root` | `./data/uploads` | 图片存储根目录（相对启动目录），按 `yyyy/MM` 分目录 |
| `sems.attachment.max-per-request` | 6 | 单次报修/完工最多照片数 |
| `sems.attachment.max-size-bytes` | 5242880 | 单张上限 5 MB |
| `sems.attachment.retention-hours` | 24 | 未关联附件保留时长 |
| `sems.attachment.cleanup-interval-minutes` | 60 | 清理任务执行间隔 |
| `sems.qrcode.size` | 300 | 二维码图片边长（像素） |
| `sems.qrcode.prefix` | `EQ:` | 二维码内容前缀，内容为 `EQ:<设备编号>` |

## 4. Web 后台

```bash
cd web
pnpm install
pnpm dev        # 开发：http://localhost:5173，/api 代理到 8080
pnpm build      # 生产：输出到 backend/src/main/resources/static
```

构建后重新 `mvn package` 并重启 jar，即可通过 <http://localhost:8080/> 访问。
路由使用 hash 模式（`/#/dashboard`），无需额外配置 Nginx 回退规则；若用 Nginx 反向代理，只需转发 `/` 到 8080。

## 5. 小程序

1. 微信开发者工具导入 `miniprogram/`，`project.config.json` 中 `appid` 为 `touristappid`（游客模式）；
2. 开发阶段勾选“不校验合法域名”（`setting.urlCheck=false` 已写入）；
3. 修改 `miniprogram/utils/config.js`：

```js
const baseUrl = 'http://localhost:8080/api/v1';   // 真机改为电脑局域网 IP
const fileBaseUrl = 'http://localhost:8080';
```

4. 正式发布需在微信公众平台把后端域名（HTTPS）配置为 request/uploadFile/downloadFile 合法域名。

## 6. 备份与恢复（设计文档 8.1）

```bash
# 备份：数据库 + 图片目录
mysqldump -u root -p --single-transaction --routines sems > backup/sems_$(date +%Y%m%d).sql
tar -czf backup/uploads_$(date +%Y%m%d).tar.gz backend/data/uploads

# 恢复：先导入 SQL，再解压图片目录；随后核对数量
mysql -u root -p sems < backup/sems_20261111.sql
```

恢复后核对（数量应与备份前一致）：

```sql
SELECT
  (SELECT COUNT(*) FROM `user`) AS users,
  (SELECT COUNT(*) FROM equipment) AS equipments,
  (SELECT COUNT(*) FROM repair_request) AS repairs,
  (SELECT COUNT(*) FROM work_order) AS orders,
  (SELECT COUNT(*) FROM work_record) AS records,
  (SELECT COUNT(*) FROM acceptance) AS acceptances,
  (SELECT COUNT(*) FROM file_attachment) AS attachments;
```

同时抽查 `file_attachment.file_path` 对应的文件是否存在于 `data/uploads`，避免“有记录无文件”。

## 7. 常见问题

| 现象 | 原因与处理 |
| --- | --- |
| 启动报 `Unsupported character encoding 'utf8mb4'` | URL 中 `characterEncoding` 必须为 `UTF-8` |
| 启动报 `Access denied for user 'root'@'localhost'` | `application.yml` 的账号密码与本机不一致 |
| 端口 8080 被占用 | `java -jar target/sems.jar --server.port=9090` |
| 前端 40101 立即跳登录 | 令牌过期（默认 2 小时）、后端重启、账号被停用或密码被重置，属预期行为 |
| 上传图片返回 41301 | 超过 5 MB 或数量超过 6 张；前端与服务端都会校验 |
| 上传目录残留文件 | 未被单据引用的附件 24 小时后由定时任务回收，可手动清理 `data/uploads` |
| 中文控制台乱码 | Windows 控制台默认 GBK，日志文件 `logs/sems.log` 为 UTF-8，用编辑器查看即可 |
| 小程序请求失败 | 未勾选“不校验合法域名”，或 `config.js` 的 `baseUrl` 不是可访问地址 |
| 设备停用失败 | 存在待受理报修或未完成工单，先处理关联单据（接口会返回具体原因） |
| IDEA 构建报 `ExceptionInInitializerError` / `com.sun.tools.javac.code.TypeTag :: UNKNOW` | Lombok 与 JDK 版本不匹配：IDEA 项目 SDK 是 25 时，Spring Boot 默认的 Lombok 1.18.34 只支持到 JDK 22。处理：① 项目 SDK 设为 17（File → Project Structure → Project SDK）；② pom 已把 Lombok 升到 1.18.42 并保留注解处理，IDEA 中同时确认 Settings → Build → Compiler → Annotation Processors 已勾选。 |
| 用 JDK 21+ 执行 `mvn package` 报大量“找不到符号” | JDK 23+ 的 javac 默认不做注解处理。pom 已加 JDK 21+ 自动激活的 profile（`-proc:full`），若仍失败请确认 Maven 使用的是本项目的 pom 且没有手动 `-Dmaven.compiler.proc=none`。 |
