# 部署与运维

本文档覆盖 Windows、Linux 与 Docker Compose 上的 MySQL、Spring Boot、Python 算法服务和 Vue 前端。仓库提供三层 Dockerfile 和 `compose.yaml`；当前没有 Kubernetes、Windows Service 或 CI/CD 配置，相关能力不能视为已提供。

## 1. 组件与端口

| 组件 | 技术/版本基线 | 默认监听 | 是否可公网暴露 |
|---|---|---|---|
| MySQL | MySQL 8.0.29+ | `127.0.0.1:3306` | 否 |
| 业务后端 | Spring Boot 3.5.6，编译目标 Java 21 | `0.0.0.0:8080` | 只经反向代理 |
| 算法服务 | Python 3.11+、FastAPI/Uvicorn | `127.0.0.1:8000` | 否 |
| 前端开发 | Node `^20.19.0` 或 `>=22.12.0`、Vite 7 | `127.0.0.1:5173` | 仅开发 |
| 前端生产 | `frontend/dist` 静态文件 | HTTPS 443 | 是 |

JDK 25 可以运行以 Java 21 为目标的构建，但项目实际编译目标以 `backend/pom.xml` 的 Java 21 为准。

推荐启动顺序：MySQL → Python 算法服务 → Spring Boot → 反向代理/前端。没有激活 XGBoost 时 Python 服务不是规则评分的硬依赖；但正式启用模型后必须监控它。

### 1.1 Docker Compose 快速启动

复制 `.env.example` 为不提交版本库的 `.env`，至少填写 `MYSQL_ROOT_PASSWORD`、`MYSQL_APP_PASSWORD`、`JWT_SECRET_BASE64`、`MODEL_SERVICE_TOKEN` 和首次空库所需的 `BOOTSTRAP_ADMIN_PASSWORD`。`CORS_ALLOWED_ORIGINS` 必须设为浏览器实际访问的前端源；Compose 默认前端为 `http://localhost`，不能沿用开发源 `http://localhost:5173`。

```bash
docker compose up -d --build
docker compose ps
```

Compose 启动 MySQL 8.4、算法服务、后端和 Nginx 前端，并持久化 `mysql-data` 与 `uploads` 卷。首次空卷会导入 `backend/database/groupwork_schema.sql` 与 `groupwork_seed.sql`，后端随后由 Flyway 记录基线并执行兼容迁移。校园正式部署仍要配置 HTTPS 反向代理、外部备份和密钥管理，不应直接把 MySQL 或算法服务端口暴露到公网。

## 2. 目录与产物

```text
Groupwork/
├─ backend/src/main/resources/db/migration/   # Flyway 权威迁移
├─ backend/database/                          # 空库结构、种子、旧库迁移参考脚本
├─ backend/algorithm-service/
│  ├─ app/
│  └─ artifacts/                      # 模型、metadata、metrics、哈希、训练数据
├─ frontend/
└─ data/uploads/                      # 默认上传目录，运行后产生
```

生产环境建议：

```text
/opt/groupwork/backend/groupwork-1.0.0.jar
/opt/groupwork/algorithm/{app,artifacts,.venv}
/var/lib/groupwork/uploads
/var/www/groupwork/                    # frontend/dist 内容
/etc/groupwork/backend.env
/etc/groupwork/algorithm.env
```

运行用户必须能读取 JAR/模型并写上传目录，但不应拥有数据库管理员权限。

## 3. 环境变量

仓库根目录 `.env.example` 和 `backend/algorithm-service/.env.example` 只是模板。Docker Compose 会读取同目录 `.env`；直接运行 Spring Boot、Uvicorn 和前端脚本时则**不会自动加载 `.env`**，必须由 PowerShell、IDE、systemd、Windows 服务管理器或密钥管理系统注入。

### 3.1 Spring Boot

