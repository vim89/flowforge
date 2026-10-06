#!/usr/bin/env bash
set -euo pipefail

# Bans `Any` and `asInstanceOf` from production sources.
#
# `Any` in a signature says a value can be anything, which is the one thing every other type in this
# codebase refuses to say: the caller learns nothing and the callee has to guess. `asInstanceOf` is the same
# claim made at a single expression, with a `ClassCastException` instead of a compile error when it is wrong.
#
# This is a script rather than a scalafix rule because `DisableSyntax` has no per-file excludes, and the
# allowlist is the whole reason the check is usable: a few files genuinely sit at a boundary where the JVM
# hands back an `Object`.

# Files allowed to use either construct, each with the reason it has no narrower option.
#
#   DeequAdapter          reflection into Deequ, loaded by name to keep it an optional dependency.
#                         `Method.invoke` returns `Object`.
#   SparkDataAlgebra      reads a reflectively-obtained quality result back as the typed one. Same boundary.
#   EffectInstances       `ZIO.async[Any, Throwable, A]`: ZIO's first parameter is the environment, and `Any`
#                         there means the effect requires nothing. The opposite of an escape hatch.
#   MemoryProfiler        `Any` appears inside a string template the profiler generates sources from, and in
#                         a synthetic data generator. Neither is a signature anyone calls.
#   OpenLineageEmitter    `url.openConnection().asInstanceOf[HttpURLConnection]`: the JDK returns the
#                         supertype and expects the caller to narrow it.
allowlist=(
  "modules/quality-deequ/src/main/scala/com/flowforge/quality/deequ/DeequAdapter.scala"
  "modules/engines-spark/src/main/scala/com/flowforge/engines/spark/SparkDataAlgebra.scala"
  "modules/core/src/main/scala/com/flowforge/core/instances/EffectInstances.scala"
  "modules/performance-benchmarks/src/main/scala/com/flowforge/performance/MemoryProfiler.scala"
  "modules/core/src/main/scala/com/flowforge/core/lineage/OpenLineageEmitter.scala"
)

# `examples` is demo code, excluded from the production guards the same way the println check excludes it.
find_args=(modules -path '*/src/main/*' -name '*.scala' -not -path '*/examples/*')
for file in "${allowlist[@]}"; do
  find_args+=(-not -path "*${file#modules/}")
done

sources=()
while IFS= read -r -d '' source; do
  sources+=("$source")
done < <(find "${find_args[@]}" -print0)

# Hits on `pattern`, with comment lines dropped: the types removed from these APIs are named in the scaladoc
# that explains why they were removed.
hits() {
  grep -nE "$1" "${sources[@]}" | grep -vE ':[0-9]+: *(\*|//|/\*)' || true
}

failed=0

report() {
  if [ -n "$2" ]; then
    echo "$1" >&2
    printf '%s\n' "$2" >&2
    failed=1
  fi
}

report "\`Any\` in production sources. Give the value a type, or allowlist the file in $0 with a reason:" \
  "$(hits '\bAny\b')"
report "\`asInstanceOf\` in production sources. Make the types line up, or allowlist the file in $0 with a reason:" \
  "$(hits 'asInstanceOf')"

if [ "$failed" -ne 0 ]; then
  exit 1
fi

echo "No Any or asInstanceOf in production sources outside the allowlist."
