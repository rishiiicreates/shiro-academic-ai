# Stage 1: Build Java Backend from source
FROM maven:3.9-eclipse-temurin-17 AS backend-builder
WORKDIR /build
COPY backend/pom.xml .
COPY backend/src ./src
RUN mvn clean package -DskipTests

# Stage 2: Python Sidecar + Java Runtime
FROM python:3.11-slim
RUN apt-get update \
    && apt-get install -y --no-install-recommends \
    default-jre-headless curl ca-certificates \
    && rm -rf /var/lib/apt/lists/*
WORKDIR /app
COPY sidecar/requirements.txt /app/sidecar/requirements.txt
RUN pip install --no-cache-dir -r /app/sidecar/requirements.txt
# Pre-download FastEmbed ONNX model during build so container startup is instant
RUN python -c "from fastembed import TextEmbedding; TextEmbedding(model_name='BAAI/bge-small-en-v1.5', threads=1)"
COPY sidecar/ /app/sidecar/
COPY --from=backend-builder /build/target/shiro-backend-1.0.0.jar /app/backend.jar
COPY data/ /app/data/
# Pre-extract data files during build so runtime container boot is instant
RUN if [ -f /app/data/the_helper_rag.db.gz ]; then \
        gunzip -k -f /app/data/the_helper_rag.db.gz && rm -f /app/data/the_helper_rag.db.gz; \
    fi && \
    if [ -f /app/data/chroma_db.tar.gz.part_aa ]; then \
        cat /app/data/chroma_db.tar.gz.part_* > /tmp/chroma_db.tar.gz && \
        tar -xzf /tmp/chroma_db.tar.gz -C /app/data/ && \
        rm -f /tmp/chroma_db.tar.gz /app/data/chroma_db.tar.gz.part_*; \
    fi && \
    mkdir -p /app/data/images
COPY docker-entrypoint.sh /app/docker-entrypoint.sh
RUN chmod +x /app/docker-entrypoint.sh
ENV PORT=8080
ENV SIDECAR_URL=http://127.0.0.1:8001
ENV DATA_DIR=/app/data
EXPOSE 8080
ENTRYPOINT ["/app/docker-entrypoint.sh"]
