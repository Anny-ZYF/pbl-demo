from __future__ import annotations

from fastapi.testclient import TestClient

import app.main as main_module
from app.rules import individual_score


client = TestClient(main_module.app)


def _payload(standard_group_size: int = 4) -> dict:
    return {
        "items": [
            {
                "studentId": "student-1",
                "contribution": 0.62,
                "standardGroupSize": standard_group_size,
                "groupScore": 80,
            }
        ]
    }


def _all_sizes_payload() -> dict:
    return {
        "items": [
            {
                "studentId": f"student-{p}",
                "contribution": 0.6,
                "standardGroupSize": p,
                "groupScore": 80,
            }
            for p in range(1, 5)
        ]
    }


def test_health_is_public() -> None:
    response = client.get("/health")
    assert response.status_code == 200
    assert response.json()["status"] == "ok"
    assert response.json()["ruleVersion"] == "smoothstep-v1"


def test_internal_endpoints_require_bearer_token(monkeypatch) -> None:
    monkeypatch.setenv("ALGORITHM_SERVICE_TOKEN", "test-secret")
    assert client.get("/v1/model").status_code == 401
    assert (
        client.get(
            "/v1/model", headers={"Authorization": "Bearer wrong-secret"}
        ).status_code
        == 401
    )
    assert (
        client.get(
            "/v1/model", headers={"Authorization": "Bearer test-secret"}
        ).status_code
        == 200
    )


def test_missing_model_falls_back_to_rule_scores(monkeypatch) -> None:
    monkeypatch.setenv("ALGORITHM_SERVICE_TOKEN", "test-secret")
    monkeypatch.setattr(
        main_module,
        "model_runtime",
        main_module.ModelRuntime(False, "model_artifacts_missing"),
    )
    response = client.post(
        "/v1/predict",
        headers={"Authorization": "Bearer test-secret"},
        json=_payload(),
    )
    assert response.status_code == 200
    result = response.json()["items"][0]
    assert result["mode"] == "rule_fallback"
    assert result["modelScore"] is None
    assert result["finalSuggestion"] == result["ruleScore"]


def test_all_standard_group_sizes_use_the_model(monkeypatch) -> None:
    class FakeModelRuntime:
        available = True
        reason = "loaded"
        metadata = {"version": "test-model", "modelSha256": "a" * 64}
        metrics = {}
        hashes = {}

        @staticmethod
        def predict_batch(features):
            return [50.0 for _ in features]

    monkeypatch.setenv("ALGORITHM_SERVICE_TOKEN", "test-secret")
    monkeypatch.setattr(main_module, "model_runtime", FakeModelRuntime())
    response = client.post(
        "/v1/predict",
        headers={"Authorization": "Bearer test-secret"},
        json=_all_sizes_payload(),
    )
    assert response.status_code == 200
    items = response.json()["items"]
    assert len(items) == 4
    assert {item["studentId"] for item in items} == {
        "student-1", "student-2", "student-3", "student-4"
    }
    assert all(item["mode"] == "model" for item in items)


def test_model_deviation_over_two_is_flagged(monkeypatch) -> None:
    class FakeModelRuntime:
        available = True
        reason = "loaded"
        metadata = {}
        metrics = {}
        hashes = {}

        @staticmethod
        def predict_batch(features):
            return [100.0 for _ in features]

    monkeypatch.setenv("ALGORITHM_SERVICE_TOKEN", "test-secret")
    monkeypatch.setattr(main_module, "model_runtime", FakeModelRuntime())
    response = client.post(
        "/v1/predict",
        headers={"Authorization": "Bearer test-secret"},
        json=_payload(),
    )
    assert response.status_code == 200
    result = response.json()["items"][0]
    assert result["mode"] == "model"
    assert result["anomaly"] is True
    assert result["delta"] > 2


def test_exactly_two_point_deviation_is_not_flagged(monkeypatch) -> None:
    expected_rule = individual_score(4, 80.0, 0.62)

    class FakeModelRuntime:
        available = True
        reason = "loaded"
        metadata = {
            "version": "test-model",
            "modelSha256": "a" * 64,
        }
        metrics = {}
        hashes = {}

        @staticmethod
        def predict_batch(features):
            return [expected_rule + 2.0 for _ in features]

    monkeypatch.setenv("ALGORITHM_SERVICE_TOKEN", "test-secret")
    monkeypatch.setattr(main_module, "model_runtime", FakeModelRuntime())
    response = client.post(
        "/v1/predict",
        headers={"Authorization": "Bearer test-secret"},
        json=_payload(),
    )
    result = response.json()["items"][0]
    assert result["delta"] == 2.0
    assert result["anomaly"] is False
    assert response.json()["modelVersion"] == "test-model"
    assert response.json()["artifactSha256"] == "a" * 64


def test_model_prediction_failure_falls_back_to_rule(monkeypatch) -> None:
    class FailingModelRuntime:
        available = True
        reason = "loaded"
        metadata = {"version": "test-model"}
        metrics = {}
        hashes = {}

        @staticmethod
        def predict_batch(features):
            raise RuntimeError("simulated inference failure")

    monkeypatch.setenv("ALGORITHM_SERVICE_TOKEN", "test-secret")
    monkeypatch.setattr(main_module, "model_runtime", FailingModelRuntime())
    response = client.post(
        "/v1/predict",
        headers={"Authorization": "Bearer test-secret"},
        json=_payload(),
    )
    result = response.json()["items"][0]
    assert result["mode"] == "rule_fallback"
    assert result["modelScore"] is None
    assert result["finalSuggestion"] == result["ruleScore"]
    assert result["explanation"] == "model_prediction_failed:RuntimeError"


def test_response_scores_never_leave_the_zero_to_hundred_range(monkeypatch) -> None:
    class OutOfRangeModelRuntime:
        available = True
        reason = "loaded"
        metadata = {"version": "test-model"}
        metrics = {}
        hashes = {}

        @staticmethod
        def predict_batch(features):
            return [250.0 for _ in features]

    monkeypatch.setenv("ALGORITHM_SERVICE_TOKEN", "test-secret")
    monkeypatch.setattr(main_module, "model_runtime", OutOfRangeModelRuntime())
    response = client.post(
        "/v1/predict",
        headers={"Authorization": "Bearer test-secret"},
        json=_payload(),
    )
    assert response.status_code == 200
    result = response.json()["items"][0]
    assert result["modelScore"] == 100.0
    assert 0.0 <= result["ruleScore"] <= 100.0
    assert 0.0 <= result["finalSuggestion"] <= 100.0


def test_inputs_outside_declared_ranges_are_rejected(monkeypatch) -> None:
    monkeypatch.setenv("ALGORITHM_SERVICE_TOKEN", "test-secret")
    for field, value in (
        ("standardGroupSize", 5),
        ("contribution", 1.2),
        ("groupScore", 101),
    ):
        payload = _payload()
        payload["items"][0][field] = value
        response = client.post(
            "/v1/predict",
            headers={"Authorization": "Bearer test-secret"},
            json=payload,
        )
        assert response.status_code == 422, field


def test_legacy_actual_group_size_field_is_rejected(monkeypatch) -> None:
    monkeypatch.setenv("ALGORITHM_SERVICE_TOKEN", "test-secret")
    payload = _payload()
    payload["items"][0]["actualGroupSize"] = 2
    response = client.post(
        "/v1/predict",
        headers={"Authorization": "Bearer test-secret"},
        json=payload,
    )
    assert response.status_code == 422
