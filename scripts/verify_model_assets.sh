#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ASSET="$ROOT/app/src/main/assets/pose_landmarker_lite.task"
INFO="$ROOT/app/src/main/java/com/example/postureguard/vision/VisionModels.kt"

if [[ ! -f "$ASSET" ]]; then
  echo "ERROR: missing model asset: $ASSET" >&2
  exit 1
fi

EXPECTED="$(sed -n 's/.*MODEL_SHA256 = "\([0-9a-f]\{64\}\)".*/\1/p' "$INFO")"
if [[ -z "$EXPECTED" ]]; then
  echo "ERROR: could not read MODEL_SHA256 from $INFO" >&2
  exit 1
fi

ACTUAL="$(shasum -a 256 "$ASSET" | awk '{print $1}')"
SIZE="$(wc -c < "$ASSET" | tr -d ' ')"

if [[ "$SIZE" -lt 100000 ]]; then
  echo "ERROR: model asset is suspiciously small ($SIZE bytes)" >&2
  exit 1
fi

if [[ "$ACTUAL" != "$EXPECTED" ]]; then
  echo "ERROR: SHA-256 mismatch" >&2
  echo "  expected: $EXPECTED" >&2
  echo "  actual:   $ACTUAL" >&2
  exit 1
fi

echo "OK: pose_landmarker_lite.task"
echo "  size:   $SIZE bytes"
echo "  sha256: $ACTUAL"
