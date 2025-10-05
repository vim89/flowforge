# FlowForge Pipeline Makefile
# Quick commands for development and deployment

.PHONY: help build test run clean fmt lint deploy

# Default target
help:
	@echo "flowforge pipeline"
	@echo "================================="
	@echo "Available commands:"
	@echo ""
	@echo "Development:"
	@echo "  make build      - Compile the pipeline"
	@echo "  make test       - Run all tests"
	@echo "  make run        - Execute the pipeline locally"
	@echo "  make clean      - Clean build artifacts"
	@echo ""
	@echo "Code Quality:"
	@echo "  make fmt        - Format code with scalafmt"
	@echo "  make lint       - Run scalafix linting"
	@echo "  make check      - Run full quality check (fmt + lint + test)"

# Development commands
build:
	@echo "🔨 Building FlowForge pipeline..."
	sbt compile

test:
	@echo "🧪 Running tests..."
	sbt test

run:
	@echo "🚀 Running pipeline..."
	sbt run

clean:
	@echo "🧹 Cleaning build artifacts..."
	sbt clean

# Code quality
fmt:
	@echo "💅 Formatting code..."
	sbt scalafmt

lint:
	@echo "🔍 Running linter..."
	sbt "scalafix --check"

check: fmt lint test
	@echo "✅ Full quality check completed"

# Contract validation demo
demo-contract-drift:
	@echo "🎯 Demonstrating contract drift prevention..."
	@echo "1. Current state should compile:"
	@make build
	@echo ""
	@echo "2. Now edit Contract.scala and change 'id: Long' to 'id: String'"
	@echo "3. Run 'make build' again to see compile failure"
	@echo "4. Revert the change to fix compilation"
	@echo ""
	@echo "This proves flowforge's core USP: Pipelines won't build if contracts drift!"
