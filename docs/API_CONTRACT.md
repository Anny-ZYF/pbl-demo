# API 契约

本文档列出当前仓库中 7 个 Java Controller 暴露的全部接口，共 52 个业务端点。除特别说明外，路径前缀均为 `/api/v1`，请求和响应使用 JSON，时间为 ISO-8601 本地日期时间，服务器时区为 `Asia/Shanghai`。机器可读的当前契约见 [`openapi.json`](./openapi.json)。

## 1. 通用约定

### 1.1 统一响应

成功：

```json
{
  "code": 200,
  "message": "success",
  "data": {},
  "requestId": "4f742657-3f3a-4dc3-9578-6eb693f02607"
}
```

创建成功的 4 个接口使用 HTTP 201，且响应体 `code=201`、`message=created`。错误时 HTTP 状态与 `code` 一致，`data=null`。

常见错误：

| HTTP | 含义 |
|---|---|
| 400 | DTO 校验失败、角色/状态/份额/分数不合法。 |
| 401 | 未登录、Access Token 无效、登录凭据错误、Refresh Token 无效。 |
| 403 | 角色不匹配，或学生不属于目标团队等。 |
| 404 | 资源不存在，或资源不属于当前教师/学生；为防越权泄漏两者通常统一为 404。 |
| 409 | 当前状态不允许操作、已重复创建、截止时间已过、已有未结申诉等。 |
| 500 | 未处理的服务器错误；响应不会返回内部堆栈。 |

客户端可传 `X-Request-Id`，格式为 8–64 位字母、数字、点、下划线或连字符；否则服务器生成 UUID。响应头 `X-Request-Id` 与响应体 `requestId` 用于排查日志。

### 1.2 鉴权与角色

除登录、刷新和健康检查外，请求头必须包含：

```text
Authorization: Bearer <accessToken>
```

后端角色值为 `ADMIN`、`TEACHER`、`STUDENT`。需要角色入参的接口同时接受 `ROLE_ADMIN`、`ROLE_TEACHER`、`ROLE_STUDENT`，但响应统一使用不带 `ROLE_` 的形式。不要把前端路由守卫当成后端鉴权的替代品。

### 1.3 分页

管理员列表使用：

```json
{
  "items": [],
  "total": 0,
  "page": 1,
  "pageSize": 20
}
```

`page` 默认 1，小于 1 时按 1；`pageSize` 默认 20，服务器限制为 1–100。

## 2. 身份接口 `AuthController`

| 方法与路径 | 权限 | 请求 | 成功 `data` | HTTP |
|---|---|---|---|---|
| `POST /auth/login` | 公开 | `LoginRequest` | `TokenResponse` | 200 |
| `POST /auth/refresh` | 公开 | `{refreshToken}` | 新 `TokenResponse` | 200 |
| `POST /auth/logout` | 已登录 | 无 | `null` | 200 |
| `GET /auth/me` | 已登录 | 无 | `MeResponse` | 200 |
| `PUT /auth/change-password` | 已登录 | `ChangePasswordRequest` | `null` | 200 |

登录请求：

```json
{
  "username": "T2023001",
  "password": "example-password",
  "role": "TEACHER"
}
```

- `username` 必填，最长 50；`password` 必填，最长 100。
- `role` 可省略。省略时后端在统一账号视图中按用户名查询；只有命中唯一角色才继续校验密码。同一用户名跨角色重复或没有命中时统一返回 401，不泄漏账号存在性。
- 显式传 `role` 时只在指定角色中查找。

`TokenResponse`：

```json
{
  "accessToken": "...",
  "refreshToken": "...",
  "tokenType": "Bearer",
  "expiresIn": 1800,
  "role": "TEACHER",
  "mustChangePassword": true
}
```

刷新令牌采用单次轮换：成功刷新会撤销旧令牌并签发一对新令牌。Access Token 与 Refresh Token 都绑定账号 `tokenVersion`；退出、本人改密、管理员重置密码或切换账号状态会递增版本，旧 Token 随即失效。

`MeResponse`：

