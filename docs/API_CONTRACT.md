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
| `POST /files` | 任一已登录角色 | `multipart/form-data`，字段 `file`，可选字段 `name` | `StoredObject` | 200 |
| `GET /files?key={storageKey}` | 具有附件读取权限的已登录角色 | 查询参数 `key` | 原始文件流 | 200 |

```json
{
  "storageKey": "2026/07/student/35/7fb0c3f0-....pdf",
  "originalName": "团队报告.pdf",
  "size": 120045,
  "sha256": "64位十六进制哈希"
}
```

允许扩展名：`pdf`、`doc`、`docx`、`xls`、`xlsx`、`ppt`、`pptx`、`zip`、`rar`、`txt`、`md`、`png`、`jpg`、`jpeg`。单文件上限 20 MB，请求上限 25 MB。返回的是受控 `storageKey`，不是公开 URL。

可选字段 `name` 是原始文件名的 `encodeURIComponent` 结果（纯 ASCII）。服务端优先采用它作为 `StoredObject.originalName`，避免部分容器把 multipart 头里的中文文件名按 ISO-8859-1 误解码；未传时回退到 multipart 文件名并做 mojibake 修复。文件名在入库和写入响应头前都会清洗路径分隔符、控制字符和引号。

**`GET /files` 不是匿名静态资源**：它落在 `SecurityConfig` 的 `anyRequest().authenticated()` 下，必须携带 `Authorization: Bearer <access token>`。前端一律通过 Axios 以 `responseType: 'blob'` 下载并用 `a.download` 指定文件名，禁止用裸 `<a href="/api/v1/files?...">`、`window.open` 或 `<img src>` 直接跳转，否则会得到 401。

下载文件名取自 `submission_attachment.original_name`（即上传时记录的真实文件名），回退到磁盘上的存储文件名；响应为带 UTF-8 文件名的 `Content-Disposition: attachment`，并设置 `X-Content-Type-Options: nosniff`。管理员可读取任意受控附件；上传者可读取自己角色/ID目录下的附件；教师和学生读取他人上传附件时还要通过课程、作业、团队或成绩关系校验。**附件的可见性按提交状态分段**：`DRAFT` 提交上的附件只有该小组 `ACTIVE` 成员能列出和下载；教师侧的提交列表、附件列表和下载权限都排除 `DRAFT`，只有小组正式提交（`SUBMITTED` 及之后）教师才可见。无读取权限返回 403，文件不存在返回 404。当前没有浏览器内联预览或删除接口。

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
| `DELETE /admin/accounts/{id}` | `role`（必填） | `{id,role,username,mode}` | 200 |
| `DELETE /admin/organization/{type}/{id}` | `type` 为 `department/major/class` | `{id,type,name}` | 200 |
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

班级按**学院维度统一编号**，本身不强制归属专业：`classes[].majorId` 优先取 `class.major_id`，为空时由该班在册学生人数最多的专业推断，推断不到返回 `null`（班内没有学生，或学生都没填专业）。同号班级在学院内唯一，不同专业的学生可以属于同一班号。前端以 `majorId` 把班级渲染成「院系 › 专业 › 班级」三级树，`majorId=null` 的班级收进该学院下的「未分配专业」分组，不会从树上消失。院系、专业、班级均返回实时 `memberCount`（未注销的教师/学生人数），前端据此只对 `memberCount=0` 的节点提供删除入口。

```json
{
  "departments": [
    {
      "id": 1,
      "name": "基础医学院",
      "status": 1,
      "memberCount": 12,
      "majors": [
        {"id": 2, "departmentId": 1, "name": "临床医学", "status": 1, "memberCount": 5}
      ],
      "classes": [
        {"id": 3, "departmentId": 1, "name": "1", "grade": null, "status": 1, "memberCount": 0, "majorId": null},
        {"id": 4, "departmentId": 1, "name": "7", "grade": null, "status": 1, "memberCount": 7, "majorId": 2}
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
  "departmentName": "生物医学工程学院",
  "majorName": "智能科学与技术",
  "className": "24莞信科01班",
  "grade": 2024
}
```

学院、专业、班级三个组织维度都支持**按名称**提交：`departmentName`、`majorName`、`className`（各最长 100 字符），服务端 `trim` 后按名称复用，不存在时自动创建并启用，因此管理员录入账号时可以直接把新学院、新专业、新班级敲进去，不需要先去组织树建档。三者仍保留 `departmentId`/`majorId`/`classId` 供 CSV 导入与旧客户端使用，**名称与 ID 同时提供时以名称为准**；使用 ID 时会校验其归属与启用状态。

`className` 是**学院维度**的班级名称（如 `24莞信管01班`），与 `majorName` 互相独立：名称在该学院下唯一，不存在「班级号」概念，不同专业的学生也可以属于同一个班级。班级不写 `major_id`，组织树中的专业归属按在册学生的专业推断（无学生的班级会落在「未分配专业」分组）。旧客户端传 `classNumber` 时按 `className` 的别名处理（`7` 等价于 `"7"`）。

