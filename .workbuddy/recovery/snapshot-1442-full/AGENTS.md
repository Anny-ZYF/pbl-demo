# AGENTS.md

本文件约束所有 AI/自动化开发工具在本仓库中的行为。目标不是继续堆功能，而是在既定领域边界内完成可验证、可审计的校园团队作业差异化评分系统。

## 1. 项目定位与当前边界

- 系统角色：管理员、教师、学生。
- 管理员只负责组织账号、教学数据治理、模型版本、评分运行异常和审计，不代替教师创建团队、评分或发布成绩。
- 当前管理员前端已实现；教师端、学生端仅保留可运行骨架，后续成员应在现有路由、API 和样式规范上扩展。
- 后端是三个角色共用的完整业务底座。不得为了某个新页面绕过权限、快照、状态机或审计。
- 核心算法是 MCQS 贡献量化 + `smoothstep-v1` 确定性分段公式 + 覆盖 `p=1..4` 的可选 XGBoost 建议分（规则版本见 `docs/SCORING_ALGORITHM.md`）。模型不是唯一真相源，教师复核后才能发布。

## 2. 技术栈与目录

- `backend/src/`：Java 21、Spring Boot 3.5、Spring Security、JWT、MyBatis、Flyway、MySQL 8。
- `frontend/`：Vue 3、Vite、Pinia、Element Plus、Axios。
- `backend/algorithm-service/`：Python 3.11、FastAPI、joblib 格式的 sklearn XGBRegressor；运行时依赖 `numpy/pandas/scikit-learn/joblib/xgboost`，模型依赖缺失或制品哈希不符时仍必须能提供规则回退。
- `backend/database/`：空库结构、最小种子、保留数据的完整交接脚本、旧库迁移脚本。
- `docs/`：系统、算法、API、部署与团队交接文档。

## 3. 常用命令

```bash
# Java backend
cd backend && mvn test
cd backend && mvn spring-boot:run

# Admin frontend and role skeletons
cd frontend
npm install
npm test
npm run dev
npm run build

# Algorithm service
cd backend/algorithm-service
python -m venv .venv
python -m pip install -r requirements-runtime.txt -r requirements-dev.txt
python -m pytest
python -m uvicorn app.main:app --host 127.0.0.1 --port 8000
```

运行服务前从 `.env.example` 配置数据库、JWT、模型服务令牌和首次管理员。真实密码、JWT 密钥、模型服务令牌不得提交。

## 4. 不可破坏的算法约束

1. 作业评价分支权重 `W` 必须非负且总和为 1。
2. 对每个评价分支，团队成员工作量份额 `Q` 必须非负且总和为 1；不要校验成“每名学生跨分支总和为 1”。
3. 个人贡献为 `C = QW^T`，同组贡献之和应为 1。
4. 标准人数 `p` 为 1–4；实际人数 `r` 只作为团队事实保留在历史列中，**不参与新公式、模型特征或 HTTP 请求**。活跃评分链路不再写入 `actual_group_size`。
5. 规则版本 `smoothstep-v1` 按 `1108.py` 实现：`S(t)=3t²−2t³`、`a=1/p`；`x<=a` 时 `t=x·p, score=G·S(t)`；`x>a` 时 `t=(x−a)/(1−a), score=G+20·S(t)`；结果裁剪到 `[0,100]`。旧 `paper-mcqs-v1`（α 组规模系数、`sin` 基准分、`2/(p+2)` 阈值、`p=4` 仿射变换）已退役，不得在新链路中复活。
6. 所有分数入口（团队作品分、规则分、模型分、建议分、教师复核最终分、申诉调整分）都必须限制在 **0–100**，公式与请求校验、DTO、服务层、数据库 CHECK 四处同时约束；贡献度与权重/份额为 0–1 比例。
7. XGBoost 覆盖 `p=1..4`，不再有 `p=4` 门控，也不再产生 `rule_only` 模式。模型特征顺序固定为中文列名 `标准人数 / 个人贡献度 / 作业总分`（对应 `standardGroupSize / contribution / groupScore`），制品为 `score-model.joblib`（sklearn `joblib.dump`），不要用旧 `xgboost-model.json` 或 `paper-score-p4`。
8. 模型失败、未激活、超时或版本/哈希不匹配时必须整批回退规则结果。模型分与规则分差值严格大于 2 分时标记异常；该模型拟合的是确定性规则网格，不是真实评分数据，不得声称模型做过真实课堂验证。
9. 任何评分都要保存输入快照、输入哈希、规则分、模型分、最终分、规则版本、模型版本和解释；不得覆盖已发布历史。`joblib` 是 pickle 序列化，只加载仓库内已登记 SHA-256 的可信制品。
10. 模型只有达到 RMSE ≤ 1、MAE ≤ 0.8、R² ≥ 0.99 才能注册为可激活版本；禁止为通过门槛而篡改指标。

