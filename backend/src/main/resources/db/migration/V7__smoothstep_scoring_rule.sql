-- Switch the platform to the smoothstep scoring rule (1108.py) and its joblib model.
--
-- Historical rows are preserved: already published score_result, scoring_run,
-- scoring_input_snapshot and audit_event rows are never rewritten. The
-- actual_group_size columns stay as nullable legacy projections; the new
-- scoring path simply stops writing them (chk_scoring_run_sizes already allows NULL).

-- 1. The generated metadata column encoded the old threshold 2/(p+2).
--    Rebuild it for the new threshold 1/p. It is metadata only; no application
--    code reads it and no published score depends on it.
ALTER TABLE scoring_rule
    DROP COLUMN neutral_contribution,
    ADD COLUMN neutral_contribution DECIMAL(9,6)
        GENERATED ALWAYS AS (CAST(1.0 / standard_group_size AS DECIMAL(9,6))) STORED;

-- 1b. Future rows must not silently fall back to the retired formula version.
ALTER TABLE scoring_rule
    ALTER COLUMN formula_version SET DEFAULT 'smoothstep-v1',
    ALTER COLUMN paper_method SET DEFAULT 'SMOOTHSTEP';

-- 2. Retire the printed-paper rule and register the smoothstep rule.
UPDATE scoring_rule
SET status = 'RETIRED'
WHERE rule_key = 'MCQS_PAPER_RULE' AND version_no = 1 AND status <> 'RETIRED';

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
            'minScore', 0,
            'maxScore', 100,
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

SET @gw_smoothstep_rule_id = (
    SELECT id FROM scoring_rule
    WHERE rule_key = 'SMOOTHSTEP_SCORE_RULE' AND version_no = 1
    LIMIT 1
);

-- New runs must record the active rule version. Published results keep their
-- own scoring_run.scoring_rule_id and are untouched.
UPDATE assignment
SET scoring_rule_id = @gw_smoothstep_rule_id
WHERE scoring_rule_id IS NULL
   OR scoring_rule_id IN (SELECT id FROM (SELECT id FROM scoring_rule
                                          WHERE rule_key = 'MCQS_PAPER_RULE') AS retired_rules);

-- 3. Retire the old p=4 XGBoost JSON version and activate the delivered joblib model.
UPDATE model_version
SET status = 'RETIRED'
WHERE model_key = 'paper-score-p4' AND status IN ('ACTIVE', 'VALIDATED');

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
