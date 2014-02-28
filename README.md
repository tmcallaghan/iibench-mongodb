iibench-mongodb
===============

iiBench Benchmark for MongoDB


Running the benchmark
=====================

This example assumes that you already have a MongoDB or TokuMX server running on the same machine as the iiBench client application. You can connect a different server or port by editing the run.simple.bash script.

In it's default configuration it will run for 1 hour.

Note, you need to have the MongoDB Java driver in your CLASSPATH, as in "export CLASSPATH=/home/tcallaghan/java_goodies/mongo-2.11.2.jar:.".

tcallaghan@tmcdsk:~/temp/test$ git clone https://github.com/tmcallaghan/iibench-mongodb.git

tcallaghan@tmcdsk:~/temp/test$ cd iibench-mongodb

[optionally edit run.simple.bash to modify the benchmark behavior]

tcallaghan@tmcdsk:~/temp/test$ ./run.simple.bash
