# 前后端连接状态

## 已连接

- 登录通过 `src/api/auth.js` 请求 `/api/v1/auth/login`。
- 教师工作台通过 `src/api/teacher.js` 请求课程、作业、团队、提交和评分运行接口。
- Axios 统一注入 JWT，并按统一响应结构处理错误。
- 教师页面加载失败会显示真实错误，不使用假统计或假成功提示。

## 待接入（页面已标注“规划中”）

- 教师端课程中心、作业管理、学生管理、成绩查看详情页。
- 教师工作量确认、团队评分、评分运行复核和成绩发布操作。
- 学生端业务页面与成绩申诉链路。

## 本地运行前检查

- 后端：`http://127.0.0.1:8080`。
- 前端 Vite 代理：检查 `vite.config.js` 的 `/api` target。
- 数据库：MySQL `groupwork` 必须监听 `127.0.0.1:3306`。
- 若出现 401/403/5xx，页面应保留接口错误并先检查登录角色、后端权限和数据库状态。

## 目录边界

- 管理员页面：`src/views/admin`、`src/api/admin.js`、`src/layouts/AdminLayout.vue`。
- 教师页面：`src/views/teacher`、`src/api/teacher.js`、`src/components/TeacherNavigation.vue`、`src/layouts/RoleLayout.vue`。
- 两个角色共用认证、请求封装和会话状态，但不共用业务页面。
