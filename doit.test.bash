#!/bin/bash

if [ -z "$MONGO_DIR" ]; then
    echo "Need to set MONGO_DIR"
    exit 1
fi
if [ ! -d "$MONGO_DIR" ]; then
    echo "Need to create directory MONGO_DIR"
    exit 1
fi

export BENCHMARK_SUFFIX=""
export TARBALL=tokumx-1.0.0-rc.3-linux-x86_64
export MONGO_TYPE=tokumx
#export MONGO_TYPE=mongo
export MONGO_COMPRESSION=zlib
export MONGO_BASEMENT=65536

export MAX_ROWS=1000000000
export RUN_MINUTES=20000
export NUM_DOCUMENTS_PER_INSERT=100
export MAX_INSERTS_PER_SECOND=1000

export RUN_SECONDS=$[RUN_MINUTES*60]

export NUM_INSERTS_PER_FEEDBACK=10000
export NUM_LOADER_THREADS=1
export DB_NAME=iibench
export BENCHMARK_NUMBER=101
export MONGO_REPLICATION=N
export QUERIES_PER_INTERVAL=0
export QUERY_INTERVAL_SECONDS=60
export QUERY_LIMIT=1000
export QUERY_NUM_DOCS_BEGIN=100000

echo "Running test"
./run.test.bash
