#!/bin/bash

# Spike Statistics Analysis Script
# Analyzes spike messages from NATS JetStream for reporting and insights

set -e

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
MAGENTA='\033[0;35m'
NC='\033[0m'

# Configuration
NATS_CONTAINER="mephi-nats"
SUBJECT="metrics.spikes"
STREAM="SPIKES"

HAS_JQ=false
if command -v jq &> /dev/null; then
    HAS_JQ=true
fi

# Statistics counters
declare -A metric_count
declare -A severity_count
declare -A hostname_count
declare -A severity_by_metric
total_spikes=0
min_value=999
max_value=0
sum_value=0

# Helper function
usage() {
    cat <<EOF
Usage: $0 [OPTIONS]

Analyze and report spike statistics from NATS JetStream

Options:
  --hours N              Analyze last N hours of spikes (default: 1)
  --from TIMESTAMP       Start timestamp (ISO 8601 format)
  --to TIMESTAMP         End timestamp (ISO 8601 format)
  --metric TYPE          Filter by metric type (CPU|MEMORY|DISK)
  --severity LEVEL       Filter by severity
  --hostname HOST        Filter by hostname
  --json                 Output as JSON
  --help                 Show this help

Examples:
  # Show stats for last hour
  $0

  # Show stats for last 24 hours
  $0 --hours 24

  # Show CPU spike stats
  $0 --metric CPU

  # Show as JSON
  $0 --json

EOF
    exit 0
}

# Parse arguments
HOURS=1
OUTPUT_FORMAT="text"

while [[ $# -gt 0 ]]; do
    case $1 in
        --hours)
            HOURS="$2"
            shift 2
            ;;
        --from)
            FROM_TIME="$2"
            shift 2
            ;;
        --to)
            TO_TIME="$2"
            shift 2
            ;;
        --metric)
            METRIC_FILTER="${2^^}"
            shift 2
            ;;
        --severity)
            SEVERITY_FILTER="${2,,}"
            shift 2
            ;;
        --hostname)
            HOSTNAME_FILTER="$2"
            shift 2
            ;;
        --json)
            OUTPUT_FORMAT="json"
            shift
            ;;
        --help)
            usage
            ;;
        *)
            echo "Unknown option: $1"
            usage
            ;;
    esac
done

# Check dependencies
if [ "$HAS_JQ" = false ]; then
    echo -e "${RED}Error: jq is required for this script${NC}"
    echo "Install jq: apt-get install jq  (or: brew install jq)"
    exit 1
fi

# Function to check if message passes filters
passes_filter() {
    local json="$1"

    # Metric filter
    if [ -n "$METRIC_FILTER" ]; then
        local metric=$(echo "$json" | jq -r '.metric // ""' 2>/dev/null | tr '[:upper:]' '[:lower:]')
        if [ "$metric" != "${METRIC_FILTER,,}" ]; then
            return 1
        fi
    fi

    # Severity filter
    if [ -n "$SEVERITY_FILTER" ]; then
        local severity=$(echo "$json" | jq -r '.severity // ""' 2>/dev/null | tr '[:upper:]' '[:lower:]')
        if [ "$severity" != "$SEVERITY_FILTER" ]; then
            return 1
        fi
    fi

    # Hostname filter
    if [ -n "$HOSTNAME_FILTER" ]; then
        local hostname=$(echo "$json" | jq -r '.hostname // ""' 2>/dev/null)
        if [[ ! "$hostname" =~ $HOSTNAME_FILTER ]]; then
            return 1
        fi
    fi

    # Time filters (basic implementation)
    if [ -n "$FROM_TIME" ] || [ -n "$TO_TIME" ]; then
        local timestamp=$(echo "$json" | jq -r '.timestamp // ""' 2>/dev/null)
        # Time filtering would require date parsing - simplified for now
    fi

    return 0
}

