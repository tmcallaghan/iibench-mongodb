#!/bin/bash

# this is the "execution" script for the benchmark
#   it must be called from a higher-level script

if [ -z "$MONGO_DIR" ]; then
    echo "Need to set MONGO_DIR"
    exit 1
fi
if [ -z "$MONGO_TYPE" ]; then
    echo "Need to set MONGO_TYPE"
    exit 1
fi
if [ -z "$MONGO_COMPRESSION" ]; then
    echo "Need to set MONGO_COMPRESSION"
    exit 1
fi
if [ -z "$MONGO_BASEMENT" ]; then
    echo "Need to set MONGO_BASEMENT"
    exit 1
fi
if [ -z "$MAX_ROWS" ]; then
    echo "Need to set MAX_ROWS"
    exit 1
fi
if [ -z "$NUM_DOCUMENTS_PER_INSERT" ]; then
    echo "Need to set NUM_DOCUMENTS_PER_INSERT"
    exit 1
fi
if [ -z "$MAX_INSERTS_PER_SECOND" ]; then
    echo "Need to set MAX_INSERTS_PER_SECOND"
    exit 1
fi
if [ -z "$NUM_LOADER_THREADS" ]; then
    echo "Need to set NUM_LOADER_THREADS"
    exit 1
fi
if [ -z "$DB_NAME" ]; then
    echo "Need to set DB_NAME"
    exit 1
fi
if [ -z "$MONGO_REPLICATION" ]; then
    echo "Need to set MONGO_REPLICATION"
    exit 1
fi
if [ -z "$RUN_SECONDS" ]; then
    echo "Need to set RUN_SECONDS"
    exit 1
fi
if [ -z "$WRITE_CONCERN" ]; then
    echo "Need to set WRITE_CONCERN"
    exit 1
fi

if [ -z "$NUM_INSERTS_PER_FEEDBACK" ]; then
    export NUM_INSERTS_PER_FEEDBACK=100000
fi
if [ -z "$NUM_SECONDS_PER_FEEDBACK" ]; then
    export NUM_SECONDS_PER_FEEDBACK=-1
fi
if [ -z "$COMMIT_SYNC" ]; then
    export COMMIT_SYNC=1
fi
if [ -z "$SCP_FILES" ]; then
    export SCP_FILES=Y
fi
if [ -z "$NUM_COLLECTIONS" ]; then
    export NUM_COLLECTIONS=1
fi
if [ -z "$QUERIES_PER_INTERVAL" ]; then
    export QUERIES_PER_INTERVAL=0
fi
if [ -z "$QUERY_INTERVAL_SECONDS" ]; then
    export QUERY_INTERVAL_SECONDS=60
fi
if [ -z "$QUERY_LIMIT" ]; then
    export QUERY_LIMIT=1000
fi
if [ -z "$QUERY_NUM_DOCS_BEGIN" ]; then
    export QUERY_NUM_DOCS_BEGIN=10000000
fi


IOSTAT_INTERVAL=10
IOSTAT_ROUNDS=$[RUN_SECONDS/IOSTAT_INTERVAL+1]

ant clean default

export MINI_LOG_NAME=${MACHINE_NAME}-mongoiibench-${NUM_COLLECTIONS}-${MAX_ROWS}-${NUM_DOCUMENTS_PER_INSERT}-${MAX_INSERTS_PER_SECOND}-${NUM_LOADER_THREADS}-${MONGO_TYPE}-${QUERIES_PER_INTERVAL}-${QUERY_INTERVAL_SECONDS}
    
if [ ${MONGO_TYPE} == "tokumx" ]; then
    if [ ${COMMIT_SYNC} == "1" ]; then
        LOG_NAME=${MINI_LOG_NAME}-${MONGO_COMPRESSION}-${MONGO_BASEMENT}-SYNC_COMMIT.log
    else
        LOG_NAME=${MINI_LOG_NAME}-${MONGO_COMPRESSION}-${MONGO_BASEMENT}-NOSYNC_COMMIT.log
    fi
else
    LOG_NAME=${MINI_LOG_NAME}.log
fi
    
export BENCHMARK_TSV=${LOG_NAME}.tsv
export MONGO_LOG=${LOG_NAME}.mongolog
LOG_NAME_IOSTAT=${LOG_NAME}.iostat
    
rm -f $LOG_NAME
rm -f $BENCHMARK_TSV

# $MONGO_REPL must be set to something for the server to start in replication mode
#if [ ${MONGO_REPLICATION} == "Y" ]; then
#    export MONGO_REPL="tmcRepl"
#else
    unset MONGO_REPL
#fi

echo "`date` | starting the ${MONGO_TYPE} server at ${MONGO_DIR}" | tee -a $LOG_NAME
if [ ${MONGO_TYPE} == "tokumx" ]; then
    mongo-start-tokumx-fork
else
    mongo-start-pure-numa-fork
fi
    
mongo-is-up
echo "`date` | server is available" | tee -a $LOG_NAME

# make sure replication is started, generally you don't want to do it for the loader
#if [ ${MONGO_REPLICATION} == "Y" ]; then
#    mongo-start-replication
#fi

iostat -dxm $IOSTAT_INTERVAL $IOSTAT_ROUNDS  > $LOG_NAME_IOSTAT &
    
T="$(date +%s)"
ant execute | tee -a $LOG_NAME
echo "" | tee -a $LOG_NAME
T="$(($(date +%s)-T))"
printf "`date` | loader duration = %02d:%02d:%02d:%02d\n" "$((T/86400))" "$((T/3600%24))" "$((T/60%60))" "$((T%60))" | tee -a $LOG_NAME

T="$(date +%s)"
echo "`date` | shutting down the server" | tee -a $LOG_NAME
mongo-stop
mongo-is-down
T="$(($(date +%s)-T))"
printf "`date` | shutdown duration = %02d:%02d:%02d:%02d\n" "$((T/86400))" "$((T/3600%24))" "$((T/60%60))" "$((T%60))" | tee -a $LOG_NAME
    
pkill -f iostat
    
echo "" | tee -a $LOG_NAME
echo "-------------------------------" | tee -a $LOG_NAME
echo "Sizing Information" | tee -a $LOG_NAME
echo "-------------------------------" | tee -a $LOG_NAME
        
SIZE_BYTES=`du -c --block-size=1 ${MONGO_DATA_DIR} | tail -n 1 | cut -f1`
SIZE_APPARENT_BYTES=`du -c --block-size=1 --apparent-size ${MONGO_DATA_DIR} | tail -n 1 | cut -f1`
SIZE_MB=`echo "scale=2; ${SIZE_BYTES}/(1024*1024)" | bc `
SIZE_APPARENT_MB=`echo "scale=2; ${SIZE_APPARENT_BYTES}/(1024*1024)" | bc `
echo "`date` | post-load sizing (SizeMB / ASizeMB) = ${SIZE_MB} / ${SIZE_APPARENT_MB}" | tee -a $LOG_NAME

if [ ${SCP_FILES} == "Y" ]; then
    DATE=`date +"%Y%m%d%H%M%S"`
    tarFileName="${MACHINE_NAME}-${BENCHMARK_NUMBER}-${DATE}-mongoiibench-${BENCH_ID}${BENCHMARK_SUFFIX}.tar.gz"

    tar czvf ${tarFileName} ${MACHINE_NAME}*
    scp ${tarFileName} tcallaghan@192.168.1.242:~

    rm -f ${tarFileName}
    rm -f ${MACHINE_NAME}*
    rm -f ${MONGO_LOG}

    #movecores
fi
