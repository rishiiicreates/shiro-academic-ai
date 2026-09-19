#!/usr/bin/env bash
set -e

# Fallback decompression if not extracted at build time
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

echo "=== Java Environment ==="
java -version

JAVA_OPTS="-Xms24m -Xmx48m -XX:+UseSerialGC -XX:TieredStopAtLevel=1 -XX:CICompilerCount=1 -Djava.security.egd=file:/dev/./urandom -Djava.awt.headless=true"
echo "Starting Spring Boot immediately on :${PORT:-8080} with JAVA_OPTS=${JAVA_OPTS}..."
cd /app && java ${JAVA_OPTS} -jar /app/backend.jar --server.port=${PORT:-8080} --server.address=0.0.0.0 &
SPRING_PID=$!

(
    sleep 2
    echo "Starting Python Sidecar on :8001..."
    cd /app/sidecar && python sidecar_app.py
) &
SIDECAR_PID=$!

cleanup() {
    echo "Shutting down background services..."
    kill -TERM "$SIDECAR_PID" 2>/dev/null || true
    kill -TERM "$SPRING_PID" 2>/dev/null || true
    wait "$SIDECAR_PID" 2>/dev/null || true
    wait "$SPRING_PID" 2>/dev/null || true
}
trap cleanup SIGINT SIGTERM EXIT

# Monitor both processes. If either crashes, exit container.
while kill -0 "$SPRING_PID" 2>/dev/null && kill -0 "$SIDECAR_PID" 2>/dev/null; do
    sleep 2
done

cleanup