```json
{
  "id": 12,
  "username": "T2023001",
  "displayName": "张老师",
  "role": "TEACHER",
  "mustChangePassword": false
}
```

修改密码请求：

```json
{
  "currentPassword": "old-password",
  "newPassword": "new-password-2026"
}
```

新密码为 8–64 位且至少含一个字母和一个数字，不能与当前密码相同。成功后 `mustChangePassword=false`，撤销已有 Refresh Token 并递增 `tokenVersion`；已签发 Access Token 也立即失效。

`mustChangePassword=true` 时，`PasswordChangeRequiredFilter` 对该账号只放行 `GET /auth/me`、`PUT /auth/change-password` 和 `POST /auth/logout`（以及浏览器预检）；其他业务接口返回 403“首次登录或密码重置后必须先修改密码”。前端固定进入 `/change-password`，改密成功后清除会话并要求用新密码重新登录，不能只靠隐藏菜单实现此约束。

## 3. 文件接口 `FileController`

| 方法与路径 | 权限 | 请求 | 成功 `data` | HTTP |
|---|---|---|---|---|
| `POST /files` | 任一已登录角色 | `multipart/form-data`，字段 `file` | `StoredObject` | 200 |
| `GET /files?key={storageKey}` | 具有附件读取权限的已登录角色 | 查询参数 `key` | 原始文件流 | 200 |

```json
{
  "storageKey": "2026/07/student/35/7fb0c3f0-....pdf",
  "originalName": "团队报告.pdf",
  "size": 120045,
  "sha256": "64位十六进制哈希"
}
```

允许扩展名：`pdf`、`doc`、`docx`、`xls`、`xlsx`、`ppt`、`pptx`、`zip`、`txt`、`md`、`png`、`jpg`、`jpeg`。单文件上限 20 MB，请求上限 25 MB。返回的是受控 `storageKey`，不是公开 URL。

下载接口返回带 UTF-8 文件名的 `Content-Disposition: attachment`，并设置 `X-Content-Type-Options: nosniff`。管理员可读取任意受控附件；上传者可读取自己角色/ID目录下的附件；教师和学生读取他人上传附件时还要通过课程、作业、团队或成绩关系校验。无读取权限返回 403，文件不存在返回 404。当前没有浏览器内联预览或删除接口。

## 4. 管理员接口 `AdminController`

本节所有接口要求 `ADMIN`。

### 4.1 接口总表

| 方法与路径 | 查询/请求 | 成功 `data` | HTTP |
|---|---|---|---|
| `GET /admin/dashboard` | 无 | 工作台对象 | 200 |
| `GET /admin/organization/overview` | 无 | 组织概况与嵌套树 | 200 |
| `GET /admin/accounts` | `role? status? keyword? page? pageSize?` | 账号分页 | 200 |
| `POST /admin/accounts` | `AccountCreateRequest` | 临时密码信息 | **201** |
| `POST /admin/accounts/import` | multipart `file` | `ImportReport` | 200 |
| `PUT /admin/accounts/{id}` | `AccountUpdateRequest` | `null` | 200 |
| `PUT /admin/accounts/{id}/status` | `{role,status}` | `null` | 200 |
| `POST /admin/accounts/{id}/reset-password` | `{role,newPassword}` | `null` | 200 |
| `GET /admin/courses` | `keyword? teacherName? status? page? pageSize?` | 课程分页 | 200 |
| `GET /admin/model-versions` | `status? page? pageSize?` | 模型分页 | 200 |
| `POST /admin/model-versions` | `ModelRegistrationRequest` | `{id,status}` | **201** |
| `POST /admin/model-versions/{id}/activate` | 无 | `null` | 200 |
| `GET /admin/scoring-runs` | `keyword? status? page? pageSize?` | 评分运行分页 | 200 |
| `GET /admin/anomalies` | `keyword? severity? status? page? pageSize?` | 异常分页 | 200 |
| `PUT /admin/anomalies/{id}` | `{resolution}` | `null` | 200 |
| `GET /admin/audit-events` | `eventType? keyword? actorRole? startDate? endDate? page? pageSize?` | 审计分页 | 200 |