- 管理员创建接口仅接受教师和学生角色，登录账号在所有角色之间必须唯一。
- 教师必须填写姓名、工号和所属学院，不得携带专业、班级或年级。
- 学生必须填写姓名、学号、所属学院、专业、班级名称和年级；名称不得为空白，相关学院、专业、班级均须处于启用状态。
- 工号/学号最长 20；年级范围为 2000–2100。未提供初始密码时使用 `ABC` 加登录账号，首次登录必须修改。
- 登录账号不能以 `#` 开头；`#<id>` 是已注销账号的保留占位名。

#### 删除账号（逻辑删除）

`DELETE /admin/accounts/{id}?role=TEACHER|STUDENT`（也接受 `ROLE_*`）。**仅教师和学生可删，管理员账号不可删**。删除不会物理移除数据行：服务端把登录账号改写为保留占位名 `#<id>`、置 `status=0`、写 `deleted_at`/`deleted_by_admin_id`、递增 `tokenVersion` 并撤销全部 Refresh Token；账号随即从账号分页、工作台计数、组织人数、登录和刷新中消失，课程、分组、提交、成绩与审计历史全部保留。原登录账号释放后可重新创建；`#` 前缀禁止用于新账号。成功返回 `{id, role, username, mode:"LOGICAL_DELETE"}`；账号不存在或已注销返回 404，角色为 `ADMIN` 返回 400。已注销账号无法再编辑、启停或重置密码（相应接口返回 404）。删除会写 `ACCOUNT_DELETED` 审计事件。

#### 删除空组织

`DELETE /admin/organization/{type}/{id}`，`type` 取 `department`、`major` 或 `class`（也接受中文名称）。仅当节点**没有成员、没有子节点、也没有业务引用**时才物理删除：

- 班级：无未注销学生、无 `group_table.class_id` 引用；
- 专业：无未注销学生、无下属班级、无 `course.major_id` / `course_major` / `group_table.major_id` 引用；
- 院系：无未注销教师、无下属专业或班级。

有引用时返回 409 并列出具体数量（如「该班级仍有引用，不能删除：7名在册学生、2个关联小组」），节点不存在返回 404，类型不合法返回 400。成功返回 `{id,type,name}` 并写 `ORGANIZATION_DELETED` 审计事件。前端组织树只在 `memberCount=0` 时显示删除按钮，服务端始终重新校验。

- `displayName` 也接受别名 `name`；最长 50。
- `initialPassword` 可省略；若提供须为 8–64 位且含字母和数字。
- 省略或留空密码时，初始密码为 `ABC` 加清理后的登录账号，例如账号 `20260101` 对应 `ABC20260101`。成功响应为 `{username,role,temporaryPassword,mustChangePassword:true}`；初始密码不得写入前端日志。
- `role` 仅接受教师或学生。

更新请求不允许改用户名和角色，只用 `role` 定位物理表；组织维度同样按名称提交，名称不存在时自动创建：

```json
{
  "role": "STUDENT",
  "displayName": "李同学",
  "departmentName": "生物医学工程学院",
  "majorName": "智能科学与技术",
  "className": "24莞信科01班",
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
role,username,displayName,department,major,class,grade,initialPassword
```

`department`、`major`、`class` 直接填写学院名称、专业名称和班级名称（如 `24莞信管01班`），不填写数据库 ID。班级按学院唯一，与 `major` 无关；导入按 `(学院, 班级名称)` 复用班级，不存在的班级自动在该学院下创建并启用。教师行只填写 `department`，学生行还必须填写 `major`、`class` 和 `grade`。`ImportReport` 为 `{totalRows,successRows,failureRows,createdAccounts,errors:[{row,message}]}`。导入按行处理，可能部分成功，不是全文件原子事务。

### 4.4 课程、模型、运行、异常与审计

课程分页项：`id`、`code`、`name`、`term`、`grade`、`status`、`teacherName`、`departmentName`、`beginDate`、`endDate`、`studentCount`、`assignmentCount`、`groupCount`。`teacherName` 为教师姓名模糊查询；`status` 只接受 `ACTIVE/INACTIVE/ARCHIVED`。

模型分页 `status` 支持 `ACTIVE` 或 `INACTIVE`。项字段：`id`、`modelKey`、`name`、`version`、`algorithm`、`status`、`artifactUri`、`artifactSha256`、`metricsJson`、`trainedAt`、`activatedAt`、`createdAt`。

模型注册请求：

```json
{
  "modelKey": "smoothstep-score",
  "version": "smoothstep-xgb-a2b2c30a05b1",
  "artifactUri": "algorithm-service://artifacts/score-model.joblib",
  "artifactSha256": "a2b2c30a05b13b557748825f2d356c4563b1903edf4c53168c1bb46ee0e83644",
  "featureSchemaJson": "[\"标准人数\",\"个人贡献度\",\"作业总分\"]",
  "hyperparametersJson": "{\"n_estimators\":150,\"max_depth\":5,\"learning_rate\":0.07,\"random_state\":42}",
  "metricsJson": "{\"status\":\"ELIGIBLE\",\"holdout\":{\"rmse\":0.8942731732,\"mae\":0.6381276982,\"r2\":0.9992094930}}"
}
```

