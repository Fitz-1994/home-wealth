#!/bin/bash
set -e

cd "$(dirname "$0")"

# 确保 .env 存在
if [ ! -f .env ]; then
  echo "Error: .env file not found. Copy .env.example and fill in values."
  exit 1
fi

echo "==> Pulling latest code..."
git pull

echo "==> Starting MySQL (if not running)..."
docker-compose up -d mysql

echo "==> Waiting for MySQL to be healthy..."
until docker inspect hw-mysql --format='{{.State.Health.Status}}' 2>/dev/null | grep -q healthy; do
  echo "  waiting..."
  sleep 3
done

echo "==> Building and restarting backend..."
docker-compose up -d --build --no-deps backend

echo "==> Building and restarting frontend..."
docker-compose up -d --build --no-deps frontend

echo "==> Done. Current status:"
docker-compose ps
