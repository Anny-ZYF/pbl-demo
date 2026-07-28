-- Groupwork minimal production seed
-- Target: MySQL 8.0.29+
-- Safe to rerun. Contains no plaintext password or deployable model artifact.

SET NAMES utf8mb4 COLLATE utf8mb4_0900_ai_ci;
SET time_zone = '+08:00';

-- Paper-aligned rule metadata. W is stored in assignment_parts.weight, Q in
-- workload_item.share, and C = Q * W^T is computed per scoring snapshot.
INSERT INTO scoring_rule
    (rule_key, version_no, name, paper_method, formula_version,
     standard_group_size, formula_assumptions, feature_schema_json,
     normalization_json, min_score, max_score, status)
VALUES
    (
        'MCQS_PAPER_RULE',
        1,
        '多维贡献量化与差异化评分规则',
        'PAPER_MCQS',
        'paper-mcqs-v1',
        4,
        JSON_OBJECT(
            'ruleVersion', 'paper-mcqs-v1',
            'weightConstraint', 'w_i >= 0; sum(W) = 1',
            'shareConstraint', 'q_ij >= 0; sum_j(Q_ij) = 1 for every assignment part',
            'contributionEquation', 'C = Q * transpose(W); sum(C) = 1',
            'groupSizeCoefficient', JSON_OBJECT(
                'whenEqual', 'a = exp(p-r) = 1',
                'whenDifferenceInMinus1To3', 'a = 10 * (p-r)'
            ),
            'baselineEquation', 'meanScore = f + a * sin(pi*f/100)',
            'neutralContribution', '2/(p+2)',
            'paperBoundaryNormalizationForP4', JSON_OBJECT(
                'highBranch', 'cPrime = 0.54*c + 0.17',
                'lowBranch', 'cPrime = 0.67*c + 0.12'
            ),
            'actualGroupSizeRange', '1 <= r <= p+1',
            'scoreClamp', '[0,100]',
            'reviewPolicy', 'model-rule delta and anomalous inputs require teacher review'
        ),
        JSON_ARRAY(
            JSON_OBJECT('name', 'contribution', 'type', 'decimal', 'range', '[0,1]'),
            JSON_OBJECT('name', 'actualGroupSize', 'type', 'integer'),
            JSON_OBJECT('name', 'groupScore', 'type', 'decimal', 'range', '[0,100]')
        ),
        JSON_OBJECT(
            'standardGroupSizeGate', 4,
            'standardGroupSizeRole', 'applicability gate and request context; excluded from XGBoost feature vector',
            'assignmentPartWeightScale', '[0,1]',
            'workloadShareScale', '[0,1]',
            'legacyPercentConversion', 'values greater than 1 are divided by 100 during migration'
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
    max_score = VALUES(max_score);

-- Deterministic paper rule is runnable without a model artifact.
INSERT INTO model_version
    (model_key, version, algorithm, status, feature_schema_json,
     hyperparameters_json, metrics_json, activated_at)
VALUES
    (
        'MCQS_RULE',
        'paper-v1',
        'RULE',
        'ACTIVE',
        JSON_ARRAY('contribution', 'standardGroupSize', 'actualGroupSize', 'groupScore'),
        JSON_OBJECT('formulaVersion', 'paper-mcqs-v1'),
        JSON_OBJECT('validation', 'deterministic formula metadata only'),
        CURRENT_TIMESTAMP(3)
    )
ON DUPLICATE KEY UPDATE
    feature_schema_json = VALUES(feature_schema_json),
    hyperparameters_json = VALUES(hyperparameters_json),
    metrics_json = VALUES(metrics_json);

-- Registered verified artifact. It remains VALIDATED until an administrator
-- explicitly activates it; database uniqueness prevents two active versions.
INSERT INTO model_version
    (model_key, version, algorithm, status, artifact_uri, artifact_sha256,
     feature_schema_json, hyperparameters_json, metrics_json)
VALUES
    (
        'paper-score-p4',
        'xgb-p4-seed2025-172a3cf9486e',
        'XGBOOST',
        'VALIDATED',
        'algorithm-service://artifacts/xgboost-model.json',
        '172a3cf9486e2fa88c0d37c0401844521abdf426a6f17bf66271edb2154c162d',
        JSON_ARRAY('contribution', 'actual_group_size', 'group_score'),
        JSON_OBJECT(
            'selection', 'deterministic tuned fixed parameters',
            'parameters', JSON_OBJECT(
                'n_estimators', 800,
                'learning_rate', 0.04,
                'max_depth', 6,
                'min_child_weight', 0.5,
                'subsample', 1.0,
                'colsample_bytree', 1.0,
                'reg_alpha', 0.0,
                'reg_lambda', 1.0,
                'objective', 'reg:squarederror',
                'eval_metric', 'rmse',
                'tree_method', 'hist',
                'random_state', 2025,
                'n_jobs', 1
            ),
            'search', JSON_OBJECT(
                'available', TRUE,
                'method', 'BayesSearchCV',
                'executed', FALSE,
                'requested', FALSE,
                'note', 'Bayesian search is available via --search but was not executed for this artifact'
            ),
            'standardGroupSizeGate', 4,
            'standardGroupSizeRole', 'request context only; not a trained feature'
        ),
        JSON_OBJECT(
            'eligibility', 'ELIGIBLE',
            'holdout', JSON_OBJECT('rmse', 0.482899, 'mae', 0.356837, 'r2', 0.999700),
            'crossValidation', JSON_OBJECT('folds', 5, 'meanRmse', 0.766012,
                                           'meanMae', 0.525321, 'meanR2', 0.999259),
            'datasetSha256', '7e033aef431573642fa5b5d6cc147794647084aa2c3323056e5a39a30081d146'
        )
    )
ON DUPLICATE KEY UPDATE
    status = IF(model_version.status = 'ACTIVE', 'ACTIVE', VALUES(status)),
    artifact_uri = VALUES(artifact_uri),
    artifact_sha256 = VALUES(artifact_sha256),
    feature_schema_json = VALUES(feature_schema_json),
    hyperparameters_json = VALUES(hyperparameters_json),
    metrics_json = VALUES(metrics_json);

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