三个 `*Json` 字段必须是 JSON 文本。生产注册契约固定 `modelKey=smoothstep-score`，`artifactUri` 必须匹配 `algorithm-service://artifacts/*.joblib`（旧 `*.json` 产物已下架），特征数组必须严格按 `标准人数, 个人贡献度, 作业总分` 排列，`hyperparametersJson` 必须是对象。`metricsJson` 按 `holdout`、`test`、顶层的顺序读取指标；准入条件为有限且非负的 `RMSE <= 1.0`、`MAE <= 0.8`，以及 `0.99 <= R² <= 1`。若提供 `status`，它必须为 `ELIGIBLE`；若提供 `eligibility.passed`，它不能为 `false`。

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
| `GET /teacher/courses/{courseId}/classes` | `keyword?` | 系统班级库数组 | 200 |
| `GET /teacher/courses/{courseId}/classes/{classId}/students` | 无 | 班级学生名册数组 | 200 |
| `POST /teacher/courses/{courseId}/students/import` | `StudentImportRequest` | `StudentCourseUpdateResult` | 200 |
| `DELETE /teacher/courses/{courseId}/students/{studentId}` | 无 | `null` | 200 |
| `POST /teacher/courses/{courseId}/students/drop` | `StudentDropRequest` | `{requested,removed,skipped}` | 200 |
| `POST /teacher/courses/{courseId}/assignments` | `AssignmentCreateRequest` | `{id,status:"PUBLISHED"}` | **201** |
| `GET /teacher/assignments/{assignmentId}` | 无 | 作业详情（含评价分支与锁定标记） | 200 |
| `PUT /teacher/assignments/{assignmentId}` | `AssignmentUpdateRequest` | `null` | 200 |
| `DELETE /teacher/assignments/{assignmentId}` | 无 | `null` | 200 |
| `POST /teacher/assignments/{assignmentId}/publish` | 无 | `null` | 200 |
| `GET /teacher/assignments/{assignmentId}/groups` | 无 | 团队数组 | 200 |
| `GET /teacher/assignments/{assignmentId}/groups/{groupId}/members` | 无 | 团队成员数组 | 200 |
| `POST /teacher/assignments/{assignmentId}/groups` | `GroupCreateRequest` | `{id,memberCount,status:"CONFIRMED"}` | **201** |
| `GET /teacher/assignments/{assignmentId}/submissions` | 无 | 提交数组 | 200 |

教师课程项：`id`、`code`、`name`、`term`、`grade`、`status`、`beginDate`、`endDate`、`studentCount`。

`CourseCreateRequest`：`name` 必填；`code`、`term`、`grade`、`beginDate`、`endDate` 可选。课程代码必须唯一，结束日期不能早于开始日期。

教师作业项：`id`、`title`、`description`、`deadline`、`status`、`publishedAt`、`totalScore`、`standardGroupSize`、`createdAt`、`groupCount`、`submissionCount`、`submittedGroupCount`、`pendingReviewCount`。列表排除 `ARCHIVED`（已删除）作业。`submissionCount` 是累计提交次数（含同一团队历次重交），只用于展示份数；`submittedGroupCount` 是存在当前有效提交（`is_latest=1`）的团队数，`pendingReviewCount` 是其中 `teacher_score` 为空的团队数。提交进度与待批改指标一律使用后两者，避免把重交次数算成已提交团队数。

课程学生候选项：`studentId`、`studentNo`、`name`、`className`、`majorName`、`assignedToGroup`。提供 `assignmentId` 时，它必须属于路径中的课程，`assignedToGroup` 才表示该学生是否已加入该作业的有效团队；不提供时该字段为 `false`。接口先校验当前教师拥有课程，不能作为跨教师学生名册查询。

**从系统班级库批量导入学生**（教师端「学生管理 → 导入学生」）：

- 班级库由管理员在「组织与账号」或账号 CSV 导入时维护，教师端只读、不创建班级。班级按 `V6` 的「院系下班级唯一」规范组织，`class.major_id` 已废弃，不按专业过滤班级；两个查询接口都先校验课程归属，不能跨教师查询名册。
- `GET /teacher/courses/{courseId}/classes?keyword=` 返回启用中的班级：`id`、`name`、`grade`、`departmentId`、`departmentName`、`studentCount`（该班有效账号数）、`enrolledCount`（其中已在当前课程且 `ACTIVE` 的人数）。`studentCount - enrolledCount` 即本次预计导入人数；`keyword` 匹配班级名、院系名或年级。
- `GET /teacher/courses/{courseId}/classes/{classId}/students` 返回该班有效学生名册：`studentId`、`studentNo`、`name`、`majorName`、`enrolled`（是否已在当前课程）。仅供导入前预览，不分页；班级不存在或已停用返回 404。
- `POST /teacher/courses/{courseId}/students/import` 请求体 `StudentImportRequest`：

