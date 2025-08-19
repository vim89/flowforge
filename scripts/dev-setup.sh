#!/bin/bash
# Development environment setup

set -euo pipefail

echo "🔧 Setting up FlowForge development environment..."

# Install dependencies
echo "📦 Installing dependencies..."
sbt compile

# Setup git hooks
echo "🪝 Setting up git hooks..."
mkdir -p .git/hooks

cat > .git/hooks/pre-commit << "EOF"
#!/bin/bash
sbt scalafmtCheckAll scalafixAll --check
EOF

chmod +x .git/hooks/pre-commit

# Setup IDE configurations
echo "💻 Setting up IDE configurations..."
sbt bloopInstall

echo "✅ Development environment setup complete!"
echo "💡 Run 'sbt compile' to verify everything works"
