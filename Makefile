COMPOSE := docker compose -f infra/docker-compose.yml
TEMPORAL := $(COMPOSE) exec -T temporal-admin-tools temporal
VENV := agents/.venv
PY := $(VENV)/bin/python

.PHONY: help up down clean logs ps smoke test test-java test-python lint venv

help:
	@grep -E '^[a-z-]+:.*?## ' $(MAKEFILE_LIST) | awk 'BEGIN {FS = ":.*?## "}; {printf "  %-12s %s\n", $$1, $$2}'

up: ## Build and start Temporal, Postgres, UI and both workers
	$(COMPOSE) up -d --build

down: ## Stop everything (keeps data)
	$(COMPOSE) down

clean: ## Stop everything and delete volumes
	$(COMPOSE) down -v

logs: ## Tail worker logs
	$(COMPOSE) logs -f orchestrator agents-worker

ps:
	$(COMPOSE) ps

smoke: ## Run HelloWorkflow (Java) -> echo activity (Python) end to end
	@echo "Waiting for Temporal namespace 'default'..."
	@for i in $$(seq 1 60); do \
	  $(TEMPORAL) operator namespace describe --namespace default >/dev/null 2>&1 && break; \
	  sleep 2; \
	  if [ $$i -eq 60 ]; then echo "Temporal not ready"; exit 1; fi; \
	done
	@out=$$($(TEMPORAL) workflow execute \
	    --type HelloWorkflow --task-queue orchestrator-tq \
	    --workflow-id smoke-$$(date +%s) --execution-timeout 90s \
	    --input '"ApplyOps"' -o json); \
	  echo "$$out"; \
	  echo "$$out" | grep -q '"language": *"python"' && echo "SMOKE OK" || { echo "SMOKE FAILED"; exit 1; }

test: test-java test-python ## Run all unit tests

test-java:
	./gradlew build

venv: $(VENV)/.installed
$(VENV)/.installed: agents/pyproject.toml
	python3 -m venv $(VENV)
	$(PY) -m pip install -q --upgrade pip
	$(PY) -m pip install -q -e 'agents[dev]'
	touch $@

test-python: venv
	cd agents && .venv/bin/ruff check . && .venv/bin/ruff format --check . && .venv/bin/pytest -q

lint: venv
	cd agents && .venv/bin/ruff check --fix . && .venv/bin/ruff format .