| 变量 | 默认/要求 | 说明 |
|---|---|---|
| `SERVER_PORT` | `8080` | Java 端口。 |
| `DB_URL` | 本机 `groupwork` JDBC URL | 生产应开启合适的 TLS/证书参数。 |
| `DB_USERNAME` | `root` | 生产不要使用 root，建立最小权限应用账号。 |
| `DB_PASSWORD` | 空 | 生产必填。 |
| `DB_POOL_SIZE` | `10` | Hikari 最大连接数。 |
| `FLYWAY_ENABLED` | `true` | 正常保持开启。 |
| `JWT_SECRET_BASE64` | 无可用默认值 | 必填；Base64 解码后至少 32 字节。 |
| `JWT_ACCESS_TTL` | `30m` | Access Token 生命周期。 |
| `JWT_REFRESH_TTL` | `7d` | Refresh Token 生命周期。 |
| `JWT_ISSUER` | `groupwork-api` | 修改后旧 Token 失效。 |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:5173` | 生产设置为实际 HTTPS 前端源。 |
| `MODEL_SERVICE_URL` | `http://127.0.0.1:8000` | 内部算法服务。 |
| `MODEL_SERVICE_TOKEN` | 空 | 必须与算法服务 Token 相同；空值会直接规则回退。 |
| `MODEL_CONNECT_TIMEOUT` | `2s` | 模型连接超时。 |
| `MODEL_READ_TIMEOUT` | `5s` | 模型读取超时。 |
| `STORAGE_ROOT` | `./data/uploads` | 生产使用绝对路径并纳入备份。 |
| `BOOTSTRAP_ADMIN_USERNAME` | 空 | 仅空库第一次启动需要。 |
| `BOOTSTRAP_ADMIN_PASSWORD` | 空 | 12–64 位且含字母和数字；只在无管理员时使用。 |
| `BOOTSTRAP_ADMIN_DISPLAY_NAME` | 系统管理员 | 首次管理员显示名。 |

多个 CORS 源的具体绑定格式应在部署前用当前 Spring Boot 配置验证；校园生产通常只配置一个同源 HTTPS 地址，可避免不必要的跨域。

### 3.2 Python 算法服务

| 变量 | 要求 | 说明 |
|---|---|---|
| `ALGORITHM_SERVICE_TOKEN` | 必填 | 与 Java 的 `MODEL_SERVICE_TOKEN` 完全相同。为空时 `/v1/*` 返回 503。 |
| `ALGORITHM_MODEL_DIR` | 默认 `backend/algorithm-service/artifacts` | 模型产物绝对路径。 |

算法服务不读取业务数据库。Token 不要放入前端或浏览器环境变量。

### 3.3 生成密钥

Linux 或已安装 OpenSSL 的 Windows：

```bash
openssl rand -base64 32
openssl rand -hex 32
```

第一个可用于 `JWT_SECRET_BASE64`，第二个可用于内部服务 Token。不要把实际值提交到 Git、截图或聊天记录。

## 4. 数据库迁移

### 4.1 权威路径

- `V1__empty_baseline.sql` 是空的 Flyway 基线标记。
- `V2__legacy_and_core_migration.sql` 创建完整核心结构、兼容旧表并写入规则/模型元数据；只允许 Flyway 执行一次。
- `backend/database/groupwork_schema.sql` 是空库结构参考。
- `backend/database/groupwork_seed.sql` 是可重复执行的规则/模型元数据参考，不含可登录管理员或部署模型。
- `backend/database/legacy_migration.sql` 是旧库迁移的可审查副本；正常应用部署仍以 classpath 中的 Flyway V2 为准。

不要在同一数据库中混用“手工导入完整 SQL”和“让 Flyway 从头迁移”两条路径。

### 4.2 新空库

先创建空数据库和最小权限用户。示例 SQL 中的密码必须替换：

```sql
CREATE DATABASE groupwork
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_0900_ai_ci;

CREATE USER 'groupwork_app'@'127.0.0.1' IDENTIFIED BY 'replace-me';
GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, ALTER, INDEX, REFERENCES,
      CREATE VIEW, DROP
ON groupwork.* TO 'groupwork_app'@'127.0.0.1';
FLUSH PRIVILEGES;
```

然后用该空库启动 Spring Boot。Flyway 依次执行 V1、V2。V2 完成后 `admin_users` 仍为空；一次性初始化器才会根据 `BOOTSTRAP_ADMIN_*` 创建管理员并设置 `must_change_password=1`。

