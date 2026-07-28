# Groupwork 团队作业差异化评分系统

Groupwork 是面向校园合作学习的三角色系统，以论文中的多维贡献量化策略（MCQS）和 XGBoost 个人评分建议为核心。系统保存评分输入与版本快照，模型不可用时自动回退确定性规则，最终成绩必须由教师复核后发布。

当前交付状态：**管理员前端已经完成；教师端和学生端保留统一登录、布局与路由骨架；三个角色所需的核心后端、数据库、算法服务和接口契约已经完成**。团队成员可直接依据现有 API 开发教师端和学生端，不需要重建后端或复制旧草稿页面。

## 当前功能

### 管理员端（已实现）

管理员导航严格控制为 5 个必要页面：

1. 工作台：账号、教学、模型、评分运行和待治理异常概况。
2. 组织与账号：组织树、账号查询/新增/编辑/启停/重置密码、CSV 批量导入。
3. 教学数据：全校课程、教师、学生数、作业数和团队数只读治理视图。
4. 评分模型：XGBoost 版本登记、指标校验、激活/回滚、运行与异常治理。
5. 审计日志：按事件、角色、日期和关键词查询完整操作轨迹。

首次创建或重置的账号必须修改临时密码。Access Token 和 Refresh Token 均绑定账号版本；退出、改密、重置或状态变化后旧 Token 立即失效。

### 教师端（后端已完成，前端待团队实现）

课程与学生名单、作业和评价分支、团队与成员、作品及附件、团队分、工作量方案、成员确认状态、评分运行、逐人复核、成绩发布和申诉处理均已有受归属校验的 API。

### 学生端（后端已完成，前端待团队实现）

课程与作业、本人团队与成员、作品提交及附件、组长提交工作量方案、成员确认/异议、已发布成绩与完整评分快照、申诉提交和“我的申诉”均已有 API。

页面蓝图、开发顺序和验收标准见 [系统设计](docs/SYSTEM_DESIGN.md) 与 [团队交接](docs/TEAM_HANDOFF.md)。

## 核心评分口径

- 各评价分支权重组成 `W`，总和为 1。
- 每个分支内成员工作量份额组成 `Q`，每个分支总和为 1。
- 个人贡献度为 `C = QW^T`，同组贡献度总和为 1。
- Java 始终计算并保存 `paper-mcqs-v1` 规则分。
- 只有标准组规模 `p=4` 才使用已验证 XGBoost；其他规模明确为 `rule_only`。
- 模型服务失败、版本/规则/文件哈希/成员集合不一致时整批回退规则分。
- 模型分与规则分偏差严格大于 2 分时标记异常，但所有成员成绩都必须经教师复核。
- 已发布 `score_result` 不覆盖；接受申诉时向 `score_revision` 追加修订。

随仓库交付的模型：

| 项目 | 值 |
|---|---|
| 模型键 | `paper-score-p4` |
| 版本 | `xgb-p4-seed2025-172a3cf9486e` |
| 文件 SHA-256 | `172a3cf9486e2fa88c0d37c0401844521abdf426a6f17bf66271edb2154c162d` |
| Holdout RMSE / MAE / R² | `0.482899 / 0.356837 / 0.999700` |
| 特征顺序 | `contribution, actual_group_size, group_score` |

算法公式、论文歧义决策、训练数据和指标说明见 [评分算法文档](docs/SCORING_ALGORITHM.md)。这些指标衡量模型对规则生成标签的拟合，不等同于真实教学公平性证明。

## 技术栈与目录

```text
Groupwork/
├─ backend/
│  ├─ src/                      Spring Boot 3.5 / Java 21 后端与测试
│  ├─ algorithm-service/        FastAPI 规则与 XGBoost 服务、产物和测试
│  ├─ database/                 空库、种子、匿名演示库和旧库迁移脚本
│  ├─ pom.xml                   Maven 构建配置
│  ├─ Dockerfile                Java 后端镜像
│  ├─ .env.example              环境变量模板（不含真实密钥）
│  └─ .mvn/                     Maven 包装器配置
├─ frontend/                    Vue 3 管理端及教师/学生端骨架
├─ docs/                        设计、API、算法、部署和团队交接文档
├─ compose.yaml                 MySQL + 算法 + 后端 + 前端本地编排
└─ AGENTS.md                    团队后续 AI 开发约束
```

## 数据库脚本

根据场景四选一，不要把脚本混合重复执行：

