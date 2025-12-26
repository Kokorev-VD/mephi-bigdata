#!/bin/bash

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$SCRIPT_DIR"

echo "Building..."
../gradlew shadowJar

echo "Copying JAR to build/libs..."
mkdir -p build/libs
cp build/libs/network-analyzer.jar build/libs/ 2>/dev/null || echo "JAR already in place"

echo "Submitting to Spark..."
docker exec mephi-spark bash -c "/opt/spark/bin/spark-submit --class NetworkTrafficAnalyzer --master local[*] /opt/spark-apps/network-analyzer.jar"

exit
EOF
