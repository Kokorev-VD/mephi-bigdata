# NATS JetStream Spikes Monitoring

Real-time monitoring scripts for detecting and tracking CPU/Memory spikes from NATS JetStream.

## Scripts Overview

### 1. watch-spikes.sh (Basic Monitoring)

Simple real-time monitoring script for NATS JetStream spike messages.

**Features:**
- Real-time spike detection from subject `metrics.spikes`
- Automatic detection and pretty-printing with `jq` (if available)
- Color-coded output with severity highlighting
- Graceful shutdown with Ctrl+C
- Docker integration via `mephi-nats` container

**Usage:**
```bash
bash scripts/watch-spikes.sh
```

**Output Example:**
```
╔════════════════════════════════════════════════════════╗
║    NATS JetStream Spikes Monitor - Real-time Watch    ║
╚════════════════════════════════════════════════════════╝
Subject: metrics.spikes
Stream:  SPIKES
Container: mephi-nats
✓ JSON formatting: Enabled (jq)
────────────────────────────────────────────────────────
Waiting for spikes... Press Ctrl+C to stop

[2025-12-27 14:35:22] Spike Detected:
  Type: spike
  Metric: CPU
  Value: 87
  Severity: high
  Full data:
    {
      "timestamp": "2025-12-27T14:35:22Z",
      "hostname": "web-server-01",
      "type": "spike",
      "metric": "CPU",
      "value": 87,
      "unit": "%",
      "severity": "high",
      "threshold": 50,
      "spike_duration_ms": 3421,
      "message": "Detected CPU usage spike on web-server-01"
    }
```

### 2. watch-spikes-advanced.sh (Advanced Monitoring with Filtering)

Advanced monitoring script with filtering, statistics, and live counters.

**Features:**
- Filter spikes by severity (critical, high, medium, low)
- Filter by metric type (CPU, MEMORY, DISK)
- Filter by hostname pattern (regex support)
- Live statistics tracking
- Combine multiple filters
- Compact one-line output for high-volume scenarios

**Usage:**
```bash
# Watch only critical spikes
bash scripts/watch-spikes-advanced.sh --severity critical

# Watch CPU spikes on specific host
bash scripts/watch-spikes-advanced.sh --metric CPU --hostname web-server

# Watch all spikes with live statistics
bash scripts/watch-spikes-advanced.sh --live-stats

# Combine filters
bash scripts/watch-spikes-advanced.sh --severity high --metric MEMORY --live-stats
```

**Command-line Options:**
```
-s, --severity LEVEL      Filter by severity (critical|high|medium|low)
-m, --metric TYPE         Filter by metric type (CPU|MEMORY|DISK)
-h, --hostname HOST       Filter by hostname pattern (regex)
-l, --live-stats          Show live statistics
-a, --all                 Show messages from start (default: new only)
--help                    Show help message
```

**Output Example (Advanced):**
```
╔════════════════════════════════════════════════════════╗
║   NATS JetStream Spikes Monitor - Advanced Mode      ║
╚════════════════════════════════════════════════════════╝

Configuration:
  Subject:  metrics.spikes
  Stream:   SPIKES
  Container: mephi-nats

Active Filters:
  Severity: critical
  Metric:   CPU

Status: Monitoring spikes... (Ctrl+C to stop)
────────────────────────────────────────────────────────

[14:35:22] [critical] CPU spike on web-server-01: 95%
[STATS] Total: 1 | CPU: 1 | MEMORY: 0 | DISK: 0

[14:35:45] [critical] CPU spike on web-server-02: 92%
[STATS] Total: 2 | CPU: 2 | MEMORY: 0 | DISK: 0
```

### 3. test-spike-message.sh (Testing & Message Generation)

Generates and publishes test spike messages to NATS for testing the monitoring scripts.

**Features:**
- Generates realistic spike messages with random data
- Publishes to NATS JetStream `metrics.spikes` subject
- Pretty-prints generated message
- Helpful for testing and demonstration

**Usage:**
```bash
bash scripts/test-spike-message.sh
```

**Output Example:**
```
Publishing test spike message to NATS...

Generated message:
{
  "timestamp": "2025-12-27T14:35:22Z",
  "hostname": "host-5",
  "type": "spike",
  "metric": "MEMORY",
  "value": 78,
  "unit": "%",
  "severity": "high",
  "threshold": 50,
  "spike_duration_ms": 3256,
  "message": "Detected MEMORY usage spike on host-5"
}

Publishing to subject: metrics.spikes
Message published successfully!

To watch for spikes in real-time, run:
  bash scripts/watch-spikes.sh
```

## Expected Message Format

Spike messages should follow this JSON schema:

```json
{
  "timestamp": "2025-12-27T14:35:22Z",
  "hostname": "web-server-01",
  "type": "spike",
  "metric": "CPU|MEMORY|DISK",
  "value": 87,
  "unit": "%",
  "severity": "critical|high|medium|low",
  "threshold": 50,
  "spike_duration_ms": 3421,
  "message": "Human-readable description"
}
```