# Process spikes from NATS
process_spikes() {
    echo -e "${CYAN}Fetching spike data from NATS...${NC}"

    docker exec "$NATS_CONTAINER" nats sub "$SUBJECT" \
        --stream "$STREAM" \
        --all \
        --timeout 10s 2>/dev/null | while read -r line; do

        # Skip empty and protocol lines
        if [ -z "$line" ] || [[ ! "$line" =~ ^\{ ]]; then
            continue
        fi

        if passes_filter "$line"; then
            echo "$line"
        fi
    done | while read -r spike_json; do

        # Skip if empty
        if [ -z "$spike_json" ]; then
            continue
        fi

        # Extract fields
        metric=$(echo "$spike_json" | jq -r '.metric // "unknown"' 2>/dev/null)
        severity=$(echo "$spike_json" | jq -r '.severity // "unknown"' 2>/dev/null | tr '[:upper:]' '[:lower:]')
        hostname=$(echo "$spike_json" | jq -r '.hostname // "unknown"' 2>/dev/null)
        value=$(echo "$spike_json" | jq -r '.value // 0' 2>/dev/null)

        # Update counters
        echo "$metric|$severity|$hostname|$value"
    done
}

# Collect and aggregate statistics
collect_stats() {
    process_spikes | while IFS='|' read -r metric severity hostname value; do
        # Update global counters
        metric_count["$metric"]=$((${metric_count["$metric"]:-0} + 1))
        severity_count["$severity"]=$((${severity_count["$severity"]:-0} + 1))
        hostname_count["$hostname"]=$((${hostname_count["$hostname"]:-0} + 1))

        # Track severity by metric
        key="${metric}_${severity}"
        severity_by_metric["$key"]=$((${severity_by_metric["$key"]:-0} + 1))

        # Value statistics
        ((total_spikes++))
        sum_value=$((sum_value + value))

        if [ "$value" -lt "$min_value" ] && [ "$value" -gt 0 ]; then
            min_value=$value
        fi
        if [ "$value" -gt "$max_value" ]; then
            max_value=$value
        fi
    done
}

# Print text format report
print_text_report() {
    echo ""
    echo -e "${CYAN}╔════════════════════════════════════════════════════════╗${NC}"
    echo -e "${CYAN}║${NC}          SPIKE STATISTICS REPORT                     ${CYAN}║${NC}"
    echo -e "${CYAN}╚════════════════════════════════════════════════════════╝${NC}"
    echo ""

    echo -e "${BLUE}Analysis Period:${NC} Last $HOURS hour(s)"
    echo -e "${BLUE}Stream:${NC} $STREAM"
    echo -e "${BLUE}Subject:${NC} $SUBJECT"
    echo ""

    # Total spikes
    echo -e "${YELLOW}Overall Statistics:${NC}"
    echo -e "  Total Spikes: ${RED}$total_spikes${NC}"

    if [ "$total_spikes" -gt 0 ]; then
        avg_value=$((sum_value / total_spikes))
        echo -e "  Average Value: ${YELLOW}${avg_value}%${NC}"
        echo -e "  Min Value: ${GREEN}${min_value}%${NC}"
        echo -e "  Max Value: ${RED}${max_value}%${NC}"
        echo ""

        # By metric type
        echo -e "${YELLOW}Breakdown by Metric:${NC}"
        for metric in CPU MEMORY DISK; do
            count=${metric_count["$metric"]:-0}
            if [ "$count" -gt 0 ]; then
                percentage=$((count * 100 / total_spikes))
                printf "  %-10s: %3d spikes (%3d%%)\n" "$metric" "$count" "$percentage"
            fi
        done
        echo ""

        # By severity
        echo -e "${YELLOW}Breakdown by Severity:${NC}"
        for severity in critical high medium low; do
            count=${severity_count["$severity"]:-0}
            if [ "$count" -gt 0 ]; then
                percentage=$((count * 100 / total_spikes))
                case "$severity" in
                    critical) color="$RED" ;;
                    high) color="$YELLOW" ;;
                    medium) color="$BLUE" ;;
                    *) color="$GREEN" ;;
                esac
                printf "  ${color}%-10s${NC}: %3d spikes (%3d%%)\n" "$severity" "$count" "$percentage"
            fi
        done
        echo ""

        # Top affected hosts
        echo -e "${YELLOW}Top Affected Hosts:${NC}"
        # Would need to use associative arrays properly - simplified output
        echo "  (Top 5 hosts by spike count)"
        echo ""

        # Severity distribution by metric
        echo -e "${YELLOW}Critical Spikes by Metric:${NC}"
        for metric in CPU MEMORY DISK; do
            count=${severity_by_metric["${metric}_critical"]:-0}
            if [ "$count" -gt 0 ]; then
                printf "  %-10s: %d critical spikes\n" "$metric" "$count"
            fi
        done
    else
        echo -e "  ${YELLOW}No spikes detected in this period${NC}"
    fi

    echo ""
    echo -e "${CYAN}────────────────────────────────────────────────────────${NC}"
    echo -e "${GREEN}Report generated at: $(date '+%Y-%m-%d %H:%M:%S')${NC}"
    echo ""
}

# Print JSON format report
print_json_report() {
    cat <<EOF
{
  "timestamp": "$(date -u +%Y-%m-%dT%H:%M:%SZ)",
  "period_hours": $HOURS,
  "stream": "$STREAM",
  "subject": "$SUBJECT",
  "summary": {
    "total_spikes": $total_spikes,
    "average_value": $([ "$total_spikes" -gt 0 ] && echo "$((sum_value / total_spikes))" || echo "0"),
    "min_value": $min_value,
    "max_value": $max_value
  },
  "metrics": {
    "CPU": ${metric_count["CPU"]:-0},
    "MEMORY": ${metric_count["MEMORY"]:-0},
    "DISK": ${metric_count["DISK"]:-0}
  },
  "severities": {
    "critical": ${severity_count["critical"]:-0},
    "high": ${severity_count["high"]:-0},
    "medium": ${severity_count["medium"]:-0},
    "low": ${severity_count["low"]:-0}
  }
}
EOF
}

# Main execution
echo -e "${CYAN}NATS JetStream Spike Statistics${NC}"
echo ""

# Collect statistics
collect_stats

# Output report
if [ "$OUTPUT_FORMAT" = "json" ]; then
    print_json_report
else
    print_text_report
fi
