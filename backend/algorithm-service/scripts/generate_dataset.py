"""Write the deterministic paper-style dataset and its SHA-256 digest."""

from __future__ import annotations

import argparse
import json
from pathlib import Path

from app.dataset import generate_paper_samples, write_dataset


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "--output",
        type=Path,
        default=Path("artifacts/synthetic-samples.csv"),
    )
    args = parser.parse_args()

    samples = generate_paper_samples()
    digest = write_dataset(args.output, samples)
    digest_path = args.output.with_suffix(".sha256.json")
    digest_path.write_text(
        json.dumps(
            {"algorithm": "sha256", "file": args.output.name, "sha256": digest},
            ensure_ascii=False,
            indent=2,
            sort_keys=True,
        )
        + "\n",
        encoding="utf-8",
    )
    print(f"wrote {len(samples)} samples to {args.output}")
    print(f"sha256 {digest}")


if __name__ == "__main__":
    main()

