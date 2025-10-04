# Presenter Cheatsheet (Both Talks)

## Timing (30 min target)
- WHY (3) + Boundaries (2) = 5
- HOW (7–9)
- Demo (7–8)
- WHAT (3–4)
- Takeaways + Q&A (4–6)

## One‑liners (repeat at section changes)
- If it compiles, contracts align.
- Pure inside, effects at the edges.
- Typed pipelines, portable engines, safer ops.

## Boundary clarifiers
- Compile‑time vs Runtime: structures/policies vs DQ, lineage, retries.
- DX vs Process: fast local loop vs CI policy gate + compile‑fail PR.

## Demo prep
- Copy/paste snippets into a scratch file/repl; verify error text in advance.
- Keep screenshots as a fallback (error and success states).
- Time both demos < 4 minutes total.

## Likely Q&A (seed)
- Do types replace tests? No; types are exhaustive for shape/policy, tests for behavior.
- Can we relax policies? Yes (Backward/Forward during rollout), then sunset to Exact.
- What remains at runtime? DQ for anomalies, lineage/metrics, idempotent edges.

## Fail‑safe plan
- Demo fails → show screenshot and narrate change.
- Move to WHAT; don’t stall on tooling.

## Last‑slide product note (20–30s only)
- “We built a framework that embodies these ideas; if you’re curious, start here.”
- Link + quickstart command + invitation to contribute.

