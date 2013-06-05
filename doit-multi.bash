#!/bin/bash

export MONGO_COMPRESSION=zlib
export MONGO_BASEMENT=65536
export MAX_ROWS=100000000
export RUN_MINUTES=2880
export NUM_DOCUMENTS_PER_INSERT=1000
export NUM_LOADER_THREADS=1
export DB_NAME=sbtest
export BENCHMARK_NUMBER=101

export TOKUMON_CACHE_SIZE=12884901888

# lock out all but 16G
if [ -z "$LOCK_MEM_SIZE_16" ]; then
    echo "Need to set LOCK_MEM_SIZE_16"
    exit 1
fi

export BENCHMARK_SUFFIX=".${LOCK_MEM_SIZE_16}-lock"



# TOKUDS 0.0.2 : can have problems if memory locked out
export TARBALL=mongodb-2.2.0-tokutek-0.0.2-tokudb-54343-linux-x86_64
export MONGO_TYPE=tokumx
export MONGO_REPLICATION=N
export BENCH_ID=tokumx-0.0.2-${MONGO_COMPRESSION}
#./doit.bash
#mongo-clean


# need to lockout memory for pure mongo tests
sudo pkill -9 lockmem
sudo ~/bin/lockmem $LOCK_MEM_SIZE_16 &


# TOKUDS 1.0.0.rc0
export TARBALL=tokumx-1.0.0-rc.0-linux-x86_64
export MONGO_TYPE=tokumx
export MONGO_REPLICATION=N
export BENCH_ID=tokumx-1.0.0.rc0-${MONGO_COMPRESSION}
./doit.bash
mongo-clean

# PURE MONGODB
export TARBALL=mongodb-linux-x86_64-2.2.3
export MONGO_TYPE=mongo
export MONGO_REPLICATION=N
export BENCH_ID=mongo-2.2.3
./doit.bash
mongo-clean


# unlock memory
sudo pkill -9 lockmem
