# Demo runbook - Optional Quality mode flip

## Goal
Show a default native quality check, then flip a flag to enable an external checker when present.

## Steps
1) Run with defaults → native checks report a score.
2) Add a JVM/system flag (e.g., -Dquality.mode=external) and a dependency on the classpath.
3) Re‑run → external checker engaged; show result/score change.

## Notes
- Emphasize graceful degradation: no dependency, no problem (native path stays active).
- Keep this as an optional time‑box in the talk.