首次成功登录后立即修改密码，并从进程环境中删除三个 `BOOTSTRAP_ADMIN_*` 变量。初始化器只有在管理员表完全为空时才创建账号，后续重启不会重复创建。

### 4.3 旧库升级

1. 停止所有写入进程。
2. 完整备份数据库和上传目录。
3. 在克隆库演练 `backend/database/legacy_migration.sql` 或等价 Flyway V2，并检查冲突/约束。
4. 第一次接管一个没有 `flyway_schema_history` 的非空旧库时，将 Flyway 基线设为 1：

```text
SPRING_FLYWAY_BASELINE_VERSION=1
FLYWAY_ENABLED=true
```

5. 启动一次应用，让 Flyway 将旧库基线到 V1 并执行 V2。
6. 检查 `flyway_schema_history` 中 V2 成功后再恢复流量；以后可移除临时的基线版本覆盖。

不要在迁移失败后反复手工执行 V2。先保存错误和数据库状态，在副本中确认恢复/修复方案。

## 5. Windows 本地与试部署

以下命令在不同 PowerShell 窗口运行。路径按实际仓库调整。

### 5.1 Python 算法服务

```powershell
Set-Location 'D:\Web-work\Groupwork\backend\algorithm-service'
py -3.11 -m venv .venv
.\.venv\Scripts\Activate.ps1
python -m pip install --upgrade pip

# 只需要规则 API：
python -m pip install -r requirements.txt

# 需要加载仓库 XGBoost 产物时安装推理依赖：
python -m pip install -r requirements-runtime.txt

$env:ALGORITHM_SERVICE_TOKEN = '<same-random-token-as-Java>'
$env:ALGORITHM_MODEL_DIR = (Resolve-Path '.\artifacts').Path
uvicorn app.main:app --host 127.0.0.1 --port 8000
```

服务在进程启动时加载模型。替换 `artifacts` 后必须重启进程。

### 5.2 Spring Boot

```powershell
Set-Location 'D:\Web-work\Groupwork\backend'
$env:DB_URL = 'jdbc:mysql://127.0.0.1:3306/groupwork?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true&useSSL=false'
$env:DB_USERNAME = 'groupwork_app'
$env:DB_PASSWORD = '<database-password>'
$env:JWT_SECRET_BASE64 = '<base64-secret>'
$env:CORS_ALLOWED_ORIGINS = 'http://localhost:5173'
$env:MODEL_SERVICE_URL = 'http://127.0.0.1:8000'
$env:MODEL_SERVICE_TOKEN = '<same-random-token-as-Python>'
$env:STORAGE_ROOT = 'D:\GroupworkData\uploads'

# 仅新空库首次启动：
$env:BOOTSTRAP_ADMIN_USERNAME = 'admin'
$env:BOOTSTRAP_ADMIN_PASSWORD = '<temporary-12+-char-password>'
$env:BOOTSTRAP_ADMIN_DISPLAY_NAME = '系统管理员'

mvn spring-boot:run
```

生产式 JAR：

```powershell
mvn clean package
java -jar .\target\groupwork-1.0.0.jar
```

### 5.3 Vue

```powershell
Set-Location 'D:\Web-work\Groupwork\frontend'
npm ci
npm run dev
```

Vite 将 `/api` 代理到 `http://localhost:8080`。生产构建：

```powershell
npm run build
```

将 `frontend/dist` 交给 IIS、Nginx 或其他静态服务器，并配置 SPA 回退与 `/api/` 反向代理。仓库没有提供 Windows 服务包装器；长期运行需由运维选择 WinSW、NSSM、IIS Application Request Routing 或等价受管方案，并把环境变量放入受保护配置。

## 6. Linux 生产部署

下面示例假定构建产物已复制到 `/opt/groupwork`，运行用户为 `groupwork`。

### 6.1 算法服务

```bash
cd /opt/groupwork/algorithm
python3.11 -m venv .venv
.venv/bin/pip install --upgrade pip
.venv/bin/pip install -r requirements-train.txt
```

`/etc/groupwork/algorithm.env`：

```text
ALGORITHM_SERVICE_TOKEN=<internal-random-token>
ALGORITHM_MODEL_DIR=/opt/groupwork/algorithm/artifacts
```

`/etc/systemd/system/groupwork-algorithm.service`：

