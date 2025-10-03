package com.flowforge.compilefail

import cats.effect.IO
import com.flowforge.core.contracts.SchemaPolicy
import com.flowforge.core.types._
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers

/**
 * Compile-fail coverage for the typestate builder and typed endpoints.
 *
 * We assert that:
 *  - build() is unavailable until source, transform, and sink are added (phantom-state gate)
 *  - typed endpoints require SchemaConforms evidence at compile-time
 */
class BuilderTypestateFailSpec extends AnyWordSpec with Matchers {

  final case class User(id: Long, email: String)
  final case class UserV1(id: Long, email: String)
  final case class DriftedUser(id: Long, name: String) // schema drift

  "PipelineBuilder typestate" should {
    "not allow build() without sink" in {
      assertTypeError(
        """
          import cats.effect.IO
          import com.flowforge.core.PipelineBuilder
          import com.flowforge.core.algebra.EffectSystem
          import com.flowforge.core.instances.EffectInstances._
          import com.flowforge.core.types._
          import com.flowforge.core.contracts._


          val src  = TypedSource[Int](LocalDataSource("/tmp/in", DataFormat.Parquet))
          val pipe = PipelineBuilder[IO]("p").addTypedSource[Int, Int, SchemaPolicy.Exact](src, _ => IO.pure(1))
          // Missing transform and sink -> should not compile
          val built = pipe.build
        """.stripMargin,
      )
    }

    "require SchemaConforms evidence for typed source with mismatched contract" in {
      assertTypeError(
        """
          import cats.effect.IO
          import com.flowforge.core.PipelineBuilder
          import com.flowforge.core.algebra.EffectSystem
          import com.flowforge.core.instances.EffectInstances._
          import com.flowforge.core.types._
          import com.flowforge.core.contracts._
          import com.flowforge.core.contracts.SchemaPolicy
          import com.flowforge.core.contracts.derive.Shape


          val src = TypedSource[UserV1](LocalDataSource("/tmp/in", DataFormat.Parquet))
          // Evidence should fail to materialize (DriftedUser vs UserV1 under Exact)
          val pipe = PipelineBuilder[IO]("p")
            .addTypedSource[DriftedUser, UserV1, SchemaPolicy.Exact](src, _ => IO.pure(DriftedUser(1, "n")))
        """.stripMargin,
      )
    }

    "allow build() only after sink under valid evidence" in {
      assertCompiles(
        """
          import cats.effect.IO
          import com.flowforge.core.PipelineBuilder
          import com.flowforge.core.algebra.EffectSystem
          import com.flowforge.core.instances.EffectInstances._
          import com.flowforge.core.types._
          import com.flowforge.core.contracts._
          import com.flowforge.core.contracts.SchemaPolicy
          import com.flowforge.core.contracts.derive.Shape

          val src  = TypedSource[User](LocalDataSource("/tmp/in", DataFormat.Parquet))
          val snk  = TypedSink[User](LocalDataSink("/tmp/out", DataFormat.Parquet))
          val pipe = PipelineBuilder[IO]("p")
            .addTypedSource[User, User, SchemaPolicy.Exact](src, _ => IO.pure(User(1, "a@b")))
            .noTransform
            .addTypedSink[User, SchemaPolicy.Exact](snk, (_, _) => IO.unit)
          val built = pipe.build
        """.stripMargin,
      )
    }
  }
}
