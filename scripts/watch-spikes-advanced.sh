#!/bin/bash

# Advanced NATS JetStream Spikes Monitoring Script
# Features: filtering by severity, metric type, hostname, and statistics

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

# Filter options (empty = no filter)
SEVERITY_FILTER=""
METRIC_FILTER=""
HOSTNAME_FILTER=""

# Statistics
declare -A spike_count
declare -A severity_count
total_spikes=0

HAS_JQ=false
if command -v jq &> /dev/null; then
    HAS_JQ=true
fi

# Usage information
usage() {
    cat <<EOF
Usage: $0 [OPTIONS]

Advanced NATS JetStream Spikes Monitoring with filtering

Options:
  -s, --severity LEVEL      Filter by severity (critical|high|medium|low)
  -m, --metric TYPE         Filter by metric type (CPU|MEMORY|DISK|ALL)
  -h, --hostname HOST       Filter by hostname pattern
  -l, --live-stats          Show live statistics
  -a, --all                 Show messages from start (default: new only)
  --help                    Show this help message

Examples:
  # Watch only critical spikes
  $0 --severity critical

  # Watch CPU spikes only
  $0 --metric CPU

  # Watch spikes on specific host with live stats
  $0 --hostname web-server --live-stats

  # Combine filters
  $0 --severity high --metric MEMORY

EOF
    exit 0
}

# Parse command line arguments
parse_args() {
    while [[ $# -gt 0 ]]; do
        case $1 in
            -s|--severity)
                SEVERITY_FILTER="${2,,}"
                shift 2
                ;;
            -m|--metric)
                METRIC_FILTER="${2^^}"
                shift 2
                ;;
            -h|--hostname)
                HOSTNAME_FILTER="$2"
                shift 2
                ;;
            -l|--live-stats)
                LIVE_STATS=true
                shift
                ;;
            -a|--all)
                SHOW_ALL=true
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
}

# Check if value passes filter
passes_filter() {
    local json_line="$1"

    if [ "$HAS_JQ" = false ]; then
        return 0
    fi

    # Severity filter
    if [ -n "$SEVERITY_FILTER" ]; then
        local severity=$(echo "$json_line" | jq -r '.severity // "unknown"' 2>/dev/null | tr '[:upper:]' '[:lower:]')
        if [ "$severity" != "$SEVERITY_FILTER" ]; then
            return 1
        fi
    fi

    # Metric type filter
    if [ -n "$METRIC_FILTER" ]; then
        local metric=$(echo "$json_line" | jq -r '.metric // "unknown"' 2>/dev/null | tr '[:upper:]' '[:lower:]')
        if [ "$metric" != "${METRIC_FILTER,,}" ]; then
            return 1
        fi
    fi

    # Hostname filter
    if [ -n "$HOSTNAME_FILTER" ]; then
        local hostname=$(echo "$json_line" | jq -r '.hostname // "unknown"' 2>/dev/null)
        if [[ ! "$hostname" =~ $HOSTNAME_FILTER ]]; then
            return 1
        fi
    fi

    return 0
}

# Print header with active filters
print_header() {
    clear
    echo -e "${CYAN}╔════════════════════════════════════════════════════════╗${NC}"
    echo -e "${CYAN}║${NC}   NATS JetStream Spikes Monitor - Advanced Mode      ${CYAN}║${NC}"
    echo -e "${CYAN}╚════════════════════════════════════════════════════════╝${NC}"
    echo ""
    echo -e "${BLUE}Configuration:${NC}"
    echo -e "  Subject:  $SUBJECT"
    echo -e "  Stream:   $STREAM"
    echo -e "  Container: $NATS_CONTAINER"
    echo ""

    if [ -n "$SEVERITY_FILTER" ] || [ -n "$METRIC_FILTER" ] || [ -n "$HOSTNAME_FILTER" ]; then
        echo -e "${YELLOW}Active Filters:${NC}"
        [ -n "$SEVERITY_FILTER" ] && echo -e "  Severity: ${RED}$SEVERITY_FILTER${NC}"
        [ -n "$METRIC_FILTER" ] && echo -e "  Metric:   ${YELLOW}$METRIC_FILTER${NC}"
        [ -n "$HOSTNAME_FILTER" ] && echo -e "  Hostname: ${GREEN}$HOSTNAME_FILTER${NC}"
        echo ""
    fi

    echo -e "${YELLOW}Status: Monitoring spikes... (Ctrl+C to stop)${NC}"
    echo -e "${CYAN}────────────────────────────────────────────────────────${NC}"
    echo ""
}

