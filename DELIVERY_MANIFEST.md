# 交付清单 (Delivery Manifest)

本文件记录 Groupwork 算法核心化重构与管理员端落地的交付状态、测试证据与关键工件哈希。团队成员接收仓库后应按本文件核对完整性，并重新运行验证命令确认环境一致。

## 交付时间

- 交付日期：2026-07-15
- 交付版本：算法核心化重构首版（管理员端完整，教师/学生端后端完整 + 前端骨架）

## 交付范围

| 模块 | 状态 | 说明 |
|---|---|---|
| Spring Boot 后端 | 完成 | 七大领域：身份认证、教学治理、团队协作、评分、模型治理、审计、文件存储 |
| Python 算法服务 | 完成 | 论文 MCQS 规则引擎 + XGBoost 推理服务，含训练脚本与模型产物 |
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
| Java 单元/安全/契约测试 | `cd backend && mvn clean test` | 通过（含论文公式边界、MCQS 权重/份额校验、管理员权限隔离、教师跨课程拒绝、登录角色识别、工作量并发确认、评分输入哈希规范化等） |
| Python 规则/模型/接口测试 | `cd backend/algorithm-service && python -m pytest -q` | 通过（含论文公式等值、1785 条样本、模型指标门槛、Java/Python 跨实现等值、服务超时回退） |
| 前端单元测试 | `cd frontend && npm test -- --run` | 通过 |
| 前端生产构建 | `cd frontend && npm run build` | 通过 |
| 数据库空库导入 | 导入 `backend/database/groupwork_schema.sql` + `groupwork_seed.sql` | 通过 |
| 数据库旧库迁移 | 在副本库执行 `backend/database/legacy_migration.sql` | 通过（31 张核心表、账号与归档一致性校验） |

## 模型指标

随仓库交付的 XGBoost 模型已通过准入门槛（RMSE ≤ 1.0、MAE ≤ 0.8、R² ≥ 0.99）。

| 项目 | 值 |
|---|---|
| 模型键 | `paper-score-p4` |
| 版本 | `xgb-p4-seed2025-172a3cf9486e` |
| 规则版本 | `paper-mcqs-v1` |
| 标准组规模 `p` | 4 |
| 适用实际人数 `r` | 1–5 |
| 特征顺序 | `contribution, actual_group_size, group_score` |
| 训练数据行数 | 1785 |
| 训练/测试切分 | 1429 / 356（80/20 分层） |
| 交叉验证 | 5 折确定性分层 |
| 随机种子 | 2025 |
| Holdout RMSE | 0.482899 |
| Holdout MAE | 0.356837 |
| Holdout R² | 0.999700 |
| 5 折 CV RMSE 均值 ± 标准差 | 0.766012 ± 0.045531 |
| 5 折 CV MAE 均值 ± 标准差 | 0.525321 ± 0.027287 |
| 5 折 CV R² 均值 ± 标准差 | 0.999259 ± 0.000106 |
| 准入状态 | ELIGIBLE |

> 注意：上述指标衡量模型对确定性规则生成标签的拟合程度，不等同于真实教学公平性证明。只有 `p=4` 使用 XGBoost；其他合法组规模仅使用规则分并标注超出模型验证范围。

## 关键工件 SHA-256

### 数据库脚本

| 文件 | SHA-256 | 大小 |
|---|---|---|
| `backend/database/groupwork_schema.sql` | `e9593934f8e923d33a198ab3ac34bad098eeb39960cfcbf2a8b2de2bff922d99` | 51,983 B |
| `backend/database/groupwork_seed.sql` | `6a25c692e83516262bbd1d9d1fc28224ad7b4312ea5b4a645b67782765213c20` | 6,436 B |
| `backend/database/groupwork_complete.sql` | `2945f2ba24e640c9a2b8eafa21cca622b1b9d44bfba7968fed9d5d18fee59992` | 100,636 B |
| `backend/database/legacy_migration.sql` | `b554f8e4d500cc881c62b4e7927a33d130820388b514e67e6ccdf7091da79782` | 116,767 B |

### 模型产物

| 文件 | SHA-256 | 大小 |
|---|---|---|
| `backend/algorithm-service/artifacts/xgboost-model.json` | `172a3cf9486e2fa88c0d37c0401844521abdf426a6f17bf66271edb2154c162d` | 4,631,746 B |
| `backend/algorithm-service/artifacts/metadata.json` | `11592152e096882e1e1bb22bbc853c17ef8a9bcc5faf427cc0938df53e327a0e` | 2,692 B |
| `backend/algorithm-service/artifacts/metrics.json` | `e3aba2733517b300554be3496b6116b31dd309eee1b4d83de13a6f278fac6764` | 1,784 B |
| `backend/algorithm-service/artifacts/synthetic-samples.csv` | `7e033aef431573642fa5b5d6cc147794647084aa2c3323056e5a39a30081d146` | 127,272 B |

### API 契约

| 文件 | SHA-256 | 大小 |
|---|---|---|
| `docs/openapi.json` | `8ab9cd846d2d5294cf7d86eb978b569e7b1599834fae204d0c5d628c8ec11380` | 66,206 B |

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
