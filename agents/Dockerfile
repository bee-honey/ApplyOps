FROM python:3.12-slim
WORKDIR /app
RUN useradd --system --uid 10001 app
COPY pyproject.toml ./
COPY src ./src
RUN pip install --no-cache-dir .
USER app
CMD ["applyops-agents-worker"]
