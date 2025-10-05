# FlowForge Lineage Management
#
# Quick commands to manage the lineage stack

.PHONY: lineage-up lineage-down lineage-logs lineage-status

# Start the lineage stack (Marquez + PostgreSQL + Web UI)
lineage-up:
	@echo "Starting FlowForge lineage stack"
	docker compose -f docker-compose.yml up -d
	@echo "✓ Lineage stack started"
	@echo "  • Marquez Web UI: http://localhost:3000"
	@echo "  • OpenLineage API: http://localhost:5000"
	@echo "  • Admin API: http://localhost:5001"
	@echo ""
	@echo "FlowForge pipelines will automatically emit lineage events to localhost:5000"

# Stop the lineage stack
lineage-down:
	@echo "Stopping FlowForge lineage stack"
	docker compose -f docker-compose.yml down
	@echo "✓ Lineage stack stopped"

# View logs from all services
lineage-logs:
	docker compose -f docker-compose.yml logs -f

# Check status of lineage services
lineage-status:
	@echo "FlowForge Lineage Stack Status:"
	@echo "================================"
	docker compose -f docker-compose.yml ps
	@echo ""
	@echo "Service Health Checks:"
	@echo "• PostgreSQL: $$(curl -s -o /dev/null -w \'%{http_code}\' http://localhost:5432 2>/dev/null || echo \'DOWN\')"
	@echo "• Marquez API: $$(curl -s -o /dev/null -w \'%{http_code}\' http://localhost:5000/api/v1/namespaces 2>/dev/null || echo \'DOWN\')"
	@echo "• Marquez Web: $$(curl -s -o /dev/null -w \'%{http_code}\' http://localhost:3000 2>/dev/null || echo \'DOWN\')"

# Clean up all data (removes PostgreSQL volume)
lineage-clean:
	@echo "Cleaning up lineage stack and data"
	docker compose -f docker-compose.yml down -v
	docker volume prune -f
	@echo "✓ All lineage data cleaned"

# Reset the lineage stack (stop, clean, start)
lineage-reset: lineage-clean lineage-up
	@echo "✓ Lineage stack reset complete"

help:
	@echo "FlowForge Lineage Management Commands:"
	@echo "====================================="
	@echo "  make lineage-up     - Start the lineage stack"
	@echo "  make lineage-down   - Stop the lineage stack"
	@echo "  make lineage-logs   - View service logs"
	@echo "  make lineage-status - Check service health"
	@echo "  make lineage-clean  - Clean up all data"
	@echo "  make lineage-reset  - Reset (clean + restart)"
	@echo ""
	@echo "After starting, run your FlowForge pipelines and view lineage at:"
	@echo "http://localhost:3000"
