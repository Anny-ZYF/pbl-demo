-- Groupwork minimal production seed
-- Target: MySQL 8.0.29+
-- Safe to rerun. Contains no plaintext password or deployable model artifact.

SET NAMES utf8mb4 COLLATE utf8mb4_0900_ai_ci;
SET time_zone = '+08:00';

-- Smoothstep rule metadata. W is stored in assignment_parts.weight, Q in
-- workload_item.share, and C = Q * W^T is computed per scoring snapshot.
-- The active scoring path does not use actual_group_size.
INSERT INTO scoring_rule
    (rule_key, version_no, name, paper_method, formula_version,
     standard_group_size, formula_assumptions, feature_schema_json,
     normalization_json, min_score, max_score, status)
VALUES
    (
        'SMOOTHSTEP_SCORE_RULE',
        1,
        '平滑分段差异化评分规则',
        'SMOOTHSTEP',
        'smoothstep-v1',
        4,
        JSON_OBJECT(
            'ruleVersion', 'smoothstep-v1',
            'weightConstraint', 'w_i >= 0; sum(W) = 1',
            'shareConstraint', 'q_ij >= 0; sum_j(Q_ij) = 1 for every assignment part',
            'contributionEquation', 'C = Q * transpose(W); sum(C) = 1',
            'threshold', 'a = 1/p',
            'lowBranch', 't = x*p; score = G*(3*t^2 - 2*t^3)',
            'highBranch', 't = (x-a)/(1-a); score = G + 20*(3*t^2 - 2*t^3)',
            'boostCap', 20,
            'scoreClamp', '[0,100]',
            'standardGroupSizeRange', '[1,4]',
            'actualGroupSize', 'not used by the scoring rule or the model',
            'modelScope', 'p=1..4',
            'reviewPolicy', 'teacher must review every member; model output is a suggestion only'
        ),
        JSON_ARRAY(
            JSON_OBJECT('name', '标准人数', 'jsonField', 'standardGroupSize', 'type', 'integer', 'range', '[1,4]'),
            JSON_OBJECT('name', '个人贡献度', 'jsonField', 'contribution', 'type', 'decimal', 'range', '[0,1]'),
            JSON_OBJECT('name', '作业总分', 'jsonField', 'groupScore', 'type', 'decimal', 'range', '[0,100]')
        ),
        JSON_OBJECT(
            'standardGroupSizeRange', '[1,4]',
            'scoreRange', '[0,100]',
            'boostCap', 20,
            'scoreBoundaries', 'min_score = 0.00, max_score = 100.00',
            'actualGroupSizeRole', 'excluded from features and formula'
        ),
        0.00,
        100.00,
        'ACTIVE'
    )
ON DUPLICATE KEY UPDATE
    name = VALUES(name),
    paper_method = VALUES(paper_method),
    formula_version = VALUES(formula_version),
    formula_assumptions = VALUES(formula_assumptions),
    feature_schema_json = VALUES(feature_schema_json),
    normalization_json = VALUES(normalization_json),
    min_score = VALUES(min_score),
    max_score = VALUES(max_score),
    status = VALUES(status);

-- Deterministic rule is runnable without a model artifact.
INSERT INTO model_version
    (model_key, version, algorithm, status, feature_schema_json,
     hyperparameters_json, metrics_json, activated_at)
VALUES
    (
        'MCQS_RULE',
        'smoothstep-rule-v1',
        'RULE',
        'ACTIVE',
        JSON_ARRAY('标准人数', '个人贡献度', '作业总分'),
        JSON_OBJECT('formulaVersion', 'smoothstep-v1'),
        JSON_OBJECT('validation', 'deterministic formula metadata only'),
        CURRENT_TIMESTAMP(3)
    )
ON DUPLICATE KEY UPDATE
    feature_schema_json = VALUES(feature_schema_json),
    hyperparameters_json = VALUES(hyperparameters_json),
    metrics_json = VALUES(metrics_json);

-- Delivered joblib artifact. Its hash is registered in artifacts/sha256.json;
-- the runtime refuses to load it if any digest or the feature order differs.
INSERT INTO model_version
    (model_key, version, algorithm, status, artifact_uri, artifact_sha256,
     feature_schema_json, hyperparameters_json, metrics_json, activated_at)
VALUES
    (
        'smoothstep-score',
        'smoothstep-xgb-a2b2c30a05b1',
        'XGBOOST',
        'ACTIVE',
        'algorithm-service://artifacts/score-model.joblib',
        'a2b2c30a05b13b557748825f2d356c4563b1903edf4c53168c1bb46ee0e83644',
        JSON_ARRAY('标准人数', '个人贡献度', '作业总分'),
        JSON_OBJECT(
            'selection', 'delivered by project owner (1108.py)',
            'modelFormat', 'joblib-sklearn',
            'parameters', JSON_OBJECT(
                'n_estimators', 150,
                'learning_rate', 0.07,
                'max_depth', 5,
                'random_state', 42
            ),
            'split', JSON_OBJECT('method', 'train_test_split', 'testSize', 0.2, 'randomState', 42),
            'ruleVersion', 'smoothstep-v1',
            'standardGroupSizeRange', '[1,4]',
            'scoreRange', '[0,100]'
        ),
        JSON_OBJECT(
            'eligibility', 'ELIGIBLE',
            'holdout', JSON_OBJECT('rmse', 0.8942731731861232,
                                   'mae', 0.6381276982293735,
                                   'r2', 0.9992094929712033),
            'thresholds', JSON_OBJECT('rmseMax', 1.0, 'maeMax', 0.8, 'r2Min', 0.99),
            'datasetRows', 40804,
            'note', 'Measured against the deterministic smoothstep rule grid, not real student data.'
        ),
        CURRENT_TIMESTAMP(3)
    )
ON DUPLICATE KEY UPDATE
    status = VALUES(status),
    artifact_uri = VALUES(artifact_uri),
    artifact_sha256 = VALUES(artifact_sha256),
    feature_schema_json = VALUES(feature_schema_json),
    hyperparameters_json = VALUES(hyperparameters_json),
    metrics_json = VALUES(metrics_json),
    activated_at = VALUES(activated_at);

INSERT INTO model_version
    (model_key, version, algorithm, status, feature_schema_json, metrics_json)
VALUES
    (
        'LEGACY_IMPORT',
        'legacy-v1',
        'LEGACY',
        'RETIRED',
        JSON_ARRAY('legacyFinalScore'),
        JSON_OBJECT('purpose', 'traceability for scores imported from the pre-refactor schema')
    )
ON DUPLICATE KEY UPDATE
    feature_schema_json = VALUES(feature_schema_json),
    metrics_json = VALUES(metrics_json);