### 4.2 工作台与组织

工作台 `data` 同时提供前端友好的聚合对象和部分原始计数：

```json
{
  "summary": {
    "studentCount": 0,
    "teacherCount": 0,
    "accountCount": 0,
    "courseCount": 0,
    "assignmentCount": 0,
    "scoringRunCount": 0,
    "pendingAnomalyCount": 0,
    "openAppealCount": 0,
    "activeModelVersion": null
  },
  "systemStatus": {
    "scoringService": "HEALTHY",
    "activeModelVersion": null,
    "lastUpdatedAt": "2026-07-15T15:00:00"
  },
  "recentScoringRuns": [
    {
      "id": 1,
      "runNo": "RUN-...",
      "status": "MODEL_COMPLETE",
      "assignmentTitle": "课程报告",
      "modelVersion": "xgb-...",
      "finishedAt": "2026-07-15T14:00:00",
      "updatedAt": "2026-07-15T14:00:00"
    }
  ],
  "updatedAt": "2026-07-15T15:00:00"
}
```

`systemStatus.scoringService` 目前由“是否存在历史 `DEGRADED` 运行”推导为 `HEALTHY/WARNING`，不是对 Python 服务的实时健康探测。

组织概况包含 `departmentCount`、`majorCount`、`classCount`、`activeTeacherCount`、`activeStudentCount`、`accountCount` 和：

```json
{
  "departments": [
    {
      "id": 1,
      "name": "基础医学院",
      "status": 1,
      "majors": [
        {
          "id": 2,
          "departmentId": 1,
          "name": "临床医学",
          "status": 1,
          "classes": [
            {"id": 3, "majorId": 2, "name": "临床1班", "grade": "2024", "status": 1}
          ]
        }
      ]
    }
  ]
}
```

### 4.3 账号

账号查询：

- `role`：`ADMIN/TEACHER/STUDENT` 或 `ROLE_*`。
- `status`：启用可传 `1/ACTIVE/ENABLED`，停用可传 `0/INACTIVE/DISABLED`。
- `keyword`：匹配用户名或显示名。

账号分页项字段：`id`、`username`、`displayName`、`name`、`role`、`status`、`mustChangePassword`、`departmentId`、`majorId`、`classId`、`grade`、`departmentName`、`majorName`、`className`、`organizationName`。

创建请求：

```json
{
  "role": "STUDENT",
  "username": "20240101",
  "displayName": "李同学",
  "initialPassword": "Student2026",
  "departmentId": 1,
  "majorId": 2,
  "classId": 3,
  "grade": 2024
}
```

- 管理员创建接口仅接受教师和学生角色，登录账号在所有角色之间必须唯一。
- 教师必须填写姓名、工号和所属学院，不得携带专业、班级或年级。
- 学生必须填写姓名、学号、所属学院、专业、班级和年级；专业必须属于所选学院，班级必须属于所选专业，且相关组织必须处于启用状态。
- 工号/学号最长 20；年级范围为 2000–2100。未提供初始密码时使用 `ABC` 加登录账号，首次登录必须修改。

- `displayName` 也接受别名 `name`；最长 50。
- `initialPassword` 可省略；若提供须为 8–64 位且含字母和数字。
- 省略或留空密码时，初始密码为 `ABC` 加清理后的登录账号，例如账号 `20260101` 对应 `ABC20260101`。成功响应为 `{username,role,temporaryPassword,mustChangePassword:true}`；初始密码不得写入前端日志。
- `role` 仅接受教师或学生。

更新请求不允许改用户名和角色，只用 `role` 定位物理表：

```json
{
  "role": "STUDENT",
  "displayName": "李同学",
  "departmentId": 1,
  "majorId": 2,
  "classId": 3,
  "grade": 2024
}
```

状态请求的 `status` 只能为整数 `0` 或 `1`。管理员不能停用当前登录的自身管理员账号。停用和重置密码都会撤销目标账号刷新令牌。

重置密码：

```json
{"role":"STUDENT","newPassword":"Reset2026Password"}
```

