/**
 * 全局配置：接口地址与业务常量
 *
 * 说明：后端运行在 http://localhost:8080，接口前缀 /api/v1。
 * 本地联调时请在微信开发者工具中保持 project.config.json 的 setting.urlCheck = false
 * （不校验合法域名），否则 localhost 请求会被拦截。
 */
const baseUrl = 'http://localhost:8080/api/v1';

// 图片地址：接口返回形如 "/api/v1/files/8" 的相对路径，需要拼上站点根地址
const fileBaseUrl = 'http://localhost:8080';

module.exports = {
  baseUrl: baseUrl,
  fileBaseUrl: fileBaseUrl,

  // 列表分页大小（设计要求 size=10）
  pageSize: 10,

  // 照片限制：单张 ≤ 5 MB，单次最多 6 张（BR08/BR09）
  maxPhotoCount: 6,
  maxPhotoBytes: 5 * 1024 * 1024,
  allowedImageExt: ['jpg', 'jpeg', 'png'],

  // 报修来源固定为小程序
  sourceMini: 'MINI',

  // 角色（与后端 Role 枚举一致）
  roles: {
    ADMIN: 'ADMIN',
    REPORTER: 'REPORTER',
    TECHNICIAN: 'TECHNICIAN'
  },

  // 角色中文名兜底（接口已返回 roleLabel，此表用于本地兜底展示）
  roleLabels: {
    ADMIN: '管理员',
    REPORTER: '报修人',
    TECHNICIAN: '维修人员'
  }
};
