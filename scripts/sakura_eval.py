#!/usr/bin/env python3
"""
Quick-and-dirty evaluator for the sakura heuristic thresholds.

Usage:
    python backend/scripts/sakura_eval.py \
        --proof-root backend/delivery/proof \
        --thresholds backend/src/main/resources/scoring/thresholds.yml

The script scans the labelled NDJSON datasets (sakura / probably_sakura /
probably_not_sakura / not_sakura), computes the same feature vector that the
ScoreService uses, applies the YAML thresholds and prints a confusion matrix
along with headline metrics. The goal is to manually confirm that the chosen
thresholds achieve >60% accuracy on the 400-sample teacher set.
"""

from __future__ import annotations

import argparse
import collections
import datetime as dt
import json
import pathlib
import re
from typing import Dict, Iterable, List, Tuple

try:
    import yaml  # type: ignore
except Exception:  # pragma: no cover
    yaml = None


LABEL_MAP = {
    "sakura": "SAKURA",
    "probably_sakura": "LIKELY",
    "probably_not_sakura": "UNLIKELY",
    "not_sakura": "GENUINE",
}

DATE_RE = re.compile(r"(\d{4})年(\d{1,2})月(\d{1,2})日")
URL_RE = re.compile(r"(?i)https?://|www\.|\.co(m|\.jp)|\.jp")
SYMBOL_RUN_RE = re.compile(r"([!！?？.,。、〜～ー\-])\1{2,}")


def load_thresholds(path: pathlib.Path) -> Dict[str, Dict[str, Dict[str, float]]]:
    defaults = {
        "sakura_percent": {
            "sakura": {"dist_bias": 80.0, "duplicates": 50.0},
            "likely": {"dist_bias": 65.0, "duplicates": 40.0},
            "unlikely": {"dist_bias": 45.0, "duplicates": 0.0},
        }
    }
    if not path.is_file() or yaml is None:
        return defaults
    with path.open(encoding="utf-8") as fh:
        data = yaml.safe_load(fh) or {}
    sakura_percent = data.get("sakura_percent") or defaults["sakura_percent"]
    return {"sakura_percent": sakura_percent}


def parse_reviews(file_path: pathlib.Path) -> List[Dict]:
    reviews: List[Dict] = []
    with file_path.open(encoding="utf-8") as fh:
        for line in fh:
            line = line.strip()
            if not line:
                continue
            record = json.loads(line)
            if record.get("type") == "histogram":
                continue
            reviews.append(record)
    return reviews


def contains_emoji(text: str) -> bool:
    return any(
        (ord(ch) >= 0x1F000)
        or (0x2600 <= ord(ch) <= 0x27BF)
        for ch in text
    )


def compute_features(reviews: List[Dict]) -> Dict[str, float]:
    total = len(reviews)
    if total == 0:
        return {"dist_bias": 0.0, "duplicates": 0.0, "surge": 0.0, "noise": 0.0}

    five_star = sum(1 for r in reviews if float(r.get("rating", 0)) >= 5.0)
    dist_bias = (five_star / total) * 100.0

    bodies = [(r.get("body") or "").strip() for r in reviews]
    clusters = collections.Counter(b for b in bodies if b)
    max_cluster = max(clusters.values(), default=0)
    duplicates = (max_cluster / total) * 100.0 if total else 0.0

    per_day: Dict[dt.date, int] = collections.Counter()
    for r in reviews:
        match = DATE_RE.search(r.get("dateText") or "")
        if not match:
            continue
        year, month, day = map(int, match.groups())
        per_day[dt.date(year, month, day)] += 1
    max_day = max(per_day.values(), default=0)
    bucket_count = len(per_day)
    avg_per_day = (total / bucket_count) if bucket_count else 0.0
    surge = min(100.0, (max_day / avg_per_day) * 100.0) if avg_per_day else 0.0

    url_hits = sum(1 for b in bodies if URL_RE.search(b))
    emoji_hits = sum(1 for b in bodies if contains_emoji(b))
    symbol_hits = sum(1 for b in bodies if SYMBOL_RUN_RE.search(b))
    short_hits = sum(1 for b in bodies if len(b) < 60)
    noise = (
        url_hits / total * 0.4
        + emoji_hits / total * 0.2
        + symbol_hits / total * 0.2
        + short_hits / total * 0.2
    ) * 100.0

    return {
        "dist_bias": dist_bias,
        "duplicates": duplicates,
        "surge": surge,
        "noise": min(100.0, noise),
    }


def judge(features: Dict[str, float], thresholds: Dict[str, Dict[str, Dict[str, float]]]) -> str:
    sakura = thresholds["sakura_percent"]
    dist = features["dist_bias"]
    dup = features["duplicates"]
    if dist >= sakura["sakura"]["dist_bias"] and dup >= sakura["sakura"]["duplicates"]:
        return "SAKURA"
    if dist >= sakura["likely"]["dist_bias"] or dup >= sakura["likely"]["duplicates"]:
        return "LIKELY"
    if dist >= sakura["unlikely"]["dist_bias"]:
        return "UNLIKELY"
    return "GENUINE"


def iter_labelled_files(root: pathlib.Path) -> Iterable[Tuple[str, pathlib.Path]]:
    for folder_name, label in LABEL_MAP.items():
        for ndjson_path in sorted((root / folder_name).rglob("*.ndjson")):
            yield label, ndjson_path


def main() -> None:
    parser = argparse.ArgumentParser(description="Evaluate sakura thresholds on the proof dataset.")
    parser.add_argument("--proof-root", type=pathlib.Path, default=pathlib.Path("backend/delivery/proof"),
                        help="Root directory that contains sakura/probably_sakura/... folders.")
    parser.add_argument("--thresholds", type=pathlib.Path,
                        default=pathlib.Path("backend/src/main/resources/scoring/thresholds.yml"),
                        help="Path to scoring/thresholds.yml")
    args = parser.parse_args()

    thresholds = load_thresholds(args.thresholds)
    confusion = collections.Counter()
    totals = collections.Counter()
    correct = 0
    total = 0

    for label, ndjson_path in iter_labelled_files(args.proof_root):
        reviews = parse_reviews(ndjson_path)
        features = compute_features(reviews)
        prediction = judge(features, thresholds)
        confusion[(label, prediction)] += 1
        totals[label] += 1
        total += 1
        if prediction == label:
            correct += 1

    if total == 0:
        print("No labelled samples found under", args.proof_root)
        return

    labels = ["SAKURA", "LIKELY", "UNLIKELY", "GENUINE"]
    print("Confusion Matrix (rows = actual, columns = predicted)")
    header = "{:>12}".format("") + "".join(f"{col:>12}" for col in labels)
    print(header)
    for actual in labels:
        row = f"{actual:>12}"
        for predicted in labels:
            row += f"{confusion[(actual, predicted)]:>12}"
        print(row)
    accuracy = correct / total
    print()
    print(f"Accuracy: {accuracy:.2%}  ({correct}/{total})")
    for label in labels:
        if totals[label]:
            hit = confusion[(label, label)]
            print(f"{label:>12}: {hit}/{totals[label]} matched")


if __name__ == "__main__":
    main()
