#! /bin/bash

for resultFile in mongoiibench*.txt; do
    instanceType=`echo $resultFile | cut -f 10 -d - | cut -f 1-3 -d .`
    perfInfo=`cat $resultFile | grep 'inserts : ' | tail -n3 | head -n1`
    cumIps=`echo $perfInfo | cut -f 3 -d : | cut -f 2 -d =`
    #echo "$instanceType | $cumIps"
    printf "%20s | %15s\n" "$instanceType" "$cumIps"
done

