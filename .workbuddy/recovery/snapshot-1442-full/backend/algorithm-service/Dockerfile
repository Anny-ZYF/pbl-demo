FROM python:3.11-slim

ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    ALGORITHM_MODEL_DIR=/app/artifacts

WORKDIR /app

COPY requirements.txt requirements-runtime.txt ./
RUN python -m pip install --no-cache-dir -r requirements-runtime.txt \
    && adduser --system --uid 10001 --group algorithm

COPY --chown=algorithm:algorithm app ./app
COPY --chown=algorithm:algorithm artifacts ./artifacts

USER algorithm
EXPOSE 8000

HEALTHCHECK --interval=30s --timeout=3s --start-period=10s --retries=3 \
    CMD ["python", "-c", "import urllib.request; urllib.request.urlopen('http://127.0.0.1:8000/health', timeout=2).read()"]

CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8000", "--no-access-log"]
