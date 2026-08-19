#!/usr/bin/env bash
set -e

if [ -f /app/data/the_helper_rag.db.gz ] && [ ! -f /app/data/the_helper_rag.db ]; then
    echo "Decompressing the_helper_rag.db..."
    gunzip -k -f /app/data/the_helper_rag.db.gz || true
fi

if [ -f /app/data/chroma_db.tar.gz.part_aa ] && [ ! -d /app/data/chroma_db ]; then
    echo "Reassembling and unpacking ChromaDB..."
    cat /app/data/chroma_db.tar.gz.part_* > /tmp/chroma_db.tar.gz
    tar -xzf /tmp/chroma_db.tar.gz -C /app/data/
    rm -f /tmp/chroma_db.tar.gz || true
fi

mkdir -p /app/data/images

echo "Starting Python Sidecar on :8001"
cd /app/sidecar && python sidecar_app.py &

for i in $(seq 1 30); do
    if curl -s http://127.0.0.1:8001/health > /dev/null 2>&1; then
        echo "Python Sidecar is ready!"
        break
    fi
    sleep 1
done

echo "Starting Spring Boot on :${PORT:-8080}"
cd /app && exec java -Dserver.port=${PORT:-8080} -jar /app/backend.jar
