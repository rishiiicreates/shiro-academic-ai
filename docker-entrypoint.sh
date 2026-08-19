    #!/usr/bin/env bash
    set -e
    echo "=== Starting Python Sidecar on :8001 ==="
    python /app/sidecar/sidecar_app.py &
    for i in {1..30}; do curl -s http://127.0.0.1:8001/health > /dev/null && echo "Sidecar ready" && break; sleep 1; done
    echo "=== Starting Spring Boot on :${PORT:-8080} ==="
    exec java -Dserver.port=${PORT:-8080} -jar /app/backend.jar
    ENTRY
    chmod +x docker-entrypoint.sh
    echo "✅ docker-entrypoint.sh created"
RENDER
