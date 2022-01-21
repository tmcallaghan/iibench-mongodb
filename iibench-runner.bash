#! /bin/bash

instanceClasses="db.r6g.large db.r6g.xlarge db.r6g.2xlarge db.r6g.4xlarge db.r6g.8xlarge db.r6g.12xlarge db.r6g.16xlarge db.r5.large db.r5.xlarge db.r5.2xlarge db.r5.4xlarge db.r5.8xlarge db.r5.12xlarge db.r5.16xlarge db.r5.24xlarge"
#instanceClasses="db.r6g.large db.r6g.8xlarge db.r5.2xlarge"

DBINSTANCE='ddb4-max-insert-speed2'
sleepSeconds=5

for thisInstanceClass in $instanceClasses; do
    echo "Modifying to instance type ${thisInstanceClass}"
    aws docdb modify-db-instance --db-instance-identifier $DBINSTANCE --db-instance-class $thisInstanceClass --apply-immediately

    T="$(date +%s)"

    instanceStatus='unknown'
    instancePendingModifiedValues=1

    while true ; do
        instanceInfo=`aws docdb describe-db-instances --db-instance-identifier $DBINSTANCE`

        instanceStatus=`echo $instanceInfo | jq -r '.DBInstances[0].DBInstanceStatus'`
        instanceClass=`echo $instanceInfo | jq -r '.DBInstances[0].DBInstanceClass'`
        instanceAvailabilityZone=`echo $instanceInfo | jq -r '.DBInstances[0].AvailabilityZone'`
        instancePendingModifiedValues=`echo $instanceInfo | jq '.DBInstances[0].PendingModifiedValues | length'`

        T2="$(($(date +%s)-T))"

	thisDuration=`printf "%02d:%02d:%02d:%02d" "$((T2/86400))" "$((T2/3600%24))" "$((T2/60%60))" "$((T2%60))"`

	echo "${thisDuration} | status = ${instanceStatus}, instance = ${instanceClass}, az = ${instanceAvailabilityZone}, pending modifications = ${instancePendingModifiedValues}"

        if [[ "$instanceStatus" == "available" ]] && [[ $instancePendingModifiedValues -eq "0" ]] ; then
            break
        fi

        sleep $sleepSeconds
    done

    # instance type now set, execute the benchmark
    export LOG_NAME_EXTRA="-${thisInstanceClass}"

    ./run.simple.bash
done
