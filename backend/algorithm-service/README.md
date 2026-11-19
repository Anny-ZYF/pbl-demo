# 平滑分段差异化评分算法服务

这是独立于 Spring Boot 主后端的 Python FastAPI 内部服务，实现 MCQS 贡献量化辅助函数、`smoothstep-v1` 确定性规则评分和可选 XGBoost 推理。规则定义来自项目方提供的 `1108.py`，模型制品为交付的 `xgb_score_model4.joblib`。

即使没有安装模型依赖、没有模型文件或哈希校验失败，服务也能启动，并在预测时稳定返回规则分。

## 评分口径

- 标准组规模 `p` 为 1–4；**实际人数 `r` 不参与评分**，也不出现在 HTTP 请求中。
- 分支权重之和必须为 1；`workloadMatrix` 按「成员 × 分支」排列，每个分支的份额之和必须为 1。
- 后端按 `C = Q × W^T` 计算成员贡献（0–1 的比例，不是百分数）。
- 规则版本 `smoothstep-v1`：`S(t)=3t²−2t³`，阈值 `a=1/p`。

```text
x <= a:  t = x·p             score = G·S(t)
x >  a:  t = (x−a)/(1−a)     score = G + 20·S(t)
score = clip(score, 0, 100)
```

- `G`（团队作品分）为 0–100，所有分数输出（规则分、模型分、建议分）都裁剪到 **0–100**。
- 模型覆盖 `p=1..4` 全部标准组规模，**没有 `p=4` 门控，也不返回 `rule_only`**。
- 模型标签由上述确定性公式生成，不是真实课堂标注；指标只代表对规则的拟合程度。

## 本地运行

```powershell
cd algorithm-service
py -3.11 -m venv .venv
.\.venv\Scripts\Activate.ps1
python -m pip install -r requirements-runtime.txt
$env:ALGORITHM_SERVICE_TOKEN = "replace-with-a-long-random-internal-token"
$env:ALGORITHM_MODEL_DIR = (Resolve-Path '.\artifacts').Path
uvicorn app.main:app --host 127.0.0.1 --port 8000
```

运行时依赖 `numpy / pandas / scikit-learn / joblib / xgboost`。服务不自动读取 `.env`，生产环境应由进程管理器注入环境变量。`/health` 用于探活且不鉴权；所有 `/v1/*` 接口必须携带内部 Bearer Token。OpenAPI 和交互式文档默认关闭。

**安全**：`joblib` 基于 pickle 序列化，加载即反序列化执行代码。只加载仓库内、SHA-256 已登记的可信制品。

## API

### `GET /health`

返回规则版本、模型可用状态和不可用原因。

### `GET /v1/model`

返回已加载模型的 metadata、metrics 和哈希清单。需要：

```text
Authorization: Bearer <ALGORITHM_SERVICE_TOKEN>
```

### `POST /v1/predict`

Spring Boot 后端先根据 `W、Q` 计算贡献，再一次提交最多 1000 名学生：

```json
{
  "items": [
    {
      "studentId": "20230101",
      "contribution": 0.6,
      "standardGroupSize": 2,
      "groupScore": 60
    }
  ]
}
```

请求体 `extra="forbid"`；`contribution` 为 0–1，`groupScore` 为 0–100，`standardGroupSize` 为 1–4。附带旧字段 `actualGroupSize` 会返回 422。

模型不可用时响应：

```json
{
  "ruleVersion": "smoothstep-v1",
  "modelVersion": null,
  "artifactSha256": null,
  "items": [
    {
      "studentId": "20230101",
      "ruleScore": 62.08,
      "modelScore": null,
      "finalSuggestion": 62.08,
      "mode": "rule_fallback",
      "delta": null,
      "anomaly": false,
      "explanation": "model_artifacts_missing"
    }
  ]
}
```

