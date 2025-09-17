FROM eclipse-temurin:17-jre-alpine

LABEL maintainer="FlowForge Team <team@flowforge.io>"
LABEL version="0.2.0"
LABEL description="FlowForge - Revolutionary Functional Data Engineering Framework"

# Install required packages
RUN apk add --no-cache \
    bash \
    curl \
    dumb-init

# Create flowforge user
RUN addgroup -g 1001 flowforge && \
    adduser -D -s /bin/bash -u 1001 -G flowforge flowforge

# Set working directory
WORKDIR /opt/flowforge

# Copy application files
COPY --chown=flowforge:flowforge target/universal/stage/ ./

# Set permissions
RUN chmod +x bin/flowforge

# Switch to flowforge user
USER flowforge

# Health check
HEALTHCHECK --interval=30s --timeout=3s --start-period=5s --retries=3 \
  CMD curl -f http://localhost:8080/health || exit 1

# Expose port
EXPOSE 8080

# Use dumb-init to handle signals properly
ENTRYPOINT ["/usr/bin/dumb-init", "--"]
CMD ["bin/flowforge"]
