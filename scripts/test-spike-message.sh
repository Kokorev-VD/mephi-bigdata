#!/bin/bash

# Test Script: Publish a spike message to NATS JetStream
# Used for testing the watch-spikes.sh monitoring script

set -e

NATS_CONTAINER="mephi-nats"
SUBJECT="metrics.spikes"

if ! command -v docker &> /dev/null; then
    echo "Error: docker is not installed"
    exit 1
fi

# Generate a test spike message with realistic data
generate_spike_message() {
    local timestamp=$(date -u +%Y-%m-%dT%H:%M:%SZ)
    local hostname="host-$(shuf -i 1-10 -n 1)"
    local cpu_value=$((RANDOM % 40 + 60))
    local memory_value=$((RANDOM % 30 + 70))

    # Randomly select metric type and severity
    local metric_type=$(shuf -e "CPU" "MEMORY" "DISK" -n 1)
    local severity=$(shuf -e "high" "critical" "medium" -n 1)

    if [ "$metric_type" = "CPU" ]; then
        local value=$cpu_value
    else
        local value=$memory_value
    fi

    cat <<EOF
{
  "timestamp": "$timestamp",
  "hostname": "$hostname",
  "type": "spike",
  "metric": "$metric_type",
  "value": $value,
  "unit": "%",
  "severity": "$severity",
  "threshold": 50,
  "spike_duration_ms": $((RANDOM % 5000 + 1000)),
  "message": "Detected $metric_type usage spike on $hostname"
}
EOF
}

echo "Publishing test spike message to NATS..."
echo ""

SPIKE_MESSAGE=$(generate_spike_message)

echo "Generated message:"
echo "$SPIKE_MESSAGE" | docker run -i stedolan/jq '.' 2>/dev/null || echo "$SPIKE_MESSAGE"
echo ""

echo "Publishing to subject: $SUBJECT"
echo "$SPIKE_MESSAGE" | docker exec -i "$NATS_CONTAINER" nats pub "$SUBJECT" 2>/dev/null || {
    echo "Error: Failed to publish message. Is NATS container running?"
    exit 1
}

echo "Message published successfully!"
echo ""
echo "To watch for spikes in real-time, run:"
echo "  bash scripts/watch-spikes.sh"
