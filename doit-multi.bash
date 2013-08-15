#!/bin/bash

export MONGO_COMPRESSION=zlib
export MONGO_BASEMENT=65536
export MAX_ROWS=100000000
export RUN_MINUTES=200000
export NUM_DOCUMENTS_PER_INSERT=1000
export MAX_INSERTS_PER_SECOND=999999
export NUM_INSERTS_PER_FEEDBACK=100000
export NUM_LOADER_THREADS=1
export DB_NAME=iibench
export BENCHMARK_NUMBER=101

# FSYNC_SAFE, NONE, NORMAL, REPLICAS_SAFE, SAFE
export WRITE_CONCERN=FSYNC_SAFE

# set these if you want inserts plus queries
export QUERIES_PER_INTERVAL=0
export QUERY_INTERVAL_SECONDS=15
export QUERY_LIMIT=10
export QUERY_NUM_DOCS_BEGIN=1000000

export TOKUMON_CACHE_SIZE=12884901888

# lock out all but 16G
if [ -z "$LOCK_MEM_SIZE_16" ]; then
    echo "Need to set LOCK_MEM_SIZE_16"
    exit 1
fi

export BENCHMARK_SUFFIX=".${LOCK_MEM_SIZE_16}-lock"



# need to lockout memory for pure mongo tests
sudo pkill -9 lockmem
sudo ~/bin/lockmem $LOCK_MEM_SIZE_16 &


# TOKUMX
export TARBALL=tokumx-1.0.2-linux-x86_64
export MONGO_TYPE=tokumx
export MONGO_REPLICATION=N
export WRITE_CONCERN=FSYNC_SAFE
export BENCH_ID=tokumx-1.0.2-${MONGO_COMPRESSION}-${WRITE_CONCERN}
./doit.bash
mongo-clean


# TOKUMX - SAFE
export TARBALL=tokumx-1.0.2-linux-x86_64
export MONGO_TYPE=tokumx
export MONGO_REPLICATION=N
export WRITE_CONCERN=SAFE
export BENCH_ID=tokumx-1.0.2-${MONGO_COMPRESSION}-${WRITE_CONCERN}
./doit.bash
mongo-clean


# MONGODB
export TARBALL=mongodb-linux-x86_64-2.2.5
export MONGO_TYPE=mongo
export MONGO_REPLICATION=N
export WRITE_CONCERN=FSYNC_SAFE
export BENCH_ID=mongo-2.2.5-${WRITE_CONCERN}
./doit.bash
mongo-clean


# MONGODB - SAFE
export TARBALL=mongodb-linux-x86_64-2.2.5
export MONGO_TYPE=mongo
export MONGO_REPLICATION=N
export WRITE_CONCERN=SAFE
export BENCH_ID=mongo-2.2.5-${WRITE_CONCERN}
./doit.bash
mongo-clean


# unlock memory
sudo pkill -9 lockmem
