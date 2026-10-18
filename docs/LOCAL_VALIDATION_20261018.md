# 2026-10-18 本地验证与 Linux 部署交接

项目来自 [Anny-ZYF/pbl-demo](https://github.com/Anny-ZYF/pbl-demo)，分支 `master`，本次修复基于原始提交 `7635dd1`。本文记录修复内容与本地验证结果；服务器环境验证另行进行。

结论：本次发现的运行、模型加载、页面布局及成绩发布问题已修复，并通过本地测试、真实 MySQL 与浏览器复查。此前的未复核发布、整作业联动发布问题已关闭。可以继续进行目标服务器测试部署；Linux Docker、HTTPS 和服务器环境尚未实测。

## 1. 本次实际验证

| 项目 | 结果与范围 |
|---|---|
| Java | JDK 25.0.2 / Maven 3.9.4，编译目标 Java 21；41 项常规测试通过，JAR 打包成功。另有 5 项 MySQL 集成测试在空库和旧库分别通过。容器使用 JDK 21，尚未实测。 |
| Vue | Node 24.15.0；9 项测试通过，生产构建成功。构建存在较大 JS 分包提示，不阻止运行。 |
| Python | 隔离虚拟环境 Python 3.12.13；修复后 29 项测试通过。容器使用 Python 3.11，尚未实测。 |
| 数据库 | 使用独立 MySQL 8.0.42 实例，端口 13316；数据只在本项目 `.codex/validation` 中，未使用其他项目数据库。 |
| 迁移 | 全新空库 V1–V4、Compose schema + seed 预导入升级、仓库旧版演示快照升级均成功。旧库 11 张关键表行数一致，成绩孤儿记录及重复有效成员均为 0。 |
| 管理员浏览器 | 首次登录、强制改密、重新登录，以及工作台、组织账号、教学数据、评分模型、审计日志 5 个页面。 |
| 教师浏览器 | 课程中心、课程首页、作业列表、创建作业页、学生列表、成绩页、批改页，共 7 个页面。 |
| 学生浏览器 | 工作台、课程列表、课程详情、作业详情、成绩页，共 5 个页面。 |
| 页面运行 | 使用生产构建产物的 Vite preview；上述页面未观察到 JS 未捕获异常或失败的业务 API 请求。不是 Docker 内 Nginx 验证。 |
| 真实 API | 管理员创建教师/学生、改密、导入选课名单、教师创建作业、学生建组/加入、提交正文及附件、教师下载附件、评分、发布、学生查成绩。 |
| 附件 | 约 2 MiB 的本地合成 PDF 文件完成上传、提交关联、教师下载，下载字节与上传一致；未验证实际 PDF 阅读能力。 |
| 权限负例 | 匿名访问返回 401；跨角色返回 403；教师访问不属于自己的课程、学生操作他人成绩返回 404；前端跨角色路由被重定向。 |
| 组队期 | 新作业组队期内提交返回 409。只在合成测试库将该作业发布时间前移 3 天后继续验证提交流程。 |
| 模型 | `/health` 返回 `modelAvailable=true`；内部模型接口要求 Token；激活模型后的 Java→Python 请求完成，运行状态为 `MODEL_COMPLETE`。 |
| 预测样例 | 两名测试学生贡献 0.6/0.4、标准人数 4、实际人数 2、团队分 85；规则分 95.51/94.55，模型分 95.43/94.49。 |

本轮 Java 常规测试 41 项、前端 9 项、Python 29 项通过；5 项真实 MySQL 集成测试分别在新空库和旧库运行，共 10 次通过。默认未设置专用 MySQL 测试环境变量时，集成测试会跳过，本次已显式配置两套隔离测试库执行。

浏览器截图和不含 Token 的验证结果位于本地 `.codex/validation/`。最初启动验证为 `browser-admin.json`、`browser-roles.json`、`browser-layout.json`；发布修复的最终证据为 `publication-browser.json`、`publication-api-final.json`、`publication-migration-counts.json`。较早的 `api-smoke.json`、`api-final.json` 保留了修复前的问题复现，不代表当前结果。这些本地证据与数据库、密码文件均不放入部署包。

## 2. 已修复的问题

1. **模型元数据校验失败**：上游 `sha256.json` 保存的是 `metadata.json`、`metrics.json` 的 CRLF 字节哈希，而 Git 中的实际文件是 LF。两份文件恢复 CRLF 后恰好匹配旧哈希，确认原因。本次更新这两项哈希，并令训练脚本固定以 LF 写 JSON，增加跨平台换行回归测试。模型文件、指标数值、训练数据和评分公式未改动，四个产物哈希现均匹配。
2. **缺少根目录环境模板**：新增 `.env.example`，供根目录 `compose.yaml` 使用；所有真实密码与密钥留空。
3. **Docker 构建上下文**：新增前端和后端 `.dockerignore`，排除本机依赖、构建目录、本地配置、数据等。
4. **上传体积不一致**：前端 Nginx 增加 `client_max_body_size 25m`，与后端 25 MB 请求上限配合。原配置未覆盖 Nginx 的默认 1 MB 限制，相关行为见 [Nginx 官方说明](https://nginx.org/en/docs/http/ngx_http_core_module.html#client_max_body_size)。此项尚待服务器 Nginx 实测。
5. **学生首页图标撑大布局**：为课程箭头和助手图标补充局部尺寸。1440 px 与 390 px 视口复查通过，助手卡片高度分别为 141 px、228 px，进入课程按钮点击正常。
6. **成绩复核与发布**：服务层要求全员复核，发布 SQL 与查询投影限定当前运行；去掉批改失败后直接生成已发布成绩的兜底路径。教师页面增加逐人确认、调整理由与按小组发布；当前运行不会继承旧成绩的已发布状态。详见下一节。

## 3. 成绩发布修复与回归

### 原因与修复

原实现允许 `DEGRADED`/`MODEL_COMPLETE` 的未复核运行直接发布，`ScoringMapper.publishRun`、`publishResults` 和投影 SQL 按作业关联所有运行，导致选择一个运行却同时发布历史运行和其他小组。批改失败的兜底路径也直接生成已发布投影。现有 30 项 Java 测试没有覆盖真实数据库中的完整发布链路。

修复后，运行必须为 `APPROVED` 且所有成员结果为 `REVIEWED`。复核与发布使用行锁，整个操作在事务中完成。只发布指定运行及其成员结果、提交和投影。已发布运行重复请求返回成功且不再写入，保留原有发布时间、审计和申诉修订后的投影；不追溯修改既有已发布历史。

### 已通过的真实验证

- 未复核发布返回 409；教师跨归属返回 404；学生调用教师发布接口返回 403；匿名调用返回 401。
- 漏掉成员、改分不填理由返回 400；第二个成员校验失败时，第一个成员的修改也全部回滚。
- 输入不满足评分条件时，批改返回 409，团队分及提交状态回滚，没有产生已发布兜底成绩。
- 浏览器逐人核对、改分、填写理由、保存复核、发布 A 组；B 组仍待复核，A 组学生看到调整后的 88 分，B 组学生看不到尚未发布的结果。
- A 组发布前后，B 组运行及数据快照一致；A 组旧运行不被连带发布。
- 两个并发发布请求均返回 200，只记录一条发布审计；重复发布不改写时间、投影或其他小组数据。
- 通过真实申诉接口将成绩调整为 89 分，随后重复发布，成绩仍为 89 分，相关记录保持不变。
- 创建新的待复核运行后，教师列表正确显示其未发布状态，旧的已发布记录保留。
- 桌面 1440 px、手机 390 px 下检查复核弹窗；页面无未捕获脚本错误及业务接口失败。

回归测试代码位于 `backend/src/test/java/com/gdmu/scoring/ScoringPublicationTest.java` 和 `ScoringPublicationMySqlTest.java`。MySQL 测试仅接受回环地址下名为 `groupwork_*_test` 的专用库，测试数据在每项结束后回滚。

```powershell
# 先创建独立空测试库，或将仓库演示快照导入另一独立测试库。
$env:GROUPWORK_MYSQL_TEST_URL = 'jdbc:mysql://127.0.0.1:13316/groupwork_publication_test?allowPublicKeyRetrieval=true&useSSL=false&serverTimezone=Asia/Shanghai'
$env:GROUPWORK_MYSQL_TEST_USERNAME = '你的测试库用户'
$env:GROUPWORK_MYSQL_TEST_PASSWORD = '你的测试库密码'
Set-Location backend
mvn -Dtest=ScoringPublicationMySqlTest test
```

### 功能与初始化边界

- 本版本已有教师/学生业务页面，README 已同步主要页面范围；旧交接文档可能仍描述早期骨架状态。
- 学生首页的智能学习助手仍是展示区域，没有接通聊天动作，不能作为可用 AI 对话功能交付。
- Compose 默认创建空教学库，仅初始化规则和模型元数据。教师/学生账号可由管理员创建；课程/组织基础数据需要导入或预置。本次测试课程由隔离数据库的合成数据准备脚本建立，未声称有现成的建课界面。
- 新库模型处于 `VALIDATED` 状态，需要管理员激活后才使用 XGBoost；未激活时走确定性规则。

## 4. Linux + Docker Compose 测试部署

本节记录本地验证结束时的部署准备：当时本机未安装 Docker，也未连接服务器。随后完成的 MySQL 8.4、Linux 容器、Nginx 与 HTTPS 实测结果见第 6 节。

1. 根据服务器发行版按 [Docker 官方安装入口](https://docs.docker.com/engine/install/) 安装 Docker Engine 与 Compose 插件，确认 `docker version`、`docker compose version` 可用。
2. 将部署源码包上传并解压到独立目录，例如 `/opt/pbl-demo`，或拉取包含本次修复的 GitHub 提交。部署前核对源码版本与本文对应的修复内容。
3. 进入项目根目录，准备环境：

```bash
umask 077
cp .env.example .env
nano .env
```

填写 `MYSQL_ROOT_PASSWORD`、`MYSQL_APP_PASSWORD`、`JWT_SECRET_BASE64`、`MODEL_SERVICE_TOKEN`、`BOOTSTRAP_ADMIN_PASSWORD`。JWT 使用 `openssl rand -base64 32` 生成；其余密码/内部令牌使用相互独立的随机值。管理员初始密码必须至少 12 位并含字母和数字。

`CORS_ALLOWED_ORIGINS` 填实际浏览器访问源，如 `http://服务器IP:18080` 或 `https://实际域名`，需要与 `FRONTEND_PORT`/HTTPS 代理一致。前端默认 80；若已有站点占用，选择独立端口。MySQL、后端宿主端口默认 3306/8080 且绑定 127.0.0.1；若已占用，可分别修改 `MYSQL_PORT`、`BACKEND_PORT`。

4. 检查并启动：

```bash
docker compose config --quiet
docker compose up -d --build
docker compose ps
docker compose logs --tail=100 backend algorithm
docker compose exec -T frontend nginx -t
docker compose exec -T backend curl -fsS http://127.0.0.1:8080/actuator/health
docker compose exec -T algorithm python -c "import json,urllib.request; h=json.load(urllib.request.urlopen('http://127.0.0.1:8000/health')); print(h); assert h['modelAvailable']"
```

5. 浏览器登录新管理员并完成强制改密。在 `.env` 中清空 `BOOTSTRAP_ADMIN_PASSWORD`，运行 `docker compose up -d backend` 使新环境生效；在评分模型页激活所需模型。
6. 服务器上重放第 3 节的复核发布验证，并检查三角色页面、附件上传下载、权限隔离；按 `DEPLOYMENT.md` 配置 HTTPS 与数据备份。

数据在 `mysql-data`、`uploads` 卷中持久化。镜像升级不会自动重放数据库初始化脚本，后续结构升级由 Flyway 执行。

## 5. 本地预览

本次临时预览地址：`http://127.0.0.1:15173`。管理员用户名 `pbl_admin`；本地验证密码保存在未纳入 Git 的 `.codex/validation/local-secrets.json`，管理员最终密码字段为 `adminPassword`。教师 `pbl_teacher1`、学生 `pbl_student1` 使用 `testPassword` 字段。所有账号与课程仅属于隔离测试库。

本地服务监听端口：前端 15173、Java 18081、算法 18001、MySQL 13316，均为 127.0.0.1。它们是临时验证进程，不是正式服务器服务。

## 6. 服务器部署与验证

2026-10-18 14:35（北京时间）完成服务器复查。正式地址为 `https://pbl.luzujian.com`，部署目录为 `/root/workspace/pbl`，使用 `deploy/compose.production.yaml`。应用产物基于修复提交 `4a6788b`；生产部署配置另由服务器 `release-manifest.json` 记录文件哈希，可据此核对仓库文件与实际部署产物。

| 项目 | 实际结果 |
|---|---|
| 容器 | `pbl-mysql-1`、`pbl-algorithm-1`、`pbl-backend-1`、`pbl-frontend-1` 均为 healthy，没有崩溃重启。 |
| 新库初始化 | MySQL 8.4 导入 schema/seed，Flyway 的 1–4 版本记录成功；正式库只有 1 个管理员，教师、学生、课程均为 0。 |
| 时区 | 后端、算法和数据库均配置 `Asia/Shanghai`；后端日志及管理页面时间与北京时间一致。 |
| 本轮构建测试 | Java 常规测试 41 项、前端 9 项、Python 本地 29 项通过，前后端构建成功。本轮未重跑真实 MySQL 的 5 项集成测试，默认跳过；先前执行结果仍见第 1、3 节。 |
| Linux 算法镜像 | 在正式 CPU 推理镜像的临时容器中运行全部 29 项 Python 测试，通过后删除测试容器。 |
| 公网浏览器 | 实际访问正式域名，完成初次登录、强制改密、再次登录，以及工作台、组织账号、教学数据、评分模型、审计日志五个页面；桌面和手机尺寸检查通过，无未捕获脚本错误或失败业务请求。 |
| 最终浏览器复查 | 移除初始化密码、重建 PBL 后端及证书续期 hook 执行后，重新登录并检查工作台、评分模型页，仍正常。 |
| 管理接口 | 登录、当前用户、工作台、组织、账号、课程、模型、评分运行、异常、审计及退出接口返回预期结果。 |
| 权限与来源 | 匿名访问管理接口为 401，管理员访问教师/学生业务接口为 403；同源 OPTIONS 为 200，其他 Origin 为 403。 |
| 模型 | 已激活 `xgb-p4-seed2025-172a3cf9486e`；后端容器使用实际配置的 URL/Token 访问算法服务成功，算法匿名访问为 401。实际预测样例规则分 95.506576、模型分 95.427322。 |
| 代理与上传目录 | 2 MiB 未认证请求经两层 Nginx 后返回 401，没有触发 413；应用用户 UID 10001 可写持久化上传目录。此项不等同于已在生产库执行学生附件上传/下载。 |
| HTTPS | 独立证书 SAN 为 `pbl.luzujian.com`，有效期至 2027-01-16；证书部署文件一致，Certbot 定时器启用且运行。 |
| 续期 | 正式签发使用 DNS-01。模拟续期直连测试 CA 超时，经临时代理完成 DNS 验证与部署 hook，Certbot 报告成功；Cloudflare DNS 请求仍由服务器直连，临时代理已关闭，未修改正式定时任务的网络配置。 |
| 其他项目 | 原有 20 个容器的身份、镜像、配置、挂载、启动时间和运行状态保持一致，仅共享网关加入 PBL 网络；实习系统、腺样体网站返回 200，旧测试域名仍返回 410。 |
| 备份 | 初始数据库、上传目录和部署记录已备份到服务器及本机，SHA-256 校验通过。密钥配置单独保存；本轮没有执行数据库恢复演练。 |

公网可用性依据为真实浏览器及其业务请求。Windows curl 的 TLS 握手失败没有被计为通过，服务端 curl 的结果也没有代替公网浏览器验证。

管理员账号为 `pbl_admin`，密码保存在未纳入 Git 的 `.codex/deployment/admin-credentials.json`。一次性初始化密码已从服务器环境和本机交付信息中移除。运行环境密钥单独位于受限文件，未进入 Git、构建上下文或数据备份压缩包；含初始化密钥的临时上传包已删除。

本机证据包括 `.codex/deployment/production-browser.json`、`production-browser-final.json`、`production-api.json`、`deployment-result.json`、`certificate-renewal-proxy.log` 和 `initial-backup-verified.json`。服务器记录位于 `/root/workspace/deploy-backups/pbl-deploy-20261018`。

最终检查时服务器剩余磁盘约 1.94 GB、可用内存约 2.5 GiB。共享网关还保留一个无容器的旧名称兼容网络，原因及维护边界见 `DEPLOYMENT.md` 第 12 节。没有恢复旧测试环境的容器、数据库或部署目录。正式教学库未写入合成测试课程，因此本次没有在生产库重放教师评分、成绩发布和申诉完整流程；这些业务回归的既有本地证据见第 3 节。
