package com.flowforge

package object contracts {
  type TypedSource[C] = com.flowforge.core.types.TypedSource[C]
  type TypedSink[R]   = com.flowforge.core.types.TypedSink[R]

  object TypedSource {
    def apply[C](underlying: com.flowforge.core.types.DataSource)(
      implicit sc: com.flowforge.core.contracts.derive.Shape[C],
    ): TypedSource[C] = com.flowforge.core.types.TypedSource[C](underlying)
  }

  object TypedSink {
    def apply[R](underlying: com.flowforge.core.types.DataSink)(
      implicit sr: com.flowforge.core.contracts.derive.Shape[R],
    ): TypedSink[R] = com.flowforge.core.types.TypedSink[R](underlying)
  }
}

