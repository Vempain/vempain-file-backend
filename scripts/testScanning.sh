#!/bin/bash

SOURCE_DIR=${1}

# Make sure the source directory is provided

if [[ -z "${SOURCE_DIR}" ]]; then
  echo "Usage: $0 <source_directory>"
  exit 1
fi

# Then make sure the source directory exists
if [[ ! -d "${SOURCE_DIR}" ]]; then
  echo "Source directory '${SOURCE_DIR}' does not exist."
  exit 1
fi

set -euo pipefail

# Step 1: Restart the PostgreSQL container
echo "🔁 Restarting PostgreSQL container..."
./docker_db.sh

# Step 2: Start Spring Boot service in a new GNOME terminal window
echo "🚀 Starting Spring Boot service in a new terminal..."

GNOME_TERMINAL_CMD="gnome-terminal -- bash -c './gradlew clean test bootJar bootRun --args=\"--spring.profiles.active=local\" 2>&1 | tee /tmp/out-vempain_file.log; exec bash'"

if ! eval "${GNOME_TERMINAL_CMD}"; then
  echo "❌ Failed to launch service in GNOME terminal. Aborting."
  exit 1
fi

echo "⏳ Waiting 30 seconds for service to initialize..."
sleep 30

# Step 3: Update admin password in PostgreSQL
echo "🔐 Setting admin password in the database..."
psql -h localhost -p 5432 -U vempain_file -d vempain_file <<EOF
UPDATE user_account SET "password" = '\$2y\$12\$grjqvqJtHVivS0mTDeseW.0mZKr8/qFod/KVHAaBZNwPe7mJtFEZm' WHERE id = 1;
EOF

# Step 4: Get JWT token
echo "🔓 Logging in to get JWT..."
TOKEN=$(curl -s -X 'POST' 'http://localhost:8080/api/login' \
  -H 'accept: application/json' \
  -H 'Content-Type: application/json' \
  -d '{
    "login": "admin",
    "password": "qwe"
  }' | jq -r '.token')

if [[ "${TOKEN}" == "null" || -z "${TOKEN}" ]]; then
  echo "❌ Failed to retrieve JWT token"
  exit 1
fi

echo "✅ Token retrieved."

# Step 5: Call scan-files endpoint
echo "📂 Triggering file scan..."
curl -s -X 'POST' 'http://localhost:8080/api/scan-files' \
  -H 'accept: application/json' \
  -H "Authorization: Bearer ${TOKEN}" \
  -H 'Content-Type: application/json' \
  -d "{
    \"directory_name\": \"${SOURCE_DIR}\"
  }" | jq

echo "🎉 All steps completed!"
