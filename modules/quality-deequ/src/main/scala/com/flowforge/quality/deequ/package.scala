package com.flowforge.quality

/**
 * =Data Quality (Deequ enhancement)=
 *
 * An optional adapter that augments FlowForge's native Spark quality checks with Amazon Deequ when the
 * `deequ` artifact is available on the classpath. Falls back to native checks automatically if Deequ is not
 * present or fails.
 *
 *   - Entry point: [[com.flowforge.quality.deequ.DeequAdapter]]
 *   - Behavior: reflection detects Deequ and maps FlowForge constraints to Deequ checks
 *   - Version: 2.0.12‑spark‑3.5 (as of 2025‑10‑02)
 */
package object deequ
