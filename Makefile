## Local developer commands

.PHONY: test coverage linkcheck doclint security

test:
	sbt -batch test

coverage:
	sbt -batch -DwithSparkIT=false \
	  "set core / coverageMinimumStmtTotal := 80" \
	  "set core / coverageMinimumBranchTotal := 75" \
	  "set core / coverageFailOnMinimum := true" \
	  "set infrastructure / coverageMinimumStmtTotal := 70" \
	  "set infrastructure / coverageMinimumBranchTotal := 65" \
	  "set infrastructure / coverageFailOnMinimum := true" \
	  "set connectors / coverageMinimumStmtTotal := 70" \
	  "set connectors / coverageFailOnMinimum := true" \
	  clean coverage core/test infrastructure/test connectors/test coverageReport coverageAggregate

linkcheck:
	lychee --config .lychee.toml README.md "docs/**/*.md" || true

doclint:
	bash scripts/lint-docs.sh || true

security:
	# Local shells for security tools can be added here
	echo "Run CodeQL locally via codeql CLI or use workflow_dispatch in CI."

