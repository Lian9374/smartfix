FROM python:3.12-slim
WORKDIR /app
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt && useradd --uid 10001 --create-home worker
COPY worker.py init_db.py ./
USER worker
EXPOSE 9090
HEALTHCHECK --interval=30s --timeout=5s CMD python -c "from urllib.request import urlopen; urlopen('http://127.0.0.1:9090/metrics',timeout=3)" || exit 1
CMD ["python", "-u", "worker.py"]
