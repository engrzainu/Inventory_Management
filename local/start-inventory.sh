#!/usr/bin/env bash
# Starts the Inventory Management system on Linux or macOS. No internet is needed.
cd "$(dirname "$0")" || exit 1
JAR=$(ls ../target/inventory-management-*.jar inventory-management-*.jar 2>/dev/null | head -n 1)
[ -z "$JAR" ] && { echo "Program file not found. Run: mvn clean package -DskipTests"; exit 1; }
[ -f inventory.env ] || { cp inventory.env.example inventory.env; echo "Created inventory.env. Edit it, then run this again."; exit 0; }
set -a; . ./inventory.env; set +a
echo "Open http://localhost:8080"
exec java -jar "$JAR"