```json
{ "classIds": [1, 2], "studentNos": ["20260099"] }
```

  - `classIds` 最多 50 个且必须是 `ACTIVE` 班级；`studentNos` 最多 500 个、每个最长 20 字符。两者至少提供一个，可同时使用；旧客户端仍可只发送 `{ "studentNos": [...] }`。
  - 服务端把班级展开为有效学生账号（`status=1`），与学号解析结果按 `studentId` 去重合并；合并后单次最多 2000 人，超出返回 400 并要求分批。
  - 已在课程且 `ACTIVE` 的跳过，`DROPPED`/`INACTIVE` 的重新激活（`join_method='IMPORT'`）；学号查不到账号的记入 `notFound`，不阻断其余导入。
  - 响应 `StudentCourseUpdateResult` 为 `{success, skipped, notFound: string[]}`；写入 `STUDENTS_IMPORTED` 审计，记录成功/跳过/未找到数量与所选班级 ID。
- `DELETE /teacher/courses/{courseId}/students/{studentId}` 把 `student_course.status` 置为 `DROPPED`，不删除学生账号。前端按学号在本地名单中解析出 `studentId` 后调用。
- `POST /teacher/courses/{courseId}/students/drop` 是批量版本，供「学生管理」勾选多人后一次移除：请求体 `{ "studentIds": [12, 15, 18] }`，`studentIds` 必填、去重后最多 500 个；后端用单条 `UPDATE ... WHERE status='ACTIVE' AND student_id IN (...)` 处理，返回 `{requested, removed, skipped}`，`skipped` 是本来就不在课程中的条数，不算失败。前端不要循环调用单条 `DELETE`，避免中途失败只删了一半。两种移除都不删除学生账号，写入 `STUDENTS_DROPPED` 审计。

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
- 创建后即为 `PUBLISHED` 并同时写入 `ASSIGNMENT_CREATED`、`ASSIGNMENT_PUBLISHED` 审计；`publish` 接口只用于把历史 `DRAFT` 数据改为 `PUBLISHED`。

### 5.2 查看、编辑与删除作业

`GET /teacher/assignments/{assignmentId}` 返回作业基础字段、`courseId`、`groupCount`、`submissionCount`，以及：

```json
{
  "partsLocked": false,
  "scorePublished": false,
  "parts": [
    {"id": 11, "name": "资料检索", "description": "证据检索", "weight": 0.3, "sortOrder": 1}
  ]
}
```

`partsLocked` 表示作业已有团队、工作量、提交或评分运行记录；`scorePublished` 表示已有发布的成绩。前端据此禁用对应控件，服务端也会独立校验，不信任前端。

`PUT` 使用 `AssignmentUpdateRequest`，字段与 `AssignmentCreateRequest` 相同（`branches` 为完整列表而非增量）。约束：

- 作业必须属于当前教师且未归档；否则分别返回 404 / 409。
- 权重总和与分支名唯一性规则和创建一致。
- `partsLocked=true` 时，评价分支（名称、权重、数量）与 `standardGroupSize` 必须与库中现有值一致，否则 409；此时服务端不会重写 `assignment_parts`，避免破坏 W/Q 矩阵与已算出的贡献。
- `scorePublished=true` 时 `totalScore` 不得修改，否则 409。
- 截止时间可原样保留（即使已过期）；一旦改为其他值，必须晚于当前时间，否则 400。
- 成功后写入 `ASSIGNMENT_UPDATED` 审计，记录 `courseId`、`partsLocked` 和分支数。

`DELETE` 是**归档而非物理删除**：把 `assignment.status` 置为 `ARCHIVED`，写入 `ASSIGNMENT_ARCHIVED` 审计，不改动任何提交、团队、评分、快照或审计记录。理由是多张下游表对 `assignment` 使用 `ON DELETE RESTRICT`，且已发布评分与审计只追加。归档后：教师作业列表不再返回；学生端作业列表/详情只认 `PUBLISHED`/`CLOSED`，因此不可见；已发布成绩仍通过 `score_result.status='PUBLISHED'` 查询得到，可继续追溯。学生成绩页提供申诉入口，不依赖作业详情页，归档作业后仍可申诉。重复删除或编辑已归档作业返回 409。

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

提交列表项：`id`、`assignmentId`、`groupId`、`groupName`、`leaderName`、`content`、`filePath`、`note`、`submitTime`、`status`、`teacherScore`、`teacherComment`、`scorePublished`、`attachments`。`leaderName` 是组长姓名，优先取 `group_table.leader_student_id` 对应学生；历史小组未写该字段时回退到 `group_member` 中 `ACTIVE` 且 `role='LEADER'` 的成员，仍没有则为 `null`（教师批改页只显示组名）。批改页将其显示为「第10组——演示学生05」。`filePath` 是受控 `storageKey`，查看附件必须调用 `GET /files?key=...`，不能直接拼接静态路径。

### 5.2 评分接口 `TeacherScoringController`

