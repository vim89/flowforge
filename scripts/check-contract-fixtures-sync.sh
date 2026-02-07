#!/usr/bin/env bash
set -euo pipefail

root="modules/contracts-inputs"
if [ ! -d "$root" ]; then
  echo "contracts-inputs directory not found: $root" >&2
  exit 1
fi

avro_missing=0
yaml_missing=0

normalize_name() {
  local name="$1"
  # Strip known contract version suffixes, e.g. ".v1.0.0"
  echo "$name" | sed -E 's/\.v[0-9]+\.[0-9]+\.[0-9]+$//'
}

while IFS= read -r -d '' yaml; do
  dir="$(dirname "$yaml")"
  raw="$(basename "$yaml")"
  name="${raw%.yaml}"
  name="${name%.yml}"
  norm="$(normalize_name "$name")"
  parent="$(basename "$dir")"
  avro_dir="${root}/avro/${parent}"

  if [ ! -d "$avro_dir" ] || ! find "$avro_dir" -maxdepth 1 -type f -name "${norm}*.avsc" | grep -q .; then
    echo "Missing AVRO for metadata: $yaml" >&2
    yaml_missing=1
  fi
done < <(find "$root/metadata" -type f \( -name '*.yaml' -o -name '*.yml' \) -print0)

while IFS= read -r -d '' avsc; do
  dir="$(dirname "$avsc")"
  raw="$(basename "$avsc" .avsc)"
  norm="$(normalize_name "$raw")"
  parent="$(basename "$dir")"
  meta_dir="${root}/metadata/${parent}"

  if [ ! -d "$meta_dir" ] || { [ ! -f "${meta_dir}/${norm}.yaml" ] && [ ! -f "${meta_dir}/${norm}.yml" ]; }; then
    echo "Missing metadata YAML for AVRO: $avsc" >&2
    avro_missing=1
  fi
done < <(find "$root/avro" -type f -name '*.avsc' -print0)

if [ "$avro_missing" -ne 0 ] || [ "$yaml_missing" -ne 0 ]; then
  echo "Contract fixture sync check failed." >&2
  exit 1
fi

echo "Contract fixtures are coherent (.avsc <-> .yaml/.yml)."