**Field Descriptions:**
- `timestamp`: ISO 8601 timestamp (UTC)
- `hostname`: Name of the host where spike was detected
- `type`: Message type (always "spike")
- `metric`: Type of metric (CPU, MEMORY, DISK)
- `value`: Current value of the metric
- `unit`: Unit of measurement (%)
- `severity`: Severity level
- `threshold`: Alert threshold that was exceeded
- `spike_duration_ms`: Duration of the spike in milliseconds
- `message`: Human-readable description

## Requirements

**System Requirements:**
- Docker (for container execution)
- NATS CLI tools (automatic via `docker exec`)
- bash >= 4.0

**Optional:**
- `jq` - for JSON pretty-printing (gracefully degraded without it)

**Verify Requirements:**
```bash
# Check if docker is available
docker --version

# Check if jq is available (optional)
jq --version

# Check bash version
bash --version
```

## Setup Instructions

### 1. Ensure NATS JetStream is Running

```bash
# Check NATS container status
docker ps | grep mephi-nats

# Start services if needed
docker-compose up -d mephi-nats
```

### 2. Create SPIKES Stream (if needed)

```bash
# Enter NATS container
docker exec -it mephi-nats nats

# Create stream (in NATS CLI)
stream add SPIKES
# Select subject: metrics.spikes
# Accept defaults for retention policy
```

Or via command-line:

```bash
docker exec -it mephi-nats nats stream add SPIKES \
  --subjects "metrics.spikes" \
  --storage file
```

### 3. Run Monitoring Script

```bash
# Simple monitoring
bash scripts/watch-spikes.sh

# Or with filters
bash scripts/watch-spikes-advanced.sh --severity critical --metric CPU
```

### 4. Test with Sample Messages (Optional)

In another terminal:

```bash
# Generate and send test spike message
bash scripts/test-spike-message.sh

# Send multiple test messages
for i in {1..5}; do
  bash scripts/test-spike-message.sh
  sleep 2
done
```

## Color Scheme

- **Green** `[✓]` : Success/Info messages, timestamps
- **Yellow** `[!]` : Warnings, medium severity, metric types
- **Red** `[X]` : Critical severity, high values, errors
- **Blue** `[@]` : Configuration info, medium severity
- **Cyan** `[#]` : Headers and separators
- **Magenta** `[$]` : Statistics

## Troubleshooting

### NATS Container Not Responding

```bash
# Check if container is running
docker ps | grep mephi-nats

# Check container logs
docker logs mephi-nats

# Restart container
docker restart mephi-nats
```

### No Messages Appearing

```bash
# Verify stream exists
docker exec mephi-nats nats stream list

# Check subject has messages
docker exec mephi-nats nats sub metrics.spikes --stream SPIKES --all

# Publish test message
bash scripts/test-spike-message.sh
```

### jq Not Found

The scripts will work without `jq`, but output will be in raw format:

```bash
# Install jq (optional)
# macOS
brew install jq

# Ubuntu/Debian
sudo apt-get install jq

# CentOS/RHEL
sudo yum install jq
```

## Performance Considerations

- **High-Volume Scenarios**: Use basic `watch-spikes.sh` with compact output
- **Filtering**: Advanced filters have minimal performance impact
- **Statistics**: `--live-stats` flag adds ~5% overhead
- **jq Processing**: ~10-20ms per message (can be significant under 100+ msg/sec load)

## Integration Examples

### Monitor in Cron Job

```bash
# Add to crontab
0 * * * * bash /path/to/scripts/watch-spikes.sh > /var/log/spikes.log 2>&1
```

### Save Output to File

```bash
# Capture monitoring to file
bash scripts/watch-spikes.sh > spike-monitor.log 2>&1 &
```

### Alert on Critical Spikes

```bash
# Monitor only critical spikes and send alert
bash scripts/watch-spikes-advanced.sh --severity critical | \
  while read line; do
    echo "$line" | mail -s "SPIKE ALERT" ops@example.com
  done
```

### Metrics Aggregation

```bash
# Run with stats and log periodically
bash scripts/watch-spikes-advanced.sh --live-stats --all > metrics.log
```

## Architecture

```
┌─────────────────┐
│  Metrics Source │
└────────┬────────┘
         │
         v
┌──────────────────────────┐
│   NATS JetStream         │
│   Stream: SPIKES         │
│   Subject: metrics.spikes│
└────────┬─────────────────┘
         │
    ┌────┴─────┬──────────────┐
    v          v              v
[Basic]   [Advanced]      [Testing]
 Watch     Watch with      Generate
         Filtering & Stats  Messages
```

## Contributing

When modifying these scripts:

1. Always validate syntax: `bash -n script.sh`
2. Test with NATS running: `docker-compose up -d`
3. Verify color output: Test with and without terminal
4. Check jq availability: Test both with/without jq installed
5. Follow existing code style and naming conventions

## License

Part of the MEPHI BigData project.

## Support

For issues or questions:
1. Check the Troubleshooting section
2. Review NATS documentation: https://docs.nats.io/
3. Check container logs: `docker logs mephi-nats`
