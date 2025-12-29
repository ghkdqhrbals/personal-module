#!/usr/bin/env bash

for p in 9001 9002 9003 9004 9005 9006; do redis-cli -p $p CLUSTER RESET HARD || true; done