| 方法与路径 | 请求 | 成功 `data` | HTTP |
|---|---|---|---|
| `PUT /teacher/submissions/{submissionId}/grade` | `{groupScore,comment?}` | `{scoringSkipped,scoringRunId?,scoringStatus?,contributionSource?,memberCount?}` 或 `{scoringSkipped:true,scoringMessage}` | 200 |
| `PUT /teacher/assignments/{assignmentId}/groups/{groupId}/workload` | `WorkloadAllocationRequest` | `{id,version,status}` | 200 |
| `GET /teacher/assignments/{assignmentId}/groups/{groupId}/workload` | 无 | 最新工作量版本、明细和确认状态 | 200 |
| `POST /teacher/submissions/{submissionId}/scoring-runs` | 无 | 运行详情 | 200 |
| `GET /teacher/scoring-runs/{runId}` | 无 | 运行详情 | 200 |
| `POST /teacher/scoring-runs/{runId}/publish` | 无 | `null` | 200 |
| `GET /teacher/appeals` | 无 | 申诉数组 | 200 |
| `PUT /teacher/appeals/{appealId}` | `AppealResolveRequest` | `null` | 200 |

团队评分：`groupScore` 为 `0..100`，`comment` 最长 2000；保存成功后提交状态为 `GRADED`，**同一请求内立即按分工比例运行一次评分**并返回本次运行信息（`scoringRunId`、`scoringStatus`、`memberCount`），个人成绩随后在「成绩查看」一键发布（发布即采用算法分）。`contributionSource` 为 `workload`（采用最新 `LOCKED` 分工）或 `equal_split`（该组从未保存分工，按组内均分计算）。评分前置条件不满足（例如作业没有评价分支、小组没有有效成员）时不回滚团队成绩，返回 `{scoringSkipped:true,scoringMessage}`，教师补录后再点「重新运行评分」即可。输入不满足评分条件时不会生成已发布的兜底成绩；模型不可用仍通过确定性规则生成待复核运行。

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

请求必须包含作业全部分支且不重复；每个分支必须包含团队全部成员且不重复；每个 `share` 为 `0..1`（DTO 用 `@DecimalMin("0")`/`@DecimalMax("1")` 拦截负数与超过 100% 的值），每分支合计在 `1e-6` 容差内等于 1。成功创建递增版本，返回 `{id,version,status:"LOCKED"}`。

**学生端填写的钳位规则（前端 `frontend/src/utils/workload.js`，仅影响 UI，不改变接口语义）**：一行（一个评价分支）总额上限 100 个百分点，先填的成员先占额度，后面的成员最多只能填「100 − 同行其他成员合计」。输入 `140` 会被钳成 `100`；A 已填 `100` 后，B/C/D 输入任何正数都变成 `0`；把 A 改小后其余成员的可填额度随之恢复。份额保留两位小数（`clampCellValue` / `cellLimit`），保存时仍按 `share = 百分比 / 100` 传给后端，因此后端拿到的永远是合法值；历史数据或成员变动后回填时也按同样规则逐格钳位，保证一行不会超过 100。

教师查询工作量返回最新版本摘要 `id/versionNo/status/submittedAt/lockedAt`，以及 `items`（`assignmentPartId/partName/studentId/studentName/share/evidence`）和 `confirmations`（`studentId/studentName/status/comment/confirmedAt`）。查询同样校验作业和团队属于当前教师；尚无版本时返回 404。

发起评分前置条件：已录入团队分、有可用规则、存在已锁定的工作量版本（组长保存或教师补录）。相同输入哈希和模型版本重复运行返回 409。

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

**逐人复核流程已取消**：`PUT /teacher/scoring-runs/{runId}/review` 与 `ScoreReviewRequest` 已删除。发布是教师的唯一出口，服务端按状态自动分流：

- `MODEL_COMPLETE` / `DEGRADED`：先把本次运行 `DRAFT` 结果的 `final_score` 原样保留（`manual_adjustment=0`）、`status` 置为 `REVIEWED`、`review_reason` 写「发布时采用算法建议分（未人工调整）」，再走 `APPROVED`，写一条 `SCORING_RUN_AUTO_APPROVED` 审计；没有可发布的草稿结果返回 409。
- `APPROVED`：直接发布，仍要求全部结果为 `REVIEWED`。
- 其他状态（`PENDING_REVIEW`、`FAILED` 等）返回 409，非归属运行返回 404。

发布只作用于路径中的 `runId`、该运行的个人成绩、对应提交和查询投影，不联动同一作业的其他小组或历史运行。重复发布已经发布的运行返回 200，但不修改发布时间、审计或投影，也不重置申诉后的成绩。发布通过数据库行锁串行处理。需要人工改分的场景走「成绩申诉」，不再经过复核弹窗。

教师成绩列表增加 `runStatus`，表示当前返回的 `scoringRunId` 状态；`scorePublished` 随当前运行计算，不能沿用更早运行的已发布标记。教师页面按小组分段展示（按组号升序、未分组垫尾），一次可发布该作业全部待发布小组；前端对每个小组各发一次发布请求，某个组失败不影响其他组。

