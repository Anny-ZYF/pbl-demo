# MCQS 差异化评分算法服务

这是独立于 Spring Boot 主后端的 Python 3.11 FastAPI 内部服务，实现论文中的 MCQS 贡献量化、规则评分和可选 XGBoost 推理。

即使没有安装 XGBoost 或没有模型文件，服务也能启动，并在预测时稳定返回规则分。

## 评分口径

- 标准组规模 `p` 为 1–4，实际人数 `r` 必须满足 `1 <= r <= p + 1`。
- 分支权重之和必须为 1。
- `workloadMatrix` 按“成员 × 分支”排列；每个分支的所有成员份额之和必须为 1。
- 后端按 `C = Q × W^T` 计算成员贡献。
- `p=4` 时，高、低贡献区间分别应用论文给出的两段贡献变换。
- `p=4` 的低区间变换覆盖 `c=0`，即先得到 `c'=0.12` 再代入低分支；`p!=4` 时 `c=0` 按未变换公式的极限返回 0 分。这是论文未定义边界上的系统版本决策。
- 论文声明分数范围为 0–100，因此规则结果最终限制在该范围。
- 论文只对 `p=4` 训练模型；其他 `p` 明确返回 `rule_only`。

规则版本：`paper-mcqs-v1`。

## 本地运行

```powershell
cd algorithm-service
py -3.11 -m venv .venv
.\.venv\Scripts\Activate.ps1
python -m pip install -r requirements-runtime.txt
Copy-Item .env.example .env
$env:ALGORITHM_SERVICE_TOKEN = "replace-with-a-long-random-internal-token"
uvicorn app.main:app --host 127.0.0.1 --port 8000
```

服务不自动读取 `.env`，生产环境应由进程管理器注入环境变量。`/health` 用于探活且不鉴权；所有 `/v1/*` 接口必须携带内部 Bearer Token。OpenAPI 和交互式文档默认关闭。

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
      "contribution": 0.62,
      "standardGroupSize": 4,
      "actualGroupSize": 2,
      "groupScore": 80
    }
  ]
}
```

响应契约：

```json
{
  "ruleVersion": "paper-mcqs-v1",
  "modelVersion": null,
  "artifactSha256": null,
  "items": [
    {
      "studentId": "20230101",
      "ruleScore": 85.2,
      "modelScore": null,
      "finalSuggestion": 85.2,
      "mode": "rule_fallback",
      "delta": null,
      "anomaly": false,
      "explanation": "model_artifacts_missing"
    }
  ]
}
```

返回同时包含规则分、可选模型分和最终建议分：

- `mode=model`：XGBoost 可用，`finalSuggestion` 使用模型分。
- `mode=rule_fallback`：模型缺失或推理失败，`finalSuggestion` 使用规则分。
- `mode=rule_only`：`p != 4`，明确只使用规则分。
- `delta` 是模型分与规则分的绝对偏差。
- `anomaly=true` 表示偏差严格大于 2 分，应由教师复核。

## 生成论文式数据集

```powershell
python -m scripts.generate_dataset
```

固定生成 1785 条样本：`p=4`；`r=2..5` 时贡献与团队分各取 21 个等距值，每种人数 441 条；`r=1` 时贡献固定为 1，共 21 条。脚本同时输出 CSV 的 SHA-256。

## 训练 XGBoost

训练是可选能力。默认使用已验证的固定参数，并执行按实际组规模 `r` 分层的 80/20 留出和训练集内 5 折分层交叉验证：

```powershell
python -m pip install -r requirements-train.txt
python -m scripts.train_model --output-dir artifacts
```

需要重新执行可复现的贝叶斯参数搜索时：

```powershell
python -m scripts.train_model --search --search-iterations 30 --output-dir artifacts
```

当前随项目保存的产物使用固定已验证参数，metadata 中 `search.executed=false`，没有虚称执行过贝叶斯搜索。若使用 `--search`，则通过 `BayesSearchCV`、5 折分层 CV 和固定种子 2025 搜索，并把实际执行信息写入 metadata。

训练固定随机种子为 `2025`，输出：

- `xgboost-model.json`：XGBoost 原生 JSON 模型。
- `metadata.json`：特征顺序、规则版本、数据哈希、参数和样本数。
- `metrics.json`：测试集 RMSE、MAE、R²。
- `sha256.json`：所有产物的 SHA-256。
- `synthetic-samples.csv`：可审计的训练样本。

模型加载前会校验文件 manifest、metadata 中的 `modelSha256`、实际模型哈希、特征顺序和准入状态。独立留出集必须同时满足 `RMSE <= 1.0`、`MAE <= 0.8`、`R² >= 0.99` 才会标记为 `ELIGIBLE` 并被加载，否则保留产物但服务强制规则回退。项目不生成或加载 pickle 文件。

这些指标只表示模型对规则生成标签的拟合程度，不代表真实教学公平性。正式上线应保留规则分、模型分、最终分、规则版本和模型版本，并将偏差超过 2 分的结果交由教师复核。

模型注册约定：`modelKey=paper-score-p4`，`artifactUri=algorithm-service://artifacts/xgboost-model.json`。XGBoost 特征顺序严格为 `contribution`、`actual_group_size`、`group_score`；请求字段 `standardGroupSize` 只用于等于 4 的门控，不进入模型。

## Docker

生产镜像安装 FastAPI、NumPy 和 XGBoost 推理依赖，不包含 pytest 等开发依赖，并以非 root 用户运行：

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

测试覆盖 MCQS 求和约束、规则边界、`p=4` 两段变换、1785 条确定性数据、Bearer 鉴权、产物三重哈希一致性、模型降级、`p != 4` 和模型偏差标记。HTTP 接口接收后端已经计算好的单个学生贡献，MCQS 的 `W、Q -> C` 计算函数仍由规则库测试覆盖。
