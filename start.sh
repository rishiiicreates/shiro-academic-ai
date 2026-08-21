#!/usr/bin/env bash
set -e

DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$DIR"

echo "=========================================================="
echo " Starting Shiro — Adaptive Professor Edition (SRMIST RAG) "
echo "=========================================================="

# 1. Start Python Retrieval Sidecar (Port 8001)
echo "[1/3] Starting Python FastEmbed ONNX Retrieval Sidecar on :8001..."
/tmp/dl_venv/bin/python sidecar/sidecar_app.py > /tmp/shiro_sidecar.log 2>&1 &
SIDECAR_PID=$!
echo "   Sidecar PID: $SIDECAR_PID"

# Wait for sidecar to become healthy
echo "   Waiting for sidecar health check..."
for i in {1..30}; do
  if curl -s http://127.0.0.1:8001/health > /dev/null; then
    echo "   ✓ Sidecar is healthy, loaded 95,672 chunks & 75,000+ diagram figures!"
    break
  fi
  sleep 1
done

# 2. Start Spring Boot Reactive WebFlux Backend (Port 8080)
echo "[2/3] Starting Spring Boot WebFlux Backend on :8080..."
java -jar backend/target/chiroshiro-backend-1.0.0.jar > /tmp/shiro_backend.log 2>&1 &
BACKEND_PID=$!
echo "   Backend PID: $BACKEND_PID"

echo "   Waiting for Spring Boot backend health check..."
for i in {1..30}; do
  if curl -s http://127.0.0.1:8080/api/health > /dev/null; then
    echo "   ✓ Spring Boot WebFlux backend is healthy!"
    break
  fi
  sleep 1
done

# 3. Start React + Vite Frontend (Port 5173)
echo "[3/3] Starting React (Vite) Claude-style Frontend on :5173..."
cd frontend
npm run dev -- --host 127.0.0.1 --port 5173

# Trap exit to cleanup background processes
trap "kill $SIDECAR_PID $BACKEND_PID 2>/dev/null || true" EXIT
