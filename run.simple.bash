#!/bin/bash

# simple script to run against running MongoDB/TokuMX server localhost:(default port)

# if running TokuMX, need to select compression for collection and secondary indexes (zlib is default)
#   valid values : lzma, quicklz, zlib, none
export MONGO_COMPRESSION=zlib

# if running TokuMX, need to select basement node size (65536 is default)
#   valid values : integer > 0 : 65536 for 64K
export MONGO_BASEMENT=65536

# run the benchmark for this many inserts (or the number of minutes defined by RUN_MINUTES)
#   valid values : integer > 0
export MAX_ROWS=100000000

# run the benchmark for this many minutes (or the number of inserts defined by MAX_ROWS)
#   valid values : intever > 0
export RUN_MINUTES=60
export RUN_SECONDS=$[RUN_MINUTES*60]

# total number of documents to insert per "batch"
#   valid values : integer > 0
export NUM_DOCUMENTS_PER_INSERT=1000

# total number of documents to insert per second, allows for the benchmark to be rate limited
#   valid values : integer > 0
export MAX_INSERTS_PER_SECOND=999999

# total number of simultaneous insertion threads
#   valid values : integer > 0
export NUM_LOADER_THREADS=1

# database in which to run the benchmark
#   valid values : character
export DB_NAME=iibench

# write concern for the benchmark client
#   valid values : FSYNC_SAFE, NONE, NORMAL, REPLICAS_SAFE, SAFE
export WRITE_CONCERN=SAFE

# name of the server to connect to
export MONGO_SERVER=localhost

# port of the server to connect to
export MONGO_PORT=27017

# display performance information every time the client application inserts this many documents
#   valid values : integer > 0, set to -1 if using NUM_SECONDS_PER_FEEDBACK
export NUM_INSERTS_PER_FEEDBACK=100000

# display performance information every time the client application has run for this many seconds
#   valid values : integer > 0, set to -1 if using NUM_INSERTS_PER_FEEDBACK
export NUM_SECONDS_PER_FEEDBACK=-1

# number of additional character fields (semi-compressible) to add to each inserted document
#   valid values : integer >= 0
export NUM_CHAR_FIELDS=0

# size (in bytes) of each additional semi-compressible character field
#   valid values : integer >= 0
export LENGTH_CHAR_FIELDS=100

# number of secondary indexes to maintain
#   valid values : integer >= 0 and <= 3
export NUM_SECONDARY_INDEXES=3

# the following 4 parameters allow an insert plus query workload benchmark

# number of queries to perform per QUERY_INTERVAL_SECONDS seconds
#   valid values : integer > 0, set to zero for insert only workload
export QUERIES_PER_INTERVAL=0

# number of seconds during which to perform QUERIES_PER_INTERVAL queries
#   valid values : integer > 0
export QUERY_INTERVAL_SECONDS=15

# number of documents to return per query
#   valid values : integer > 0
export QUERY_LIMIT=10

# wait this many inserts to begin the query workload
#   valid values : integer > 0
export QUERY_NUM_DOCS_BEGIN=1000000


javac -cp $CLASSPATH:$PWD/src src/jmongoiibench.java


export LOG_NAME=mongoiibench-${MAX_ROWS}-${NUM_DOCUMENTS_PER_INSERT}-${MAX_INSERTS_PER_SECOND}-${NUM_LOADER_THREADS}-${QUERIES_PER_INTERVAL}-${QUERY_INTERVAL_SECONDS}
export BENCHMARK_TSV=${LOG_NAME}.tsv
    
rm -f $LOG_NAME
rm -f $BENCHMARK_TSV

T="$(date +%s)"
java -cp $CLASSPATH:$PWD/src jmongoiibench $DB_NAME $NUM_LOADER_THREADS $MAX_ROWS $NUM_DOCUMENTS_PER_INSERT $NUM_INSERTS_PER_FEEDBACK $NUM_SECONDS_PER_FEEDBACK $BENCHMARK_TSV $MONGO_COMPRESSION $MONGO_BASEMENT $RUN_SECONDS $QUERIES_PER_INTERVAL $QUERY_INTERVAL_SECONDS $QUERY_LIMIT $QUERY_NUM_DOCS_BEGIN $MAX_INSERTS_PER_SECOND $WRITE_CONCERN $MONGO_SERVER $MONGO_PORT $NUM_CHAR_FIELDS $LENGTH_CHAR_FIELDS $NUM_SECONDARY_INDEXES | tee -a $LOG_NAME
echo "" | tee -a $LOG_NAME
T="$(($(date +%s)-T))"
printf "`date` | iibench duration = %02d:%02d:%02d:%02d\n" "$((T/86400))" "$((T/3600%24))" "$((T/60%60))" "$((T%60))" | tee -a $LOG_NAME
