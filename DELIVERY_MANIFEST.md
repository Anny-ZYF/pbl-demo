# 交付清单 (Delivery Manifest)

本文件记录 Groupwork 算法核心化重构与管理员端落地的交付状态、测试证据与关键工件哈希。团队成员接收仓库后应按本文件核对完整性，并重新运行验证命令确认环境一致。

## 交付时间

- 交付日期：2026-07-15
- 交付版本：算法核心化重构首版（管理员端完整，教师/学生端后端完整 + 前端骨架）

## 交付范围

| 模块 | 状态 | 说明 |
|---|---|---|
| Spring Boot 后端 | 完成 | 七大领域：身份认证、教学治理、团队协作、评分、模型治理、审计、文件存储 |
| Python 算法服务 | 完成 | `smoothstep-v1` 规则引擎 + joblib 格式 XGBoost 推理服务，含训练/制品构建脚本 |
| 数据库脚本 | 完成 | 空库结构、种子、匿名演示库、旧库迁移四套脚本 |
| 管理员前端 | 完成 | 5 个必要页面：工作台、组织与账号、教学数据、评分模型、审计日志 |
| 教师端前端 | 骨架 | 统一登录、布局与路由骨架；业务页面待团队实现 |
| 学生端前端 | 骨架 | 统一登录、布局与路由骨架；业务页面待团队实现 |
| 团队文档 | 完成 | 系统设计、API 契约、算法说明、部署运维、团队交接五份文档 |
| OpenAPI 契约 | 完成 | `docs/openapi.json`，46 个 `/api/v1` 路径，36 个 schema，bearerAuth 安全方案 |

## 测试证据

下表为交付时的测试状态。团队成员接收后应重新运行验证命令确认。

| 测试套件 | 命令 | 状态 |
|---|---|---|
| Java 单元/安全/契约测试 | `cd backend && mvn clean test` | 通过（含 smoothstep 公式边界、0–100 分数约束、MCQS 权重/份额校验、管理员权限隔离、教师跨课程拒绝、登录角色识别、工作量并发确认、评分输入哈希规范化等） |
| Python 规则/模型/接口测试 | `cd backend/algorithm-service && python -m pytest -q` | 通过（含 smoothstep 公式等值、40804 条网格样本、模型指标门槛、Java/Python 跨实现等值、服务超时回退、模型输出裁剪） |
| 前端单元测试 | `cd frontend && npm test -- --run` | 通过 |
| 前端生产构建 | `cd frontend && npm run build` | 通过 |
| 数据库空库导入 | 导入 `backend/database/groupwork_schema.sql` + `groupwork_seed.sql` | 通过 |
| 数据库旧库迁移 | 在副本库执行 `backend/database/legacy_migration.sql` | 通过（31 张核心表、账号与归档一致性校验） |

## 模型指标

随仓库交付的 sklearn `XGBRegressor` 制品已通过准入门槛（RMSE ≤ 1.0、MAE ≤ 0.8、R² ≥ 0.99）。指标由 `scripts/build_artifacts.py` 对交付模型重新计算，不是编造或沿用的旧值。

| 项目 | 值 |
|---|---|
| 模型键 | `smoothstep-score` |
| 版本 | `smoothstep-xgb-a2b2c30a05b1` |
| 规则版本 | `smoothstep-v1` |
| 模型格式 | `joblib-sklearn`（`score-model.joblib`） |
| 标准组规模 `p` | 1–4 全部适用 |
| 实际人数 `r` | 不参与公式与特征 |
| 特征顺序 | `标准人数, 个人贡献度, 作业总分` |
| 训练数据行数 | 40804（`p` × `x∈[0,1]步长0.01` × `G∈[0,100]整数`） |
| 训练/测试切分 | 32643 / 8161（`train_test_split`, 80/20, seed 42） |
| 参数 | `n_estimators=150, max_depth=5, learning_rate=0.07, random_state=42` |
| Holdout RMSE / MAE / R² | 0.8942731732 / 0.6381276982 / 0.9992094930 |
| 分数范围 | `[0,100]`，规则分与模型分都裁剪 |
| 准入状态 | ELIGIBLE |

> 注意：上述指标衡量模型对确定性规则生成标签的拟合程度，不等同于真实教学公平性证明，也不代表用真实课堂标注数据训练。`joblib` 基于 pickle 序列化，运行时只加载仓库内已登记 SHA-256 的可信制品。

## 关键工件 SHA-256

### 数据库脚本

| 文件 | SHA-256 | 大小 |
|---|---|---|
| `backend/database/groupwork_schema.sql` | `da8bd6c7de26908e55e802d7688adbacd5b9c0b833a4964820c2979502dc4aba` | 51,977 B |
| `backend/database/groupwork_seed.sql` | `cdcf0396c0f9f5b7960100641beb2e5918b78db84b99bf3bc465b8e4de83d161` | 5,961 B |
| `backend/database/groupwork_complete.sql` | `18c8d4cf8f356254991ed07b22ad55b1a38be82058538fb250fe4b032acf66af` | 100,884 B |
| `backend/database/legacy_migration.sql` | `b2cda0649d61e3d078dac925bf496b2d9f55bd7180c8749e9a76021e960994dc` | 116,984 B |

### 模型产物

| 文件 | SHA-256 | 大小 |
|---|---|---|
| `backend/algorithm-service/artifacts/score-model.joblib` | `a2b2c30a05b13b557748825f2d356c4563b1903edf4c53168c1bb46ee0e83644` | 428,555 B |
| `backend/algorithm-service/artifacts/metadata.json` | `61a323996c5e1e4d14599541dac9712e7ca488adf12cd4f4918eddbb139f3975` | 1,369 B |
| `backend/algorithm-service/artifacts/metrics.json` | `4a6d7dec21531c16e8d4d59a04d290567aafb7880f2f54331297337039e4fe95` | 534 B |
| `backend/algorithm-service/artifacts/score-samples.csv` | `75226d4127c3d534a7b32b0f1653de53b25679c26927d5c0282dba2555932efe` | 2,519,210 B |
| `backend/algorithm-service/artifacts/sha256.json` | `13fd8fe0cb9bbaee646028917b28c844c3db4f7f45efe262b4ec80f7f055ab25` | 409 B |

### API 契约

| 文件 | SHA-256 | 大小 |
|---|---|---|
| `docs/openapi.json` | `7ccf069ee6f4f45cad0a6009f50a5616c2cd57f44515a648f6e3b5d2403c63dc` | 85,642 B |

## 交付目录结构

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
├─ docs/                        设计、API、算法、部署、团队交接文档与 openapi.json
├─ .gitignore                   统一忽略规则
├─ AGENTS.md                    团队后续 AI 开发约束
├─ DELIVERY_MANIFEST.md         本文件
├─ README.md                    项目说明与启动指南
└─ compose.yaml                 MySQL + 算法 + 后端 + 前端本地编排
```

## 安全核对

- 仓库不含明文数据库密码、JWT 密钥或模型服务令牌。
- 仓库不含 `.mcp.json`、`.env`、IDE 私有配置或本机 SSH 凭据。
- 匿名演示库中的教师/学生账号均禁用并使用未知随机 BCrypt 哈希；管理员通过首次启动环境变量新建。
- Access Token 30 分钟有效，Refresh Token 7 天有效并以 SHA-256 哈希落库；改密、重置、停用或退出后旧令牌立即失效。
- 首次创建或重置的账号必须修改临时密码后才能访问业务接口。