```ini
[Unit]
Description=Groupwork algorithm service
After=network.target

[Service]
Type=simple
User=groupwork
Group=groupwork
WorkingDirectory=/opt/groupwork/algorithm
EnvironmentFile=/etc/groupwork/algorithm.env
ExecStart=/opt/groupwork/algorithm/.venv/bin/uvicorn app.main:app --host 127.0.0.1 --port 8000
Restart=on-failure
RestartSec=3
NoNewPrivileges=true
PrivateTmp=true

[Install]
WantedBy=multi-user.target
```

### 6.2 Spring Boot

`/etc/groupwork/backend.env`：

```text
SERVER_PORT=8080
DB_URL=jdbc:mysql://127.0.0.1:3306/groupwork?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&useSSL=true
DB_USERNAME=groupwork_app
DB_PASSWORD=<database-password>
JWT_SECRET_BASE64=<base64-secret>
CORS_ALLOWED_ORIGINS=https://groupwork.example.edu.cn
MODEL_SERVICE_URL=http://127.0.0.1:8000
MODEL_SERVICE_TOKEN=<same-internal-random-token>
STORAGE_ROOT=/var/lib/groupwork/uploads
```

首次空库临时加入 `BOOTSTRAP_ADMIN_USERNAME/PASSWORD/DISPLAY_NAME`，完成改密后从文件删除并重启。

`/etc/systemd/system/groupwork-backend.service`：

```ini
[Unit]
Description=Groupwork Spring Boot backend
After=network.target mysql.service groupwork-algorithm.service
Wants=groupwork-algorithm.service

[Service]
Type=simple
User=groupwork
Group=groupwork
WorkingDirectory=/opt/groupwork/backend
EnvironmentFile=/etc/groupwork/backend.env
ExecStart=/usr/bin/java -jar /opt/groupwork/backend/groupwork-1.0.0.jar
SuccessExitStatus=143
Restart=on-failure
RestartSec=5
NoNewPrivileges=true
PrivateTmp=true

[Install]
WantedBy=multi-user.target
```

保护环境文件并启动：

```bash
sudo chown root:groupwork /etc/groupwork/*.env
sudo chmod 640 /etc/groupwork/*.env
sudo install -d -o groupwork -g groupwork -m 750 /var/lib/groupwork/uploads
sudo systemctl daemon-reload
sudo systemctl enable --now groupwork-algorithm groupwork-backend
```

### 6.3 Nginx 与 HTTPS

```nginx
server {
    listen 443 ssl http2;
    server_name groupwork.example.edu.cn;

    root /var/www/groupwork;
    index index.html;
    client_max_body_size 25m;

    location / {
        try_files $uri $uri/ /index.html;
    }

    location /api/ {
        proxy_pass http://127.0.0.1:8080;
        proxy_http_version 1.1;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        # 覆盖客户端伪造的转发头，不要使用 $proxy_add_x_forwarded_for。
        proxy_set_header X-Forwarded-For $remote_addr;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_set_header X-Request-Id $request_id;
        proxy_connect_timeout 5s;
        proxy_read_timeout 60s;
    }
}
```

另建 80 端口到 HTTPS 的重定向。证书签发方式取决于校园网络，不在仓库中实现。Spring 使用 Tomcat 原生 forwarded-header 处理，只信任内部代理范围；因此边缘代理必须覆盖而不是追加外部转发头，并禁止公网直连 8080。不要为算法服务增加公网 `location /v1/`。

## 7. 模型部署与激活

当前签入产物 `paper-score-p4 / xgb-p4-seed2025-172a3cf9486e` 的 holdout 指标为 RMSE `0.4828993804`、MAE `0.3568366849`、R² `0.9997004404`，已达到管理端准入阈值；模型文件 SHA-256 为 `172a3cf9486e2fa88c0d37c0401844521abdf426a6f17bf66271edb2154c162d`。`ELIGIBLE` 只表示离线准入通过，不代表数据库已注册、算法服务已加载或平台已激活。按以下顺序部署：