- `mode=model`：模型可用且通过校验，`finalSuggestion` 使用裁剪后的模型分。
- `mode=rule_fallback`：模型缺失、哈希不符、推理失败或未激活，`finalSuggestion` 使用规则分。
- `delta` 是模型分与规则分的绝对偏差；`anomaly=true` 表示偏差严格大于 2 分，仍须由教师复核。

Java 侧还会整批核对 `ruleVersion`、`modelVersion`、`artifactSha256`、成员 ID 集合和分数范围，任一不符即整批回退规则分。

## 数据集、训练与制品

网格由 `app/dataset.py` 统一生成，`1108.py` 的口径为 `p∈{1,2,3,4}` × `x∈[0,1]` 步长 0.01 × `G∈[0,100]` 整数，共 **40804** 行，中文列名 `标准人数 / 个人贡献度 / 作业总分`，标签 `个人成绩`。

```powershell
python -m scripts.generate_dataset
python -m scripts.train_model --output artifacts/candidate-model.joblib
python -m scripts.build_artifacts
```

- `generate_dataset`：只重建 `artifacts/score-samples.csv` 及其 SHA-256 摘要。
- `train_model`：可选的重训练工具，用 `XGBRegressor(n_estimators=150, max_depth=5, learning_rate=0.07, random_state=42)` 与 80/20 留出训练**候选模型**，绝不覆盖交付制品。
- `build_artifacts`：读取交付的 `artifacts/score-model.joblib`，按同一网格重新计算**真实 holdout 指标**，重建 `metadata.json`、`metrics.json`、`sha256.json`。不会重新训练，也不会编造指标。

`artifacts/` 下五份制品：

| 文件 | 说明 |
|---|---|
| `score-model.joblib` | 交付的 sklearn `XGBRegressor` |
| `metadata.json` | 版本、`modelFormat=joblib-sklearn`、中文特征顺序、参数、`scoreRange=[0,100]`、哈希 |
| `metrics.json` | 实测 holdout RMSE/MAE/R²、准入阈值与切分信息 |
| `sha256.json` | 四份制品的 SHA-256 清单 |
| `score-samples.csv` | 40804 行可审计训练数据 |

`ModelRuntime.load()` 在进程启动时校验必需文件、`sha256.json` 与 metadata 中的 `modelSha256`、特征顺序和准入状态；缺文件、哈希不符、依赖缺失或加载失败时进入规则回退。独立留出集必须同时满足 `RMSE <= 1.0`、`MAE <= 0.8`、`R² >= 0.99` 才能标记 `ELIGIBLE` 并被加载。

模型注册约定：`modelKey=smoothstep-score`，`artifactUri=algorithm-service://artifacts/score-model.joblib`，特征顺序严格为 `标准人数, 个人贡献度, 作业总分`。管理员端已固定该契约，旧 `paper-score-p4` / `*.json` 制品不再被接受。

## Docker

生产镜像按 `requirements-runtime.txt` 安装 FastAPI、NumPy、pandas、scikit-learn、joblib 与 XGBoost CPU 推理依赖，不包含 pytest 等开发依赖，并以非 root 用户运行：

```powershell
docker build -t groupwork-algorithm-service .
docker run --rm -p 8000:8000 -e ALGORITHM_SERVICE_TOKEN="replace-me" groupwork-algorithm-service
```

镜像内置 `/health` 健康检查，服务监听 `0.0.0.0:8000`。

## 测试

```powershell
python -m pip install -r requirements-runtime.txt -r requirements-dev.txt
pytest -q
```

测试覆盖 MCQS 求和约束、规则公式与 0/100 边界、40804 行确定性数据、Java/Python 跨实现等值样例、Bearer 鉴权、旧 `actualGroupSize` 字段被拒、产物哈希一致性、模型降级与偏差异常标记。HTTP 接口接收后端已经计算好的单个学生贡献；`W、Q -> C` 的计算由规则库测试覆盖。

完整算法说明与变更规则见 `docs/SCORING_ALGORITHM.md`，接口契约见 `docs/API_CONTRACT.md` 第 7 节。
