#!/usr/bin/env bash
set -euo pipefail

IMAGE="sbt/sbt:1.10.0-java17"

docker run --rm -it \
  -v "$PWD":/workspace \
  -v "$HOME/.ivy2":/root/.ivy2 \
  -v "$HOME/.sbt":/root/.sbt \
  -v "$HOME/.cache/coursier":/root/.cache/coursier \
  -w /workspace \
  $IMAGE \
  sbt "$@"

