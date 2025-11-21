#!/bin/bash

docker exec -i mephi-hbase hbase shell <<EOF
create 'system_metrics', \
  {NAME => 'cpu', VERSIONS => 1}, \
  {NAME => 'ram', VERSIONS => 1}, \
  {NAME => 'disk', VERSIONS => 1}, \
  {NAME => 'network', VERSIONS => 1}, \
  {NAME => 'info', VERSIONS => 1}

exit
EOF