# Update and display statistics
update_stats() {
    if [ "$LIVE_STATS" = true ]; then
        local cpu_count=${spike_count["CPU"]:-0}
        local memory_count=${spike_count["MEMORY"]:-0}
        local disk_count=${spike_count["DISK"]:-0}

        echo -e "${MAGENTA}[STATS]${NC} Total: ${RED}$total_spikes${NC} | CPU: $cpu_count | MEMORY: $memory_count | DISK: $disk_count"
    fi
}

# Display spike with highlighting
display_spike() {
    local json_line="$1"
    local timestamp=$(date '+%H:%M:%S')

    if [ "$HAS_JQ" = false ]; then
        echo -e "${GREEN}[$timestamp]${NC} $json_line"
        return
    fi

    local spike_type=$(echo "$json_line" | jq -r '.type // "unknown"' 2>/dev/null)
    local metric=$(echo "$json_line" | jq -r '.metric // "unknown"' 2>/dev/null)
    local value=$(echo "$json_line" | jq -r '.value // "unknown"' 2>/dev/null)
    local severity=$(echo "$json_line" | jq -r '.severity // "unknown"' 2>/dev/null | tr '[:upper:]' '[:lower:]')
    local hostname=$(echo "$json_line" | jq -r '.hostname // "unknown"' 2>/dev/null)

    # Update statistics
    ((total_spikes++))
    spike_count["$metric"]=$((${spike_count["$metric"]:-0} + 1))
    severity_count["$severity"]=$((${severity_count["$severity"]:-0} + 1))

    # Color severity
    local severity_color="$BLUE"
    case "$severity" in
        "critical") severity_color="$RED" ;;
        "high") severity_color="$YELLOW" ;;
        "medium") severity_color="$BLUE" ;;
    esac

    echo -e "${GREEN}[$timestamp]${NC} ${severity_color}[$severity]${NC} ${YELLOW}$metric${NC} spike on ${GREEN}$hostname${NC}: ${RED}${value}%${NC}"

    # Update stats line
    update_stats
}

# Cleanup handler
cleanup() {
    echo ""
    echo -e "${YELLOW}Stopping monitoring...${NC}"
    if [ "$LIVE_STATS" = true ]; then
        echo ""
        echo -e "${MAGENTA}Final Statistics:${NC}"
        echo -e "  Total spikes: ${RED}$total_spikes${NC}"
        echo -e "  By type: CPU=${spike_count["CPU"]:-0} MEMORY=${spike_count["MEMORY"]:-0} DISK=${spike_count["DISK"]:-0}"
    fi
    exit 0
}

trap cleanup SIGINT SIGTERM

# Main execution
parse_args "$@"
print_header

# Determine subscription strategy
if [ "$SHOW_ALL" = true ]; then
    NATS_OPTS="--all"
else
    NATS_OPTS="--new"
fi

docker exec -it "$NATS_CONTAINER" nats sub "$SUBJECT" \
    --stream "$STREAM" \
    $NATS_OPTS 2>/dev/null | while read -r line; do

    # Skip empty and protocol lines
    if [ -z "$line" ] || [[ ! "$line" =~ ^\{ ]]; then
        continue
    fi

    # Check filters
    if passes_filter "$line"; then
        display_spike "$line"
    fi
done

cleanup
