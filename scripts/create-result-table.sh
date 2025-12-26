#!/bin/bash

docker exec -i mephi-hbase hbase shell <<EOF
create 'network_traffic_analysis', {NAME => 'metrics', VERSIONS => 1}
exit
EOF
