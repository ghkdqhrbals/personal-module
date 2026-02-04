#!/bin/bash

# run all docker commands from here

# Usage: ./runner.sh <compose_file1> <compose_file2> ...

if [ $# -eq 0 ]; then
    echo "Usage: $0 <docker-compose-file1> <docker-compose-file2> ..."
    exit 1
fi

for file in "$@"; do
    if [ -f "$file" ]; then
        echo "Starting $file"
        docker-compose -f "$file" up -d
    else
        echo "File not found: $file"
    fi
done