| 文件 | 用途 |
|---|---|
| `backend/database/groupwork_schema.sql` | 新空库完整结构。 |
| `backend/database/groupwork_seed.sql` | 新空库必需规则与模型元数据；不创建业务账号。 |
| `backend/database/groupwork_complete.sql` | 可直接导入的最小匿名演示库，含一个完整评分闭环；不含真实姓名、手机号、历史操作日志或可用默认账号。 |
| `backend/database/legacy_migration.sql` | 将旧版 `groupwork` 数据库就地升级到当前结构。执行前必须备份。 |

应用正常升级以 `backend/src/main/resources/db/migration/` 中的 Flyway 迁移为权威。匿名演示库中的教师/学生账号均禁用并使用未知随机 BCrypt 哈希；管理员应通过首次启动变量新建，随后再由管理员按需启用和重置演示账号。

## 最快启动：Docker Compose

要求 Docker Compose。先复制根目录环境模板为 `.env`，至少设置以下值：

```dotenv
MYSQL_ROOT_PASSWORD=仅数据库管理员使用的强密码
MYSQL_APP_PASSWORD=应用数据库强密码
JWT_SECRET_BASE64=Base64编码的至少32个随机字节
MODEL_SERVICE_TOKEN=独立的随机内部令牌
BOOTSTRAP_ADMIN_USERNAME=你的新管理员账号
BOOTSTRAP_ADMIN_PASSWORD=至少12位且包含字母和数字
BOOTSTRAP_ADMIN_DISPLAY_NAME=系统管理员
```

启动：

```powershell
docker compose up --build
```

访问 `http://localhost`。MySQL 与后端调试端口只绑定本机回环地址；算法服务不映射到宿主公网。首次管理员登录会被强制进入改密页。首次启动完成后，从环境配置中移除 `BOOTSTRAP_ADMIN_PASSWORD`。

## 本地开发

依赖：JDK 21+、Maven 3.9+、Node.js 20+、MySQL 8、Python 3.11+。

1. 创建数据库并导入 `groupwork_schema.sql` 与 `groupwork_seed.sql`，或导入 `groupwork_complete.sql`。
2. 按 `.env.example` 在 PowerShell/IDE 中设置数据库、JWT、模型令牌和首次管理员变量。
3. 启动算法服务：

```powershell
cd backend/algorithm-service
py -3.11 -m venv .venv
.\.venv\Scripts\Activate.ps1
python -m pip install -r requirements-runtime.txt
$env:ALGORITHM_SERVICE_TOKEN = "与后端一致的内部令牌"
python -m uvicorn app.main:app --host 127.0.0.1 --port 8000
```

4. 启动后端和前端：

```powershell
# 仓库根目录
cd backend && mvn spring-boot:run

# 新终端
cd frontend
npm install
npm run dev
```

Vite 默认地址为 `http://localhost:5173`，后端为 `http://localhost:8080`。Swagger UI 为 `http://localhost:8080/swagger-ui.html`，静态机器契约见 [docs/openapi.json](docs/openapi.json)。生产部署与旧库升级步骤见 [部署文档](docs/DEPLOYMENT.md)。

## 验证命令

```powershell
# Java
cd backend && mvn clean test

# Vue
cd frontend
npm test -- --run
npm run build

# Python（在 algorithm-service 虚拟环境）
python -m pip install -r requirements-runtime.txt -r requirements-dev.txt
python -m pytest -q
```

本次交付已验证 Java 单元/安全/契约测试、Vue 测试与生产构建、Python 规则/模型/接口测试、MySQL 空库导入和旧库迁移。最终验收数据与文件哈希记录在 `DELIVERY_MANIFEST.md`。

## 团队开发规则

- 教师/学生前端必须复用现有会话、路由守卫、HTTP 封装、共享布局和简约视觉规范。
- 不在前端重写评分公式，不伪造统计、成功提示或接口数据。
- 不绕过教师/学生资源归属、工作量全员确认、复核发布和申诉状态机。
- 新增接口时同步 Java 测试、[API 契约](docs/API_CONTRACT.md) 与 `docs/openapi.json`。
- 不提交 `.env`、密码、Token、上传文件、日志、构建产物、虚拟环境或 `node_modules`。
- 所有 AI 编码工具先阅读 [AGENTS.md](AGENTS.md)。

完整文档入口：

- [系统设计](docs/SYSTEM_DESIGN.md)
- [API 契约](docs/API_CONTRACT.md)
- [评分算法](docs/SCORING_ALGORITHM.md)
- [部署与运维](docs/DEPLOYMENT.md)
- [教师端与学生端交接](docs/TEAM_HANDOFF.md)

