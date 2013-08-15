#!/bin/bash

export MONGO_COMPRESSION=zlib
export MONGO_BASEMENT=65536
export MAX_ROWS=25000000
export RUN_MINUTES=200000
export NUM_DOCUMENTS_PER_INSERT=1000
export MAX_INSERTS_PER_SECOND=999999
export NUM_INSERTS_PER_FEEDBACK=100000
export NUM_LOADER_THREADS=1
export DB_NAME=iibench
export BENCHMARK_NUMBER=103

export MONGO_REPLICATION=N

# FSYNC_SAFE, NONE, NORMAL, REPLICAS_SAFE, SAFE
export WRITE_CONCERN=SAFE

# set these if you want inserts plus queries
export QUERIES_PER_INTERVAL=1000
export QUERY_INTERVAL_SECONDS=60
export QUERY_LIMIT=1000
export QUERY_NUM_DOCS_BEGIN=0

# need to lockout memory for pure mongo tests
sudo pkill -9 lockmem


# lock out all but 16G
#export TOKUMON_CACHE_SIZE=12884901888
#if [ -z "$LOCK_MEM_SIZE_16" ]; then
#    echo "Need to set LOCK_MEM_SIZE_16"
#    exit 1
#fi
#export BENCHMARK_SUFFIX=".${LOCK_MEM_SIZE_16}-lock"
#sudo ~/bin/lockmem $LOCK_MEM_SIZE_16 &

# lock out all but 8G
export TOKUMON_CACHE_SIZE=6442450944
if [ -z "$LOCK_MEM_SIZE_8" ]; then
    echo "Need to set LOCK_MEM_SIZE_8"
    exit 1
fi
export BENCHMARK_SUFFIX=".${LOCK_MEM_SIZE_8}-lock"
sudo ~/bin/lockmem $LOCK_MEM_SIZE_8 &


# TOKUMX
export TARBALL=tokumx-1.0.2-linux-x86_64
export MONGO_TYPE=tokumx
export BENCH_ID=tokumx-1.0.2-${MONGO_COMPRESSION}-${WRITE_CONCERN}
./doit.bash
mongo-clean


# MONGODB
export TARBALL=mongodb-linux-x86_64-2.2.5
export MONGO_TYPE=mongo
export BENCH_ID=mongo-2.2.5-${WRITE_CONCERN}
./doit.bash
mongo-clean


# unlock memory
sudo pkill -9 lockmem
