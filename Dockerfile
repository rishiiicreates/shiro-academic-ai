    FROM python:3.11-slim

    RUN apt-get update && apt-get install -y --no-install-recommends openjdk-21-jre-headless curl ca-certificates && rm -rf /var/lib/apt/lists/*

    ENV JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
    ENV PATH="$JAVA_HOME/bin:$PATH"

    WORKDIR /app

    COPY sidecar/requirements.txt /app/sidecar/requirements.txt
    RUN pip install --no-cache-dir -r /app/sidecar/requirements.txt

    COPY sidecar/ /app/sidecar/
    COPY backend/target/the-helper-rag-backend-1.0.0.jar /app/backend.jar
    COPY data/ /app/data/

    COPY docker-entrypoint.sh /app/docker-entrypoint.sh
    RUN chmod +x /app/docker-entrypoint.sh

    ENV PORT=8080
    ENV SIDECAR_URL=http://127.0.0.1:8001
    ENV DATA_DIR=/app/data

    EXPOSE 8080
    ENTRYPOINT ["/app/docker-entrypoint.sh"]
    EOF
    echo "✅ Dockerfile fixed"
