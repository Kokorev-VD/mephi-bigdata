#!/bin/bash

echo "Watching spike events (Ctrl+C to stop)..."
echo "Waiting for CPU or Memory spikes..."
docker run --rm --network mephi-bigdata_mephi-network natsio/nats-box:latest \
  nats sub "metrics.spikes" -s nats://mephi-nats:4222
