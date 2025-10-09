#!/usr/bin/env bash
set -euo pipefail

pass=true
red="\033[31m"; green="\033[32m"; reset="\033[0m"
err() { echo -e "${red}DOC LINT FAILED:${reset} $1"; pass=false; }
ok()  { echo -e "${green}OK:${reset} $1"; }

# 1) CONTRIBUTING.md must contain core anchors and references
if ! rg -n "^## Session Workflow" CONTRIBUTING.md >/dev/null 2>&1; then err "CONTRIBUTING.md missing 'Session Workflow' section"; else ok "CONTRIBUTING.md has Session Workflow"; fi
if ! rg -n "^## Condensed Pipeline Checklist" CONTRIBUTING.md >/dev/null 2>&1; then err "CONTRIBUTING.md missing 'Condensed Pipeline Checklist'"; else ok "CONTRIBUTING.md has condensed checklist"; fi
if ! rg -n "ADR Index `docs/adr/INDEX.md`" CONTRIBUTING.md >/dev/null 2>&1; then err "CONTRIBUTING.md missing ADR Index reference"; else ok "CONTRIBUTING.md references ADR Index"; fi
if ! rg -n "Developer Handbook `docs/contributing/HANDBOOK.md`" CONTRIBUTING.md >/dev/null 2>&1; then err "CONTRIBUTING.md missing Handbook reference"; else ok "CONTRIBUTING.md references Handbook"; fi

# 2) Handbook must contain key sections
declare -a handbook_sections=(
  "^## 1\. Philosophy, Pitch, Mission"
  "^## 2\. Architecture Overview"
  "^## 2\.1 SOLID Principles Implementation"
  "^## 3\. Coding Patterns \(Tagless Final\)"
  "^### 3\.1 Kleisli Composition \(Example\)"
  "^## 4\. ETL & Pipeline Patterns"
  "^### 4\.1 Effect Boundaries \(Pure vs IO\)"
  "^## 5\. Production Pipeline Concerns \(35\+\)"
  "^## 6\. Technical Implementation Strategy"
  "^## 6\.1 Multi\-Engine Strategy"
  "^### 6\.2 Resource Safety \(Example\)"
  "^## 7\. Advanced Type\-Level Patterns"
  "^## 8\. Resource Management Patterns"
  "^## 9\. Error Modeling Strategy"
  "^### 9\.1 Aggregating Validation \(ValidatedNel\)"
  "^## 10\. Template Generation Philosophy"
  "^## 10\.1 Archetype & Compile\-time Contracts"
  "^## 10\.2 Type\-Safe Archetypes \(Scala Ecosystem\)"
  "^### 10\.3 Typed Contracts & Builder \(Example\)"
  "^## 11\. Prototype Integration & Incremental Adoption"
  "^### 11\.1 Prototype Index \(Repo Paths\)"
  "^## 12\. Refactoring Strategy"
  "^## 13\. Security, Config, and Observability"
  "^## 14\. Testing & QA"
  "^## 15\. Anti\-Patterns to Reject"
  "^## 16\. 30\-Point Checklist \(Pointer\)"
  "^## 17\. Session Workflow \(Developer Tooling\)"
  "^## 18\. Functional Programming Foundation"
  "^## 19\. Low\-Level Design & Design Patterns"
)

for pat in "${handbook_sections[@]}"; do
  if ! rg -n "$pat" docs/contributing/HANDBOOK.md >/dev/null 2>&1; then err "Handbook missing section matching: $pat"; else ok "Handbook has: $pat"; fi
done

# 3) Handbook should cite key ADRs
for adr in 002 011 012 013 014 018 019 020; do
  if ! rg -n "ADR\-${adr}" docs/contributing/HANDBOOK.md >/dev/null 2>&1; then err "Handbook missing ADR-${adr} pointer"; else ok "Handbook references ADR-${adr}"; fi
done

# 4) Coverage crosswalk must reference backup and handbook
if ! rg -n "docs/contributing/HANDBOOK\.md" docs/contributing/contributors/COVERAGE.md >/dev/null 2>&1; then err "Coverage map missing handbook reference"; else ok "Coverage references handbook"; fi

$pass || exit 1
ok "Docs lint passed"
