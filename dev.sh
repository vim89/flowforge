export COURSIER_CACHE="\$PWD/.cache/coursier"
export MAVEN_OPTS="-Dmaven.repo.local=\$PWD/.cache/m2 \${MAVEN_OPTS:-}"
exec sbt "\$@"
