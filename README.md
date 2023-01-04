iibench-mongodb
===============

iiBench Benchmark for MongoDB and DocumentDB


Requirements
=====================

* Java 11
* The MongoDB Java driver jars must exist and be in the CLASSPATH. If you don't already have the MongoDB Java driver, then execute the following two commands:

```bash
wget https://repo.maven.apache.org/maven2/org/mongodb/mongodb-driver-sync/4.8.1/mongodb-driver-sync-4.8.1.jar
wget https://repo.maven.apache.org/maven2/org/mongodb/mongodb-driver-core/4.8.1/mongodb-driver-core-4.8.1.jar
wget https://repo.maven.apache.org/maven2/org/mongodb/bson/4.8.1/bson-4.8.1.jar
export CLASSPATH=$PWD/mongo-java-sync-4.8.1.jar:$PWD/mongo-driver-core-4.8.1.jar:$PWD/mongodb-driver-core-4.8.1.jar$CLASSPATH

```


Running the benchmark
=====================

In the default configuration the benchmark will run for 2 days or 10 million, whichever comes first.

```bash
git clone https://github.com/tmcallaghan/iibench-mongodb.git
cd iibench-mongodb

```

*[optionally edit run.simple.bash to modify the benchmark behavior]*
* Customize MONGO_URI for your specific connection needs

```bash
./run.simple.bash

```