教师申诉项：`id`、`scoreResultId`、`studentId`、`studentName`、`courseId`、`assignmentTitle`、`reason`、`evidence`、`status`、`resolution`、`submittedAt`、`resolvedAt`、`currentScore`。

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
| `POST /student/assignments/{assignmentId}/draft-attachments` | `DraftAttachmentRequest` | `null` | 201 |
| `DELETE /student/assignments/{assignmentId}/attachments/{attachmentId}` | 无 | `null` | 200 |
| `POST /student/assignments/{assignmentId}/submission` | `StudentSubmissionRequest` | `null` | 200 |

课程项：`id`、`code`、`name`、`term`、`grade`、`beginDate`、`endDate`、`teacherName`。

作业项：`id`、`title`、`description`、`deadline`、`status`、`totalScore`、`standardGroupSize`、`teamFormation`、`submitted`、`scorePublished`。只返回学生有效选课中已发布/结束态作业。`teamFormation` 为兼容旧客户端保留，**恒为 `false`**：系统已取消「发布后 2 天组队期」，不再有独立的组队阶段。

**统一窗口（发布 → 截止）**：作业发布后到 `deadline` 之前，学生随时可以创建/加入/退出小组、填写分工、登记附件、提交作业；加入小组后即可直接填写并提交，无需等待任何组队期。到达 `deadline` 后学生端所有写操作（建组、加入、退出、分工保存、附件登记与删除、作业提交）统一返回 409。分工与作业提交是两个独立动作：组长在 6.2 的接口单独保存分工矩阵，保存即 `LOCKED`，不需要组员确认，也不随作业提交一起发送；教师端保留补录分工的入口，否则从未填写过分工的小组将无法评分。前端不应再用 `publishedAt + 2 天` 自行推算组队期。

教师批改后，该小组的成员和学生分工填写入口锁定。批改前变更成员会将原分工标记为已被替代，组长可按新名单提交分工，已保存的历史版本保留。小组列表使用 `gradingLocked` 标记是否已因批改锁定。

**可加入小组列表**（`GET /student/assignments/{assignmentId}/groups`）每项返回 `id`、`name`、`groupNo`、`maxMembers`、`memberCount`、`gradingLocked`、`leaderName`。`groupNo` 由「第N组」解析得到，用于按数字搜索；`leaderName` 取 `group_table.leader_student_id` 对应的 `student_users.name`，该字段缺失时回退到 `group_member` 中 `role='LEADER'` 的有效成员，都没有则为 `null`。学生端卡片显示为「第1组 | 演示学生01」，组长转让或退出后随下一次请求更新。

**提交是小组级资源，不是成员级资源**：`submission` 由唯一键 `(assignment_id, group_id, is_latest)` 约束，一个小组同一作业只有一条当前提交。任意 `ACTIVE` 成员提交后，该组所有成员的作业列表与作业详情都会读到同一个 `submitted=true`、`content`、`submitNote`、`attachments` 和 `submissionStatus`（`content` 与 `submitNote` 是同一段「提交说明」的两个兼容字段，见下文）；`submitterId` 只记录是谁点的提交，不决定可见性。列表的 `submitted` 与 `scorePublished` 都通过 `group_member` 关联本组，并排除 `DRAFT`（只上传了草稿附件不算已提交）；`scorePublished` 只认本组 `scoring_run`，不会因其他小组发布成绩而把全作业学生变成"已批阅"。

作业详情另外返回 `groupId`、`groupName`、`memberRole`、`members`、`parts`、`filePath`、`submitterId`、`submitTime`、`submitNote`、`submissionStatus`、`submitted`、`teacherScore`、`teacherComment`、`attachments`。`submissionStatus` 取值 `DRAFT / SUBMITTED / RETURNED / GRADED / ARCHIVED`；`RETURNED` 仍属于"已提交过"，但前端提示重新提交。`attachments` 按本组 `ACTIVE` 成员关系返回，不按上传者过滤，组内每个成员都能列出并下载。

团队项：`id`、`name`、`assignmentId`、`teamStatus`、`leaderStudentId`。成员详情字段与教师端一致；后端要求当前学生是该未解散团队的有效成员，否则返回 404。

提交请求：

```json
{
  "content": "本次完成 KNN 实验，报告与代码见附件",
  "filePath": "2026/07/student/101/uuid.pdf",
  "note": "本次完成 KNN 实验，报告与代码见附件",
  "attachments": [
    {
      "storageKey": "2026/07/student/101/7fb0c3f0-....pdf",
      "originalName": "团队报告.pdf",
      "fileSize": 120045
    }
  ]
}
```

`attachments` 最多 20 项，每项必须是当前学生账号上传得到的 `storageKey`（`年/月/student/{id}/{uuid}.{ext}` 格式）。`originalName` 与 `fileSize` 直接取 `POST /files` 返回的 `originalName` 与 `size`，服务端清洗后写入 `submission_attachment`，教师端列表和下载响应头都使用这个名字。旧客户端仍可只传字符串数组 `["2026/07/student/101/uuid.pdf"]`，此时数据库只记录存储文件名，前端会回退展示为「附件 N.ext」；新实现不要再用这种形式，否则教师看到的仍是 UUID 文件名。同一 `storageKey` 重复提交时按唯一键更新文件名与大小。

