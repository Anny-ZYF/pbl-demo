from __future__ import annotations

from app.dataset import dataset_sha256, generate_paper_samples


def test_paper_dataset_contains_exactly_1785_samples() -> None:
    samples = generate_paper_samples()
    assert len(samples) == 1785
    assert sum(sample.actual_group_size == 1 for sample in samples) == 21
    for group_size in range(2, 6):
        assert sum(sample.actual_group_size == group_size for sample in samples) == 441


def test_paper_dataset_is_deterministic() -> None:
    first = generate_paper_samples()
    second = generate_paper_samples()
    assert first == second
    assert dataset_sha256(first) == dataset_sha256(second)
    assert dataset_sha256(first) == "7e033aef431573642fa5b5d6cc147794647084aa2c3323056e5a39a30081d146"
