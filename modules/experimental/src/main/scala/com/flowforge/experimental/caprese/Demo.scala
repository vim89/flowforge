package com.flowforge.experimental.caprese

/*
import scala.language.experimental.captureChecking

object Demo:
  type PureFn[-A, +B] = A -> B

  // Scope a capability so it cannot escape
  def withCapability[C, A](acquire: => C)(use: C^ => A): A =
    val c = acquire
    use(c)

  final case class Connector(token: String)

  // A pure, non‑capturing transform
  val pureUpper: PureFn[String, String] = s => s.toUpperCase

  def safeUse: String =
    withCapability(Connector("secret")) { c =>
      pureUpper("ok") // c is used within scope; does not escape
    }

  // Uncomment to observe compiler rejection (capability escapes scope)
  // def unsafeEscape: Connector =
  //   withCapability(Connector("secret")) { c =>
  //     c // error: capability C^ escapes its scope
  //   }

 */