**草稿附件（组内协作阶段，推荐路径）**：**当前版本学生端已暂时移除附件上传入口**，本节接口保持可用、历史附件数据保留（学生端与教师端仍可下载），恢复上传 UI 即可重新启用。学生上传文件后应立即调用 `POST /student/assignments/{assignmentId}/draft-attachments`，请求体为 `{"storageKey":"...","originalName":"报告.pdf","fileSize":120045}`。服务端校验 `storageKey` 必须由当前学生账号上传（`年/月/student/{id}/{uuid}.{ext}`）且该学生属于该作业有效团队的 `ACTIVE` 成员，然后确保存在一条 `status='DRAFT'` 的最新提交并把附件写入 `submission_attachment`。**此时附件全组成员都能列出和下载，教师侧的提交列表、附件列表与下载权限都排除 `DRAFT`。** 点击「提交作业」时 `POST .../submission` 会把同一条提交从 `DRAFT` 改为 `SUBMITTED`，附件无需重传即转为教师可见，同时全组成员的 `submitted` 一起变为 `true`；草稿附件计入「提交说明或附件至少一项」校验。作业详情的 `submissionStatus='DRAFT'` 不代表已提交。若团队已正式提交过，再登记附件会直接挂在该提交上并立即对教师可见。`DELETE .../attachments/{attachmentId}` 允许本组 `ACTIVE` 成员在**提交窗口内（未过 `deadline`、未批阅、成绩未发布）**删除本组当前最新提交（`DRAFT`/`SUBMITTED`/`RETURNED`）的任意附件，包含「重新提交」编辑态下先前已提交的附件；服务端先用与作业详情一致的 `studentGroup` 解析 `groupId`，再按 `sa.id + assignment_id + group_id + is_latest=1` 定位，避免删到别组附件。进入 `GRADED`、成绩已发布或超过 `deadline` 后附件冻结（删除返回 404／409）。删除后全组成员重新拉取详情即同步；上传者本人仍可通过 `GET /files?key=...` 读取自己账号目录下的文件。

**提交说明就是提交内容，界面上没有单独的「正文」字段**：组长在「选题题目」里填写内容，前端把它同时写入 `content` 与 `note`。`submission.content` 与 `submission.submit_note` 保存同一段文本，教师端与作业详情读到的都是这段说明。**提交说明与附件至少一项非空**，否则返回 400「请填写提交说明或上传附件」；已有附件按当前最新提交统计，附件型小组重新提交无需重传。`content` 最长 20000，`filePath` 最长 500，`note` 最长 1000。学生必须属于该作业有效团队且为 `LEADER`，其他成员提交返回 403「只有小组组长可以编辑并提交作业」；作业必须已发布且未过截止时间。处于 `DRAFT/SUBMITTED/RETURNED` 且成绩未发布时可覆盖最新提交；进入 `GRADED` 或已发布后返回 409，避免覆盖评分依据。

**分工不属于提交请求**：`StudentSubmissionRequest.contributionScores` 仅为旧客户端兼容保留，服务端已忽略，提交作业不会写入或覆盖工作量版本。分工必须通过 6.2 的 `PUT /student/assignments/{assignmentId}/groups/{groupId}/workload` 单独保存，由组长按评价分支填写矩阵，保存即 `LOCKED`；组员无需确认，也没有异议接口。需要按成员一个总百分比分配时，前端自行折算为各分支相同份额，后端的校验仍是「每个分支成员份额之和为 1」。**提交前必须已存在本组该作业的 `LOCKED` 分工版本**：`POST /student/assignments/{assignmentId}/submission` 先校验组长身份，再校验 `workload_set` 中存在 `assignment_id + group_id + status='LOCKED'` 的记录，否则返回 400「请先在「小组分工」中填写并保存贡献度，再提交作业」；成员变动或重新保存把旧版本改成 `SUPERSEDED` 后也会命中该错误，需先重新保存分工。

### 6.2 评分接口 `StudentScoringController`

| 方法与路径 | 请求 | 成功 `data` | HTTP |
|---|---|---|---|
| `GET /student/assignments/{assignmentId}/workload` | 无 | 工作量版本与明细 | 200 |
| `PUT /student/assignments/{assignmentId}/groups/{groupId}/workload` | `WorkloadAllocationRequest` | `{id,version,status}` | 200 |
| `PUT /student/workload-sets/{setId}/confirmation` | `{status,comment?}` | `null` | 200（历史 PENDING 版本兼容，新版不再产生） |
| `GET /student/scores` | 无 | 已发布成绩数组 | 200 |
| `GET /student/appeals` | 无 | 本人申诉数组 | 200 |
| `POST /student/scores/{scoreResultId}/appeals` | `AppealCreateRequest` | `null` | 200 |

