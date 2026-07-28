# Groupwork 前端

这是校园团队作业差异化评分系统的 Vue 3 前端。当前版本完成精简管理员端，并为教师端、学生端保留可登录、可鉴权、可继续开发的最小骨架。

## 当前页面

### 管理员

| 路由 | 页面 | 职责 |
| --- | --- | --- |
| `/admin/dashboard` | 工作台 | 账号、课程、评分运行、待处理异常和系统状态概览 |
| `/admin/organization` | 组织与账号 | 院系/专业/班级概况，教师与学生账号维护 |
| `/admin/teaching-data` | 教学数据 | 全局检索课程、作业和小组汇总数据 |
| `/admin/scoring-model` | 评分模型 | 模型版本、评分运行和异常处理 |
| `/admin/audit-logs` | 审计日志 | 查询登录、数据变更、评分与人工处理事件 |

修改密码和退出登录位于右上角个人菜单，不占用侧边导航。

### 教师与学生

- `/teacher/dashboard`
- `/student/dashboard`

两个角色目前只保留统一布局和“模块待团队开发”首页。旧草稿页面已删除，避免后续开发继续依赖不稳定的 UI 和接口。

## 技术栈

- Vue 3 + Vite
- Vue Router
- Pinia
- Element Plus
- Axios
- Vitest

## 本地运行

```bash
npm install
npm run dev
```

开发服务器默认运行在 `http://localhost:5173`，并将 `/api` 代理到 `http://localhost:8080`。
需要联调其他后端端口时，在启动前设置 `VITE_API_PROXY_TARGET`。

```bash
npm test
npm run build
npm run preview
```

## API 约定

Axios 基础路径固定为 `/api/v1`。所有后端响应使用统一结构：

```json
{
  "code": 200,
  "message": "success",
  "data": {},
  "requestId": "..."
}
```

`code` 为 `0`、`200` 或 `201` 时视为成功；其他业务码和非 2xx HTTP 状态会转换为统一的 `ApiError`。HTTP 401 会立即清除本地会话，受保护路由采用 fail-closed 策略返回登录页。

管理员端使用以下资源：

| 方法 | 路径 |
| --- | --- |
| POST | `/api/v1/auth/login` |
| POST | `/api/v1/auth/logout` |
| GET | `/api/v1/auth/me` |
| PUT | `/api/v1/auth/change-password` |
| GET | `/api/v1/admin/dashboard` |
| GET | `/api/v1/admin/organization/overview` |
| GET / POST | `/api/v1/admin/accounts` |
| POST | `/api/v1/admin/accounts/import` |
| PUT | `/api/v1/admin/accounts/{id}` |
| PUT | `/api/v1/admin/accounts/{id}/status` |
| POST | `/api/v1/admin/accounts/{id}/reset-password` |
| GET | `/api/v1/admin/courses` |
| GET / POST | `/api/v1/admin/model-versions` |
| POST | `/api/v1/admin/model-versions/{id}/activate` |
| GET | `/api/v1/admin/scoring-runs` |
| GET | `/api/v1/admin/anomalies` |
| PUT | `/api/v1/admin/anomalies/{id}` |
| GET | `/api/v1/admin/audit-events` |

关键写操作请求体：

- 修改密码：`{ "currentPassword": "...", "newPassword": "..." }`
- 更新账号：请求体必须带账号当前 `role`
- 启停账号：`{ "role": "ROLE_TEACHER", "status": 1 }`，其中 `1` 为启用、`0` 为停用
- 重置密码：`{ "role": "ROLE_STUDENT", "newPassword": "..." }`
- 处理评分异常：`{ "resolution": "..." }`

列表查询统一发送 `page`、`pageSize` 和页面筛选条件。推荐分页数据结构：

```json
{
  "items": [],
  "total": 0,
  "page": 1,
  "pageSize": 10
}
```

前端兼容 `items`、`rows`、`records` 和 `list` 四种列表字段，但新接口应统一使用 `items`。

## 目录结构

```text
src/
├─ api/             # Axios 实例、响应规范和按域接口
├─ components/      # 通用导航、页头和个人菜单
├─ layouts/         # 管理员布局、教师/学生统一布局
├─ router/          # 路由与角色守卫
├─ stores/          # Pinia 会话状态
├─ styles/          # 全局设计令牌和布局样式
├─ utils/           # 角色、会话和格式化纯函数
└─ views/
   ├─ admin/        # 管理员 5 个页面
   └─ role/         # 教师/学生占位首页
```

## 教师端和学生端扩展规则

1. 在 `src/views/teacher/` 或 `src/views/student/` 中增加页面。
2. 在 `src/router/index.js` 对应角色的 `children` 中增加唯一命名路由。
3. 在 `src/api/` 中按业务域新增接口模块；统一复用 `api/http.js`，不要创建第二个 Axios 实例。
4. 路由必须保留 `requiresAuth` 与角色限制，不能在异常分支放行。
5. 页面不能使用模拟数据冒充后端结果。加载失败应显示空状态或明确错误。
6. 新页面沿用浅灰背景、白色卡片、蓝色强调色和现有响应式布局。