CSV 导入要求 UTF-8、`.csv`、不超过 2 MB、最多 5000 条数据，表头必须严格为：

```text
role,username,displayName,departmentId,majorId,classId,grade,initialPassword
```

`ImportReport` 为 `{totalRows,successRows,failureRows,createdAccounts,errors:[{row,message}]}`。导入按行处理，可能部分成功，不是全文件原子事务。

### 4.4 课程、模型、运行、异常与审计

课程分页项：`id`、`code`、`name`、`term`、`grade`、`status`、`teacherName`、`departmentName`、`beginDate`、`endDate`、`studentCount`、`assignmentCount`、`groupCount`。`teacherName` 为教师姓名模糊查询；`status` 只接受 `ACTIVE/INACTIVE/ARCHIVED`。

模型分页 `status` 支持 `ACTIVE` 或 `INACTIVE`。项字段：`id`、`modelKey`、`name`、`version`、`algorithm`、`status`、`artifactUri`、`artifactSha256`、`metricsJson`、`trainedAt`、`activatedAt`、`createdAt`。

模型注册请求：

```json
{
  "modelKey": "paper-score-p4",
  "version": "xgb-p4-seed2025-172a3cf9486e",
  "artifactUri": "algorithm-service://artifacts/xgboost-model.json",
  "artifactSha256": "172a3cf9486e2fa88c0d37c0401844521abdf426a6f17bf66271edb2154c162d",
  "featureSchemaJson": "[\"contribution\",\"actual_group_size\",\"group_score\"]",
  "hyperparametersJson": "{\"random_state\":2025,\"n_estimators\":800,\"max_depth\":6}",
  "metricsJson": "{\"status\":\"ELIGIBLE\",\"holdout\":{\"rmse\":0.4828993804,\"mae\":0.3568366849,\"r2\":0.9997004404},\"eligibility\":{\"passed\":true}}"
}
```

三个 `*Json` 字段必须是 JSON 文本。生产注册契约固定 `modelKey=paper-score-p4`，`artifactUri` 必须匹配 `algorithm-service://artifacts/*.json`，特征数组必须严格按 `contribution, actual_group_size, group_score` 排列，`hyperparametersJson` 必须是对象。`metricsJson` 按 `holdout`、`test`、顶层的顺序读取指标；准入条件为有限且非负的 `RMSE <= 1.0`、`MAE <= 0.8`，以及 `0.99 <= R² <= 1`。若提供 `status`，它必须为 `ELIGIBLE`；若提供 `eligibility.passed`，它不能为 `false`。

制品元数据中的 `ELIGIBLE` 表示评估准入结论；管理数据库注册成功后返回的生命周期状态仍为 `VALIDATED`。只有 `VALIDATED` 或 `RETIRED` 版本可激活；激活同一 `modelKey` 的版本会先把原 `ACTIVE` 版本置为 `RETIRED`，因此再次激活旧版本就是受控回滚。

评分运行查询 `status` 支持前端聚合值：

- `RUNNING` -> 数据库 `CREATED`
- `SUCCESS` -> `MODEL_COMPLETE/APPROVED/PUBLISHED`
- `FAILED` -> `FAILED/DEGRADED`

分页项字段：`id`、`runNo`、`status`、`assignmentId`、`assignmentTitle`、`groupName`、`startedAt`、`completedAt`、`finishedAt`、`errorMessage`、`modelVersion`、`sampleCount`。

异常查询：

- `severity`：`HIGH`（偏差 >5）、`MEDIUM`（>3 且 <=5）、`LOW`（<=3）。注意异常记录本身已满足评分核心条件 `delta>2`。
- `status`：独立治理状态 `PENDING/RESOLVED`，不等同于教师成绩复核状态。
- 项字段：`id`、`studentId`、`studentName/subjectName`、`assignmentTitle`、`groupScore`、`ruleScore`、`modelScore`、`modelRuleDelta`、`status`、`severity`、`type`、`reviewReason`、`resolution`、`resolvedAt`、`runNo`、`createdAt/detectedAt`。
- 处理请求：`{"resolution":"核对输入与模型版本后确认结果"}`，最长 500；只可处理 `anomalyStatus=PENDING` 的异常。管理员处理只写异常治理结论，不会替代教师复核或修改最终分。

