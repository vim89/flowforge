# Conference Organizer Packet – ScalaIO Paris 2025

This packet reflects the single conference talk we are delivering. All content stays brand-agnostic until the closing invitation slide.

## Session Overview
- **Title:** Compile-Time Contracts & Fiber-Safe Data Pipelines
- **Duration:** 45 minutes including Q&A
- **Abstract:** How to move schema drift and effect leaks left of runtime. We show contract-first pipelines, policy-driven migrations, compile-fail tests, refined configuration, fiber-safe execution across Cats Effect/ZIO, and the trait seam that swaps Spark/Flink without touching user code.
- **Key Outcomes for Attendees:**
  1. Recognize how compile-time contracts block schema drift and shorten MTTR.
  2. Apply the migration playbook (Exact → Backward/Forward → Exact) with CI guardrails.
  3. Keep pipelines pure inside and effectful at the edges for safe retries and faster feedback.

## Agenda Snapshot
1. WHY: Friday-night schema failure story; belief statements (3 min)
2. Boundaries: compile-time vs runtime; DX vs process (2 min)
3. HOW: Policy lattice, compile-time evidence pipeline, red→green demo (15 min)
4. HOW: Templates, refined config, ValidatedNel DQ, effect boundary with Kleisli, Cats Effect/ZIO interop, engine seam (18 min)
5. WHAT: Runtime guardrails, outcome takeaways, invitation (7 min)
6. Q&A buffer (5–7 min)

See `docs/talks/ScalaIO-2025-Main-Talk.md` for slide-by-slide script.

## Demo Requirements
- Laptop with JDK 17+, sbt 1.9+
- Local clone of FlowForge (or prebuilt demo repo)
- Prepared code snippets & compiler-error screenshots
- Optional: Wi‑Fi only if we show a CI PR snapshot (offline screenshots available)

## AV Requests
- 1080p+ projector, dark theme
- IDE / terminal font ≥ 24–28 pt
- Confidence monitor with speaker timer preferred

## Speaker Bio (placeholder text)
- **One-liner:** [Your Name] builds type-driven data platforms that make migrations boring and predictable.
- **~100 words:** [Your Name] is a staff-level engineer focused on compile-time safety and effect-aware data systems. They have led schema governance, reliability initiatives, and cross-engine pipeline projects in finance and SaaS, and they speak regularly about moving failures left and keeping developer feedback loops fast.
