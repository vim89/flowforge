## Local developer commands

.PHONY: test coverage linkcheck doclint security

test:
	sbt -batch test

coverage:
	sbt -batch -DwithSparkIT=false \
	  "set core / coverageMinimumStmtTotal := 90" \
	  "set core / coverageMinimumBranchTotal := 85" \
	  "set core / coverageFailOnMinimum := true" \
	  "set infrastructure / coverageMinimumStmtTotal := 80" \
	  "set infrastructure / coverageMinimumBranchTotal := 75" \
	  "set infrastructure / coverageFailOnMinimum := true" \
	  "set connectors / coverageMinimumStmtTotal := 80" \
	  "set connectors / coverageFailOnMinimum := true" \
	  clean coverage core/test infrastructure/test connectors/test coverageReport coverageAggregate

linkcheck:
	lychee --config .lychee.toml README.md "docs/**/*.md" || true

doclint:
	bash scripts/lint-docs.sh || true

security:
	# Local shells for security tools can be added here
	echo "Run CodeQL locally via codeql CLI or use workflow_dispatch in CI."