审计查询的 `startDate/endDate` 为 `YYYY-MM-DD`，结束日期包含当日；`actorRole` 接受带或不带 `ROLE_` 的角色。项字段：`id`、`eventType`、`actorRole`、`actorId`、`actorName`、`aggregateType/resourceType`、`aggregateId`、`resourceName`、`result`、`requestId`、`detailJson/context`、`ipAddress`、`userAgent`、`createdAt/occurredAt`。

## 5. 教师接口

本节全部要求 `TEACHER`，并在服务/SQL 中按当前教师 ID 限定资源。

### 5.1 教学接口 `TeacherController`

| 方法与路径 | 请求 | 成功 `data` | HTTP |
|---|---|---|---|
| `GET /teacher/courses` | 无 | 课程数组 | 200 |
| `POST /teacher/courses` | `CourseCreateRequest` | `{id,name,status:"ACTIVE"}` | **201** |
| `GET /teacher/courses/{courseId}/assignments` | 无 | 作业数组 | 200 |
| `GET /teacher/courses/{courseId}/students` | `assignmentId?` | 课程学生候选数组 | 200 |
| `POST /teacher/courses/{courseId}/assignments` | `AssignmentCreateRequest` | `{id,status:"DRAFT"}` | **201** |
| `POST /teacher/assignments/{assignmentId}/publish` | 无 | `null` | 200 |
| `GET /teacher/assignments/{assignmentId}/groups` | 无 | 团队数组 | 200 |
| `GET /teacher/assignments/{assignmentId}/groups/{groupId}/members` | 无 | 团队成员数组 | 200 |
| `POST /teacher/assignments/{assignmentId}/groups` | `GroupCreateRequest` | `{id,memberCount,status:"CONFIRMED"}` | **201** |
| `GET /teacher/assignments/{assignmentId}/submissions` | 无 | 提交数组 | 200 |

教师课程项：`id`、`code`、`name`、`term`、`grade`、`status`、`beginDate`、`endDate`、`studentCount`。

`CourseCreateRequest`：`name` 必填；`code`、`term`、`grade`、`beginDate`、`endDate` 可选。课程代码必须唯一，结束日期不能早于开始日期。

教师作业项：`id`、`title`、`description`、`deadline`、`status`、`totalScore`、`standardGroupSize`、`createdAt`、`groupCount`、`submissionCount`。

课程学生候选项：`studentId`、`studentNo`、`name`、`className`、`majorName`、`assignedToGroup`。提供 `assignmentId` 时，它必须属于路径中的课程，`assignedToGroup` 才表示该学生是否已加入该作业的有效团队；不提供时该字段为 `false`。接口先校验当前教师拥有课程，不能作为跨教师学生名册查询。

创建作业：

```json
{
  "title": "小组病例分析",
  "description": "提交病例分析报告",
  "deadline": "2026-09-30T23:59:00",
  "standardGroupSize": 4,
  "totalScore": 100,
  "branches": [
    {"name":"资料检索","description":"证据检索","weight":0.3},
    {"name":"分析与写作","description":"分析和成稿","weight":0.7}
  ]
}
```

- 标题最长 200，描述最长 5000，截止时间必须在未来。
- `standardGroupSize` 为 1–4，`totalScore` 为 1–100。
- 至少一个分支；分支名称最长 255 且忽略大小写后不能重复，描述最长 2000，单项权重 `0..1`，总和在 `1e-6` 容差内等于 1。
- 只有 `DRAFT` 可发布。

团队项：`id`、`name`、`status`、`teamStatus`、`maxMembers`、`leaderName`、`memberCount`。

团队成员项：`studentId`、`studentNo`、`name`、`role`、`status`、`className`、`joinedAt`。接口同时校验作业属于当前教师、团队属于该作业且尚未解散。

建组请求：

```json
{
  "name": "第一组",
  "leaderStudentId": 101,
  "memberStudentIds": [101,102,103,104],
  "description": "病例分析小组"
}
```