算法变更必须同时更新 Java/Python 单测和 `docs/SCORING_ALGORITHM.md`，并增加跨实现等值样例。

## 5. API 与权限规则

- 所有业务 API 使用 `/api/v1`。
- 响应统一为 `{code,message,data,requestId}`；HTTP 状态必须真实反映成功、参数错误、未认证、无权限、冲突和服务错误。
- 登录可省略 `role` 自动识别唯一账号，也可显式传 `ADMIN/TEACHER/STUDENT`。禁止增加公开注册接口。
- 管理员：`/api/v1/admin/**`；教师：`/api/v1/teacher/**`；学生：`/api/v1/student/**`。
- Controller 中的教师/学生身份只能来自 `UserPrincipal`，不得信任请求体里的当前用户 ID。
- 教师查询和写入必须校验课程/作业归属；学生查询和写入必须校验选课、组员或成绩归属。
- JWT 访问令牌不写日志。刷新令牌只在数据库保存 SHA-256，修改密码、停用账号和退出时撤销。
- 修改模型状态、工作量确认、评分运行、人工调整、发布成绩和申诉结论必须写 `audit_event`。

新增接口时同步更新运行时 springdoc 注解/类型、`docs/API_CONTRACT.md` 和检查入库的 OpenAPI 文件。

## 6. 数据库规则

- 只通过 `backend/src/main/resources/db/migration/` 中新的 Flyway 版本修改生产结构；禁止让应用启动时临时执行散落 DDL。
- 已发布评分、快照、审计事件只追加，不原地改写。重算必须创建新 `scoring_run`。
- `group_member` 是团队成员唯一真相源；不得重新启用 `team_relation` 双写。
- `assignment_parts` 是论文 W/Q 的评价分支；协作任务分支不得替代它。
- 外键、唯一键和状态约束必须在数据库与服务层同时保护。比例使用 DECIMAL，不使用浮点列保存最终权重。
- 旧 `final_score`/`individual_score` 只作为 `LEGACY_IMPORTED` 历史保留，不得补造不存在的 W/Q 或模型版本。
- 修改迁移前先备份；在独立测试库完成空库导入与旧库升级演练，核对关键表行数、孤儿记录和重复成员。

## 7. 前端协作规则

- 保持浅灰蓝背景、白色卡片、蓝色主操作的简约风格；复用现有布局、间距、状态标签和响应式断点。
- 管理员导航固定为：工作台、组织与账号、教学数据、评分模型、审计日志。没有明确治理职责的功能不要加入管理员端。
- 教师/学生功能分别放在 `frontend/src/views/teacher`、`frontend/src/views/student`（创建后），API 分别放在对应模块，不把业务堆进布局组件。
- 页面不得写模拟统计、假成功提示或吞掉 API 错误。空状态、加载态、403、过期登录和服务降级都要真实呈现。
- Pinia 只维护一个当前会话；路由权限默认拒绝。不要恢复按角色存多套 token 的旧实现。
- 学生成绩页必须展示贡献、分支权重/本人份额、规则分、模型建议、教师最终分、版本与调整理由，并提供申诉入口。

## 8. 完成定义

提交前至少执行并记录：

1. `mvn test`；涉及 SQL 时再做 MySQL 空库和旧库迁移演练。
2. `python -m pytest`；涉及模型时记录数据哈希、随机种子和测试指标。
3. `npm test` 与 `npm run build`；涉及页面时做真实浏览器登录、路由和关键交互检查。
4. 安全负例：匿名访问、跨角色访问、教师跨课程访问、学生访问他人成绩。
5. 确认仓库未包含 `.env`、数据库密码、JWT、内部令牌、上传文件、日志、构建产物、虚拟环境和 `node_modules`。

不要把“能编译”当作业务完成。只有代码、数据库、运行时 API 和对应测试证据一致时才可宣称完成。