1. 训练生成新的独立版本目录，不覆盖正在服务的目录。
2. 对照 `sha256.json` 校验 `xgboost-model.json`、`metadata.json`、`metrics.json`。
3. 确认 `metadata.featureOrder` 为 `contribution,actual_group_size,group_score`，`ruleVersion=paper-mcqs-v1`，版本与 Java 契约一致。
4. 原子切换 `ALGORITHM_MODEL_DIR` 指向新目录并重启算法服务。
5. 调用 `/health` 和带内部 Token 的 `/v1/model`，确认实际加载的 `modelVersion`。
6. 通过管理员模型注册接口提交相同版本、`algorithm-service://artifacts/xgboost-model.json`、哈希、特征、超参数和完整指标。
7. 注册达到阈值成为 `VALIDATED` 后再激活。
8. 用非生产作业做一轮模型、回退、偏差异常和教师复核冒烟，再用于正式课程。

如果 Java 激活版本与 Python 返回的版本或 `artifactSha256` 不同，整批模型输出会被拒绝并回退规则分。

## 8. 健康检查与冒烟

PowerShell：

```powershell
Invoke-RestMethod http://127.0.0.1:8000/health
Invoke-RestMethod http://127.0.0.1:8080/actuator/health
Invoke-WebRequest http://127.0.0.1:5173/
```

Linux：

```bash
curl --fail http://127.0.0.1:8000/health
curl --fail http://127.0.0.1:8080/actuator/health
curl --fail https://groupwork.example.edu.cn/
```

算法健康的 `modelAvailable=false` 表示服务可用但模型不可用，不等于规则评分不可用。正式启用模型时还应校验 `/v1/model`：

```bash
curl --fail -H "Authorization: Bearer $ALGORITHM_SERVICE_TOKEN" \
  http://127.0.0.1:8000/v1/model
```

最低业务冒烟：登录 → `/auth/me` → 管理员工作台；再用教师、学生分别验证自己的列表和跨角色 403。完整评分闭环见 `TEAM_HANDOFF.md`。

## 9. 备份与恢复

每天至少备份：

1. MySQL 全库。
2. `STORAGE_ROOT` 上传目录。
3. 每个已登记/激活模型的完整产物目录。
4. 当前 JAR、前端构建和部署配置版本；密钥由单独的密钥系统备份，不和普通数据包明文存放。

MySQL 示例：

```bash
mysqldump --single-transaction --routines --triggers --hex-blob \
  -u backup_user -p groupwork | gzip > groupwork-$(date +%F-%H%M).sql.gz
tar -C /var/lib/groupwork -czf uploads-$(date +%F-%H%M).tar.gz uploads
```

Windows 可用 `mysqldump.exe` 输出到受保护目录，并由任务计划程序轮转。不要把数据库密码写在 `.bat` 或命令行参数中。

恢复顺序：停止写入 → 恢复数据库 → 恢复上传目录 → 恢复与数据库激活版本一致的模型目录 → 启动算法 → 启动 Java → 冒烟。至少定期在隔离环境演练一次恢复；“备份命令成功”不等于可恢复。

## 10. 升级与回滚

- 升级前备份数据库、上传和当前模型。
- 先在数据库副本执行 Flyway 并跑测试，再安排生产维护窗口。
- Flyway 迁移是向前的；仓库没有自动 down migration。迁移后回滚 JAR 前必须确认旧 JAR 兼容新结构。
- 模型回滚采用新旧版本目录切换、算法重启、数据库模型版本重新激活；不覆盖旧产物。
- 前端静态文件用版本目录或原子软链接切换，保留上一版供快速回退。
- 发现评分错误时优先停止新的发布动作并保留运行快照，不直接批量改 `score_result`。

## 11. 生产安全清单

- [ ] 只通过 HTTPS 暴露前端与 `/api/`。
- [ ] MySQL、8080、8000 不对公网监听或由防火墙限制。
- [ ] JWT 与内部 Token 使用独立随机值并定期轮换。
- [ ] 删除首次启动环境中的临时管理员密码。
- [ ] CORS 只允许实际前端源。
- [ ] 数据库账号不是 root，备份账号只读必要对象。
- [ ] 上传目录不可由 Web 服务器直接静态公开。
- [ ] 日志不记录密码、Token、完整申诉证据或上传正文。
- [ ] 数据库、上传、模型产物和备份都有访问控制与恢复演练。
- [ ] 激活模型的版本、指标、特征、规则版本和哈希完全匹配。
- [ ] 上线前完成角色隔离、模型回退、人工复核和申诉闭环验证。