组长会自动并入去重后的成员集合；成员总数不得超过 `p+1`，必须都在该课程有效名单中，且不能已经加入该作业其他有效团队。当前接口不要求最少达到 `p` 人。

提交列表项：`id`、`assignmentId`、`groupId`、`groupName`、`content`、`filePath`、`note`、`submitTime`、`status`、`teacherScore`、`teacherComment`、`scorePublished`。`filePath` 是受控 `storageKey`，查看附件必须调用 `GET /files?key=...`，不能直接拼接静态路径。

### 5.2 评分接口 `TeacherScoringController`

| 方法与路径 | 请求 | 成功 `data` | HTTP |
|---|---|---|---|
| `PUT /teacher/submissions/{submissionId}/grade` | `{groupScore,comment?}` | `null` | 200 |
| `PUT /teacher/assignments/{assignmentId}/groups/{groupId}/workload` | `WorkloadAllocationRequest` | `{id,version,status}` | 200 |
| `GET /teacher/assignments/{assignmentId}/groups/{groupId}/workload` | 无 | 最新工作量版本、明细和确认状态 | 200 |
| `POST /teacher/submissions/{submissionId}/scoring-runs` | 无 | 运行详情 | 200 |
| `GET /teacher/scoring-runs/{runId}` | 无 | 运行详情 | 200 |
| `PUT /teacher/scoring-runs/{runId}/review` | `ScoreReviewRequest` | `null` | 200 |
| `POST /teacher/scoring-runs/{runId}/publish` | 无 | `null` | 200 |
| `GET /teacher/appeals` | 无 | 申诉数组 | 200 |
| `PUT /teacher/appeals/{appealId}` | `AppealResolveRequest` | `null` | 200 |

团队评分：`groupScore` 为 `0..100`，`comment` 最长 2000；成功后提交状态为 `GRADED` 并生成待复核运行。输入不满足评分条件时返回真实错误并回滚本次批改，不会直接生成已发布的兜底成绩；模型不可用仍通过确定性规则生成待复核运行。

工作量请求：

```json
{
  "parts": [
    {
      "assignmentPartId": 11,
      "members": [
        {"studentId":101,"share":0.6,"evidence":"负责检索与证据表"},
        {"studentId":102,"share":0.4,"evidence":"负责筛选文献"}
      ]
    }
  ]
}
```

请求必须包含作业全部分支且不重复；每个分支必须包含团队全部成员且不重复；每个 `share` 为 `0..1`，每分支合计在 `1e-6` 容差内等于 1。成功创建递增版本，返回 `{id,version,status:"PENDING_CONFIRMATION"}`。

教师查询工作量返回最新版本摘要 `id/versionNo/status/submittedAt/lockedAt`，以及 `items`（`assignmentPartId/partName/studentId/studentName/share/evidence`）和 `confirmations`（`studentId/studentName/status/comment/confirmedAt`）。查询同样校验作业和团队属于当前教师；尚无版本时返回 404。

发起评分前置条件：已录入团队分、有可用规则、存在全员确认后锁定的工作量版本。相同输入哈希和模型版本重复运行返回 409。

运行详情字段：

```json
{
  "id": 1,
  "runNo": "RUN-...",
  "status": "MODEL_COMPLETE",
  "assignmentId": 1,
  "groupId": 2,
  "modelVersionId": 3,
  "inputHash": "...",
  "errorMessage": null,
  "startedAt": "...",
  "completedAt": "...",
  "results": [
    {
      "id": 10,
      "studentId": 101,
      "studentName": "李同学",
      "contribution": 0.62,
      "groupScore": 80,
      "ruleScore": 85.2,
      "modelScore": 85.5,
      "modelRuleDelta": 0.3,
      "anomaly": false,
      "finalScore": 85.5,
      "status": "DRAFT",
      "explanation": {},
      "reviewReason": null
    }
  ]
}
```

复核请求必须一次包含本次运行全部结果且 ID 一一对应：

```json
{
  "decisions": [
    {"scoreResultId":10,"finalScore":86.0,"reason":"结合原始作品复核后调整"}
  ]
}
```

