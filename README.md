iibench-mongodb
===============

iiBench Benchmark for MongoDB and DocumentDB

Active development at https://github.com/dritter-sap/iibench including support for other data platforms.


Requirements
=====================

* Java 11
* The MongoDB Java driver must exist and be in the CLASSPATH, as in "export CLASSPATH=/home/tcallaghan/java_goodies/mongo-java-driver-3.9.1.jar:.". If you don't already have the MongoDB Java driver, then execute the following two commands:

```bash
wget https://oss.sonatype.org/content/repositories/releases/org/mongodb/mongo-java-driver/3.9.1/mongo-java-driver-3.9.1.jar
export CLASSPATH=$PWD/mongo-java-driver-3.9.1.jar:$CLASSPATH

```

* You can connect a different server or port by editing the run.simple.bash script. 


Running the benchmark
=====================

In the default configuration the benchmark will run for 1 hour, or 100 million inserts, whichever comes first.

```bash
git clone https://github.com/tmcallaghan/iibench-mongodb.git
cd iibench-mongodb

```

*[optionally edit run.simple.bash to modify the benchmark behavior]*

```bash
./run.simple.bash

```
