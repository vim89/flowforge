#!/usr/bin/env bash
set -euo pipefail

# Simple doc lint: list public declarations lacking Scaladoc in main sources
# This is advisory (non-fatal); CI can parse the output.

# Focus fail gate on core and contracts for v1.0; others are advisory
ROOTS=(modules/core modules/contracts)
MISS=0
echo "🔎 Doc lint: scanning for missing Scaladoc on public declarations"
for r in "${ROOTS[@]}"; do
  while IFS= read -r -d '' f; do
    lines=()
    while IFS= read -r line; do
      lines+=("$line")
    done < <(nl -ba "$f")
    for ((i=0; i<${#lines[@]}; i++)); do
      L="${lines[$i]}"
      # Match top-level public declarations (very simple heuristic)
      if [[ "$L" =~ [[:space:]]+[0-9]+[[:space:]]+(trait|class|object)[[:space:]]+[A-Z][A-Za-z0-9_]*[[:space:]]*\{? ]]; then
        # Look back a few lines for a /** ... */ opener
        hasdoc=0
        for b in 1 2 3 4 5; do
          j=$((i-b))
          if (( j >= 0 )); then
            if [[ "${lines[$j]}" =~ \/\*\* ]]; then hasdoc=1; break; fi
            if [[ "${lines[$j]}" =~ ^[[:space:]]*$ ]]; then continue; fi
          fi
        done
        if (( hasdoc == 0 )); then
          echo "⚠️  Missing Scaladoc: $f:${L%%$'\t'*}"
          ((MISS++))
        fi
      fi
    done
  done < <(find "$r" -type f -path "*/src/main/*" -name "*.scala" -print0)
done

echo "Doclint: $MISS items missing Scaladoc"
if (( MISS > 0 )); then
  echo "❌ Doclint failed for: ${ROOTS[*]}"
  exit 1
fi
exit 0