`finalScore` 为 `0..100`。若与当前建议分相差超过 `0.005`，`reason` 必填且最长 500。全部结果成功变为 `REVIEWED` 后运行变为 `APPROVED`。只有 `APPROVED` 且全部结果为 `REVIEWED` 才可发布，否则返回 409；非归属运行返回 404。

发布只作用于路径中的 `runId`、该运行的个人成绩、对应提交和查询投影，不联动同一作业的其他小组或历史运行。重复发布已经发布的运行返回 200，但不修改发布时间、审计或投影，也不重置申诉后的成绩。复核与发布通过数据库行锁串行处理。

教师成绩列表增加 `runStatus`，表示当前返回的 `scoringRunId` 状态；`scorePublished` 随当前运行计算，不能沿用更早运行的已发布标记。教师页面按小组逐人复核、单独发布，展示当前结果状态。

教师申诉项：`id`、`scoreResultId`、`studentId`、`studentName`、`assignmentTitle`、`reason`、`evidence`、`status`、`resolution`、`submittedAt`、`resolvedAt`、`currentScore`。

处理请求：

```json
{
  "status": "ACCEPTED",
  "resolution": "复核工作量证据后接受申诉",
  "adjustedScore": 88.0
}
```

`status` 只能为 `ACCEPTED` 或 `REJECTED`；`resolution` 必填、最长 2000。接受时 `adjustedScore` 必填、为 `0..100` 且必须与当前有效分不同；服务端向 `score_revision` 追加修订并同步查询投影，不修改原始已发布结果。驳回时可省略分数。

## 6. 学生接口

本节全部要求 `STUDENT`，资源按当前学生 ID 过滤。

### 6.1 教学接口 `StudentController`

| 方法与路径 | 请求 | 成功 `data` | HTTP |
|---|---|---|---|
| `GET /student/courses` | 无 | 课程数组 | 200 |
| `GET /student/courses/{courseId}/assignments` | 无 | 作业数组 | 200 |
| `GET /student/groups` | 无 | 团队数组 | 200 |
| `GET /student/groups/{groupId}/members` | 无 | 本人团队成员数组 | 200 |
| `POST /student/assignments/{assignmentId}/submission` | `StudentSubmissionRequest` | `null` | 200 |

课程项：`id`、`code`、`name`、`term`、`grade`、`beginDate`、`endDate`、`teacherName`。

作业项：`id`、`title`、`description`、`deadline`、`status`、`totalScore`、`standardGroupSize`、`submitted`。只返回学生有效选课中已发布/结束态作业。

团队项：`id`、`name`、`assignmentId`、`teamStatus`、`leaderStudentId`。成员详情字段与教师端一致；后端要求当前学生是该未解散团队的有效成员，否则返回 404。

提交请求：

```json
{
  "content": "团队报告正文",
  "filePath": "2026/07/student/101/uuid.pdf",
  "note": "最终版"
}
```

`content` 最长 20000，`filePath` 最长 500，`note` 最长 1000。正文和附件至少一项非空；学生必须属于该作业有效团队；作业必须已发布且未过截止时间。处于 `DRAFT/SUBMITTED/RETURNED` 且成绩未发布时可覆盖最新提交；进入 `GRADED` 或已发布后返回 409，避免覆盖评分依据。

### 6.2 评分接口 `StudentScoringController`

| 方法与路径 | 请求 | 成功 `data` | HTTP |
|---|---|---|---|
| `GET /student/assignments/{assignmentId}/workload` | 无 | 工作量版本与明细 | 200 |
| `PUT /student/assignments/{assignmentId}/groups/{groupId}/workload` | `WorkloadAllocationRequest` | `{id,version,status}` | 200 |
| `PUT /student/workload-sets/{setId}/confirmation` | `{status,comment?}` | `null` | 200 |
| `GET /student/scores` | 无 | 已发布成绩数组 | 200 |
| `GET /student/appeals` | 无 | 本人申诉数组 | 200 |
| `POST /student/scores/{scoreResultId}/appeals` | `AppealCreateRequest` | `null` | 200 |