## 12. pbl.luzujian.com 的独立 Compose 部署

服务器目录为 `/root/workspace/pbl`，运行配置为 `deploy/compose.production.yaml`，Compose 项目名固定为 `pbl`。此配置使用本地或 CI 已编译的 `backend/target/groupwork-1.0.0.jar` 和 `frontend/dist`，通过 `deploy/Dockerfile.runtime` 构建运行镜像，不在生产服务器执行 Maven 或 Node 构建。修改源码后必须先重新测试、编译并上传对应产物。

MySQL、算法、后端和前端分别设置 640、384、768、64 MiB 的内存上限，并限制容器日志大小。后端、算法和数据库统一使用 `Asia/Shanghai` 时区。MySQL、算法和后端没有发布宿主机端口；前端仅绑定 `127.0.0.1:15880`，对外流量由共享 HTTPS 网关通过 `pbl-edge` 网络的 `pbl-web` 别名转发。

算法镜像安装 [XGBoost 官方 CPU 包](https://xgboost.readthedocs.io/en/stable/install.html#minimal-installation-cpu-only)，不包含 GPU 依赖。构建时可通过 `.env` 的 `PBL_DEBIAN_MIRROR` 和 `PBL_PIP_INDEX_URL` 选择下载源；本次服务器使用阿里云镜像。模型文件、校验哈希和评分公式保持原样。

所有运维命令显式指定私密环境文件和生产 Compose 文件：

```bash
cd /root/workspace/pbl
docker compose --env-file .env -f deploy/compose.production.yaml config --quiet
docker compose --env-file .env -f deploy/compose.production.yaml ps
docker compose --env-file .env -f deploy/compose.production.yaml logs --tail=100 backend algorithm
```

更新时设置新的 `PBL_RELEASE`，先备份，再构建与启动本项目的镜像。重建后端后，应执行 `docker compose --env-file .env -f deploy/compose.production.yaml exec -T frontend nginx -s reload`，刷新前端 Nginx 缓存的后端地址。不要对其他 Compose 项目执行重建，也不要全局清理 Docker 资源。

共享网关配置位于 `/root/workspace/internship-system/nginx/nginx.conf`。该文件是单文件绑定挂载，修改时必须保留 inode，先 `nginx -t` 再平滑重载。PBL 的外部网络也已写入共享网关的 Compose override，后续重建网关时仍可解析 `pbl-web`。

共享网关现有容器的 `HostConfig.NetworkMode` 仍引用 `internai-edge`。为保持现有容器运行，本次保留了一个无容器、标记用途为 `shared-ingress-legacy-networkmode-compatibility` 的同名兼容网络。它不运行旧测试系统；在共享网关通过当前 Compose 配置重建并确认不再引用该名称前，不要删除这个兼容网络。

HTTPS 使用 `pbl.luzujian.com` 独立证书和 DNS-01 验证，凭据仅位于服务器 `/root/.secrets/certbot/cloudflare-pbl.ini`，权限为 600。续期由现有 Certbot 定时器触发；`/etc/letsencrypt/renewal-hooks/deploy/renew-pbl.sh` 仅处理 PBL 证书，复制证书后检查并平滑重载共享 Nginx。

```bash
certbot renew --cert-name pbl.luzujian.com --dry-run \
  --run-deploy-hooks --non-interactive --no-random-sleep-on-renew
```

首次启动只初始化管理员、规则和模型元数据，不导入本机合成测试师生或课程。管理员完成首次改密后，清空 `.env` 中的 `BOOTSTRAP_ADMIN_*`，并仅重建 PBL 后端。真实密码不写入本文或 Git；本机交付信息在被忽略的 `.codex/deployment/` 受限目录中。

部署前的共享配置、部署记录和初始数据库/上传备份位于 `/root/workspace/deploy-backups/pbl-deploy-20261018`，初始备份另存本机并校验 SHA-256。密钥配置与数据压缩包分开保存。初始备份不代表已完成恢复演练，也不代替后续定期业务数据备份。