团队组长可用与教师相同的 `WorkloadAllocationRequest` 提交新方案；服务端校验组长身份、全部分支、全部成员和每分支份额和，保存成功后直接生成 `LOCKED` 版本（写入 `locked_at`），并把同组同作业的旧版本（含上一个 `LOCKED`）置为 `SUPERSEDED`。每组同一时刻只有一个非 `SUPERSEDED` 版本，评分只读取最新的 `LOCKED` 版本。截止后学生保存返回 409；小组已批改或成绩已发布后同样返回 409，教师端补录仍可用。

工作量响应：`id`、`versionNo`、`status`、`submittedAt`、`lockedAt`、`autoLocked`，以及 `items` 数组。`myConfirmation`、`comment`、`objections` 字段仅为历史 `PENDING_CONFIRMATION` 数据保留，新版恒为 `null` 或空数组，前端不应再展示确认/异议入口。`autoLocked=true` 表示该版本是在 `assignment.deadline` 到达时被系统自动锁定（`locked_at >= deadline`），而不是保存时锁定；正常保存的版本 `locked_at` 早于截止时间，学生端显示为「已保存」。明细项为 `assignmentPartId`、`partName`、`studentId`、`studentName`、`share`、`evidence`。

`PUT /student/workload-sets/{setId}/confirmation` 仅为兼容历史 `PENDING_CONFIRMATION` 版本保留；新版版本保存即 `LOCKED`，调用返回 409，学生端不再提供确认/异议入口。

确认请求：

```json
{"status":"OBJECTED","comment":"第二分支的份额与实际记录不一致"}
```

状态只能为 `CONFIRMED` 或 `OBJECTED`；异议时 `comment` 必填，最长 1000。仅用于历史 `PENDING_CONFIRMATION` 版本（含截止自动锁定前的存量数据），新版保存即 `LOCKED`，因此该接口对新版本一律返回 409。学生端不再调用此接口。

成绩数组只包含 `PUBLISHED` 项，字段：`scoreResultId`、`studentId`、`assignmentId`、`assignmentTitle`、`courseId`、`courseName`、`groupName`、`groupScore`、`contribution`、`ruleScore`、`modelScore`、`finalScore`、`explanation`、`scoringSnapshot`、`reviewReason`、`publishedAt`、`runNo`、`modelVersion`、`ruleVersion`。`finalScore` 优先取最新 `score_revision`，原始已发布 `score_result` 不被覆盖。

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

预测请求（`items` 1–1000 项；`contribution` 为 0–1 的比例，`groupScore` 为 0–100 的分数，`standardGroupSize` 为 1–4）：

```json
{
  "items": [
    {
      "studentId": "101",
      "contribution": 0.6,
      "standardGroupSize": 2,
      "groupScore": 60
    }
  ]
}
```

请求体 `extra="forbid"`：**不再有 `actualGroupSize`**，附带该字段会返回 422。模型覆盖 `p=1..4` 全部标准组规模，没有 `p=4` 门控。

预测响应：

```json
{
  "ruleVersion": "smoothstep-v1",
  "modelVersion": "smoothstep-xgb-a2b2c30a05b1",
  "artifactSha256": "a2b2c30a05b13b557748825f2d356c4563b1903edf4c53168c1bb46ee0e83644",
  "items": [
    {
      "studentId": "101",
      "ruleScore": 62.08,
      "modelScore": 61.82,
      "finalSuggestion": 61.82,
      "mode": "model",
      "delta": 0.26,
      "anomaly": false,
      "explanation": "xgboost_prediction"
    }
  ]
}
```

`mode` 只有 `model` 与 `rule_fallback` 两种，**不再返回 `rule_only`**；`modelVersion`/`artifactSha256` 在模型不可用时为 `null`。所有 `ruleScore`、`modelScore`、`finalSuggestion` 均裁剪在 `0..100`，超出范围的请求值直接被拒。Java 同时校验 `ruleVersion`、`modelVersion`、`artifactSha256`、成员 ID 集合和分数范围，任一不匹配整批回退规则分；`|modelScore − ruleScore| > 2` 才标 `anomaly=true`。未配置内部 Token 时 `/v1/*` 返回 503，Token 错误返回 401，请求校验失败返回 FastAPI 422。

Java 保存的 `explanation` JSON 字段为：`ruleVersion`、`branch`（`LOW_CONTRIBUTION`/`HIGH_CONTRIBUTION`）、`threshold`（`1/p`）、`contribution`、`normalizedContribution`、`boostCap`（20）、`scoreRange`（`[0,100]`）、`mode`、`modelExplanation`、`assumption`。其中不再有 `baseline`、`effectiveContribution`、`p4AffineTransformApplied`。

## 8. 当前明确没有的接口

为避免前端误判，当前 Java Controller 没有：

- 管理员增删改院系、专业、班级或课程的独立接口；管理员教学数据页是只读。院系、专业、班级都可在创建/编辑账号或 CSV 导入时按名称自动创建，不走独立 CRUD。
- 文件内联预览和删除接口；受鉴权的附件下载已经实现。
- 教师/学生专用工作台聚合接口。
- 作业结束/归档、申诉转 `PROCESSING/CANCELLED` 或学生撤销申诉的接口。

这些能力属于团队后续。新增前先设计权限与状态，再更新本契约。