团队组长可用与教师相同的 `WorkloadAllocationRequest` 提交新方案；服务端校验组长身份、全部分支、全部成员和每分支份额和。工作量响应：`id`、`versionNo`、`status`、`submittedAt`、`myConfirmation`、`comment`，以及 `items` 数组。明细项为 `assignmentPartId`、`partName`、`studentId`、`studentName`、`share`、`evidence`。

确认请求：

```json
{"status":"OBJECTED","comment":"第二分支的份额与实际记录不一致"}
```

状态只能为 `CONFIRMED` 或 `OBJECTED`；异议时 `comment` 必填，最长 1000。只有工作量版本仍为 `PENDING_CONFIRMATION` 时可操作；全部成员确认后服务端自动锁定。

成绩数组只包含 `PUBLISHED` 项，字段：`scoreResultId`、`assignmentId`、`assignmentTitle`、`courseName`、`groupName`、`groupScore`、`contribution`、`ruleScore`、`modelScore`、`finalScore`、`explanation`、`scoringSnapshot`、`reviewReason`、`publishedAt`、`runNo`、`modelVersion`、`ruleVersion`。`finalScore` 优先取最新 `score_revision`，原始已发布 `score_result` 不被覆盖。

申诉请求：

```json
{
  "reason": "贡献度与已确认的分工证据不一致",
  "evidence": "证据说明或受控附件引用"
}
```

`reason` 必填、最长 2000，`evidence` 最长 5000。只能申诉本人已发布成绩，期限为 `publishedAt` 后 7 天；同一成绩已有 `SUBMITTED/PROCESSING` 申诉时不能重复提交。`GET /student/appeals` 返回 `id/scoreResultId/assignmentTitle/reason/evidence/status/resolution/submittedAt/resolvedAt/currentScore`。

## 7. 内部算法服务契约

这不是 Java Controller，但 Spring 评分流程依赖它。算法服务不使用统一 Java 响应包。

| 方法与路径 | 鉴权 | 说明 |
|---|---|---|
| `GET /health` | 公开但只应在内网开放 | 返回 `status`、`ruleVersion`、`modelAvailable`、`modelReason`。 |
| `GET /v1/model` | `Bearer ALGORITHM_SERVICE_TOKEN` | 返回模型可用性、metadata、metrics 和哈希清单。 |
| `POST /v1/predict` | 同上 | 批量规则/模型预测，1–1000 项。 |

预测请求：

```json
{
  "items": [
    {
      "studentId": "101",
      "contribution": 0.62,
      "standardGroupSize": 4,
      "actualGroupSize": 4,
      "groupScore": 80
    }
  ]
}
```

预测响应：

```json
{
  "ruleVersion": "paper-mcqs-v1",
  "modelVersion": "xgb-p4-seed2025-...",
  "artifactSha256": "172a3cf9486e2fa88c0d37c0401844521abdf426a6f17bf66271edb2154c162d",
  "items": [
    {
      "studentId": "101",
      "ruleScore": 85.2,
      "modelScore": 85.5,
      "finalSuggestion": 85.5,
      "mode": "model",
      "delta": 0.3,
      "anomaly": false,
      "explanation": "xgboost_prediction"
    }
  ]
}
```

`mode` 为 `model`、`rule_fallback` 或 `rule_only`。Java 同时校验 `ruleVersion`、`modelVersion`、`artifactSha256`、成员集合和分数范围，任一不匹配整批回退规则分。未配置内部 Token 时 `/v1/*` 返回 503，Token 错误返回 401，请求校验失败返回 FastAPI 422。

## 8. 当前明确没有的接口

为避免前端误判，当前 Java Controller 没有：

- 管理员增删改院系、专业、班级或课程的接口；管理员教学数据页是只读。
- 文件内联预览和删除接口；受鉴权的附件下载已经实现。
- 教师/学生专用工作台聚合接口。
- 作业结束/归档、申诉转 `PROCESSING/CANCELLED` 或学生撤销申诉的接口。

这些能力属于团队后续。新增前先设计权限与状态，再更新本契约。
