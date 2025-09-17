#!/usr/bin/env bash
set -euo pipefail

# Produce FlowForge review context: module map, pattern scan, key docs heads.

ROOT_DIR=$(git rev-parse --show-toplevel)
cd "$ROOT_DIR"

echo "### FlowForge Review Context" 

echo "\n## Modules"
awk '/lazy val / && $3 != "root" {print "- " $3}' build.sbt | sort

echo "\n## Pattern Scan (main sources)"
scan() {
  local name="$1"; shift
  local pat="$1"; shift
  local files
  files=$(git ls-files 'modules/**/src/main/scala/**/*.scala')
  echo "- ${name}:"
  rg -n "$pat" $files | sed -n '1,50p' || true
}

scan "try blocks" "\\btry\\s*\\{"
scan "scala.util.Try" "\\bscala\\.util\\.Try\\b"
scan "unsafeRunSync" "unsafeRunSync"
scan "cats.effect.IO in main" "(^|\n)\s*import\s+cats\\.effect\\.IO|: IO\\["
scan "Thread.sleep" "Thread\\.sleep\\("
scan "Spark collect()" "\\.collect\\(\\)"
scan "DataFrame.show()" "\\.show\\("
scan "asInstanceOf/Any" "\\basInstanceOf\\b|\\bAny\\b"
scan "null usage" "\\bnull\\b"

echo "\n## Module Pattern Summary (main sources)"
printf "%-28s %7s %7s %7s %7s %7s %7s %9s %9s\n" "Module" "asInst" "Any" "Try" "collect" "show" "null" "collectTL" "limitColl"
for m in modules/*; do
  [ -d "$m" ] || continue
  mod=$(basename "$m")
  files=$(git ls-files "$m/**/src/main/scala/**/*.scala" || true)
  [ -n "$files" ] || { printf "%-28s %7d %7d %7d %7d %7d %7d %9d %9d\n" "$mod" 0 0 0 0 0 0 0 0; continue; }
  ai=$(rg -o "\\basInstanceOf\\b" $files | wc -l | tr -d ' ')
  any=$(rg -o "(^|[^A-Za-z])Any([^A-Za-z]|$)" $files | wc -l | tr -d ' ')
  tr=$(rg -o "\\bscala\\.util\\.Try\\b" $files | wc -l | tr -d ' ')
  col=$(rg -o "\\.collect\\(\\)" $files | wc -l | tr -d ' ')
  shw=$(rg -o "\\.show\\(" $files | wc -l | tr -d ' ')
  nul=$(rg -o "\\bnull\\b" $files | wc -l | tr -d ' ')
  ctl=$(rg -o "\\.collect\\(\\)\\.toList" $files | wc -l | tr -d ' ')
  lmc=$(rg -o "limit\\s*\\([^)]*\\)\\s*\\.collect\\(\\)" $files | wc -l | tr -d ' ')
  printf "%-28s %7d %7d %7d %7d %7d %7d %9d %9d\n" "$mod" $ai $any $tr $col $shw $nul $ctl $lmc
done | tee /tmp/ff_mod_summary.txt

echo "\n### Top offenders (asInstanceOf)"
sort -k2,2nr /tmp/ff_mod_summary.txt | sed -n '2,6p' || true

echo "\n### Files with collect().toList in main sources (top 10)"
git ls-files 'modules/**/src/main/scala/**/*.scala' | xargs rg -n "\\.collect\\(\\)\\.toList" | sed -n '1,10p' || true


echo "\n## ADR Index (first lines)"
sed -n '1,80p' docs/adr/INDEX.md || true

echo "\n## ADR-022 (first lines)"
sed -n '1,120p' docs/adr/022-safe-generic-error-handling.md || true

echo "\n## Idioms (first lines)"
sed -n '1,120p' docs/plan/refactor-idiomatic-scala2.md || true
