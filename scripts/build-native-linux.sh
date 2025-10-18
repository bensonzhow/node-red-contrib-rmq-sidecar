#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/../sidecar-src"
mvn -q -DskipTests -Pnative native:compile
cd ..
mkdir -p ../sidecar/linux
cp sidecar-src/target/rocketmq-sidecar ../sidecar/linux/rocketmq-sidecar
echo "$(date) linux" > ../sidecar/VERSION.txt
echo "Done: sidecar/linux/rocketmq-sidecar"