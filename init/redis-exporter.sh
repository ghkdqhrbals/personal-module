#!/usr/bin/env bash

redis_exporter --redis.addr=redis://127.0.0.1:9001 --web.listen-address=:9121 &
redis_exporter --redis.addr=redis://127.0.0.1:9002 --web.listen-address=:9122 &
redis_exporter --redis.addr=redis://127.0.0.1:9003 --web.listen-address=:9123 &
redis_exporter --redis.addr=redis://127.0.0.1:9004 --web.listen-address=:9124 &
redis_exporter --redis.addr=redis://127.0.0.1:9005 --web.listen-address=:9125 &
redis_exporter --redis.addr=redis://127.0.0.1:9006 --web.listen-address=:9126 &