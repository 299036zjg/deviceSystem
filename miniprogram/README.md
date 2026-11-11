# 设备维修保养系统 · 微信小程序端

课程设计项目「设备维修保养系统」的微信原生小程序端（WXML / WXSS / JS / JSON），不依赖 uni-app、Taro 或任何 npm 包与第三方 UI 库。

## 打开方式

1. 微信开发者工具 → 导入项目 → 目录选择本文件夹 `miniprogram/`
2. AppID 选择「游客模式」（`project.config.json` 中 `appid` 为 `touristappid`）
3. 本地联调：`project.config.json` 已设置 `setting.urlCheck = false`（不校验合法域名），
   后端需运行在 `http://localhost:8080`（接口前缀 `/api/v1`），地址见 `utils/config.js`

> 真机预览时 `localhost` 不可用，需把 `utils/config.js` 的 `baseUrl` / `fileBaseUrl`
> 改成电脑的局域网 IP（如 `http://192.168.1.10:8080`），并在微信公众平台配置合法域名。

## 目录结构

```
app.js / app.json / app.wxss          小程序入口与全局样式
project.config.json / sitemap.json    工程配置（游客模式、不校验域名）
utils/
  config.js     接口地址与业务常量（分页大小、照片数量与大小限制）
  request.js    wx.request 封装：Bearer 令牌、{code,message,data} 解析、40101 回登录页
  auth.js       令牌与用户信息读写、角色判断、登录校验
  format.js     时间/金额/文本格式化、状态与紧急程度颜色映射
  dict.js       GET /dictionaries 字典读取与缓存
  equipment.js  扫码（wx.scanCode）与设备识别/核对接口封装
  upload.js     wx.uploadFile 封装（POST /files，含进度与后缀规范化）
  media.js      附件图片带令牌下载与预览（图片接口需鉴权）
  uuid.js       一次性提交标识 submitToken
pages/
  login/        账号密码登录
  index/        按角色的首页入口与数字
  scan/         扫码/手动输入设备编号、扫码识别结果、到场核对
  repair/       list 我的报修、create 故障报修、detail 报修详情
  order/        list 我的工单、detail 工单处理（开工/完工/整改）
  profile/      个人信息、修改密码、退出登录
```

## 演示账号

| 角色 | 账号 | 密码 |
| --- | --- | --- |
| 报修人 | rep1 | rep12345 |
| 维修人员 | tech1 | tech123 |
| 管理员 | admin | admin123 |

管理员不在小程序端提供管理入口，管理功能请在 Web 后台操作。

## 关键约定

- 二维码内容格式：`EQ:<设备编号>`（如 `EQ:SB-001`）
- 图片上传：`POST /files` 的 `file` + `bizType`（`REPAIR` 故障现场 / `WORK` 维修完成）+ `sortNo`，
  先在报修/完工提交时逐张上传拿到 `attachmentId`，再随单据提交 `attachmentIds`
- 图片读取：`GET /files/{attachmentId}` 需要 `Authorization` 头，因此统一用 `wx.downloadFile` 下载到临时文件后展示
- 提交幂等：报修 `submitToken` 一次生成、重试复用；工单完工提交成功后重新生成
- 状态取值：接口同时返回中文 `status` 与枚举 `statusCode`，界面用「文字 + 颜色标签」双重表达

## 常见问题

### 扫码报错：`scanCode:fail Error: INVALID_LOGIN,access_token expired`

这是**微信扫码服务自身的登录态失效**，与本系统账号、后端令牌无关。处理办法：

1. 在微信开发者工具右上角退出并重新登录微信账号，或重启开发者工具；
2. 游客模式（`touristappid`）与测试号对 `wx.scanCode` 等能力限制较多，建议注册一个自己的小程序 AppID 并替换
   `project.config.json` 的 `appid`；
3. 真机调试时确认手机微信已登录，必要时在手机上重新登录微信；
4. 演示时可以不依赖真机扫码：
   - **开发者工具「模拟扫码」**：模拟器工具栏的扫码/模拟入口里选择 Web 后台「设备二维码」下载的 PNG 图片，
     内容就是 `EQ:<设备编号>`；
   - 或直接使用页面下方的**手动输入设备编号**（如 `SB-001`），前端会自动补 `EQ:` 前缀，调用同一个后端接口；
5. 代码已对该错误做中文提示（`utils/equipment.js#describeScanError`）并保留手动输入入口，不影响正常演示。

### 请求失败 / 登录后立刻回到登录页

- 网络：`utils/config.js` 的 `baseUrl` 是否指向本机 8080（真机需改成电脑局域网 IP，且手机与电脑同一网络）；
- 域名校验：详情 → 本地设置 → 勾选“不校验合法域名…”；
- 40101 说明令牌过期（默认 2 小时）、后端重启，或账号被停用/密码被重置，重新登录即可。
