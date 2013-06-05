//import com.mongodb.Mongo;
import com.mongodb.MongoClient;
import com.mongodb.MongoClientOptions;
import com.mongodb.DB;
import com.mongodb.DBCollection;
import com.mongodb.DBCursor;
import com.mongodb.BasicDBObject;
import com.mongodb.DBObject;
import com.mongodb.DBCursor;
import com.mongodb.ServerAddress;
import com.mongodb.WriteConcern;
import com.mongodb.CommandResult;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.Date;
import java.util.Properties;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.File;
import java.io.Writer;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;

public class jmongoiibench {
    public static AtomicLong globalInserts = new AtomicLong(0);
    public static AtomicLong globalWriterThreads = new AtomicLong(0);
    
    public static Writer writer = null;
    public static boolean outputHeader = true;

    public static String dbName;
    public static int writerThreads;
    public static Integer numMaxInserts;
    public static int documentsPerInsert;
    public static long insertsPerFeedback;
    public static long secondsPerFeedback;
    public static String compressionType;
    public static int basementSize;
    public static String logFileName;
    public static String indexTechnology;
    public static Integer numSeconds;
    
    public static int allDone = 0;
    
    public jmongoiibench() {
    }

    public static void main (String[] args) throws Exception {
        if (args.length != 11) {
            logMe("*** ERROR : CONFIGURATION ISSUE ***");
            logMe("jmongoiibench [database name] [number of writer threads] [documents per collection] [documents per insert] [inserts feedback] [seconds feedback] [log file name] [technology = mongo|tokumx] [compression type] [basement node size (bytes)] [number of seconds to run]");
            System.exit(1);
        }
        
        dbName = args[0];
        writerThreads = Integer.valueOf(args[1]);
        numMaxInserts = Integer.valueOf(args[2]);
        documentsPerInsert = Integer.valueOf(args[3]);
        insertsPerFeedback = Long.valueOf(args[4]);
        secondsPerFeedback = Long.valueOf(args[5]);
        logFileName = args[6];
        indexTechnology = args[7];
        compressionType = args[8];
        basementSize = Integer.valueOf(args[9]);
        numSeconds = Integer.valueOf(args[10]);
        
        logMe("Application Parameters");
        logMe("--------------------------------------------------");
        logMe("  database name = %s",dbName);
        logMe("  %d writer thread(s)",writerThreads);
        logMe("  %,d documents per collection",numMaxInserts);
        logMe("  Documents Per Insert = %d",documentsPerInsert);
        logMe("  Feedback every %,d seconds(s)",secondsPerFeedback);
        logMe("  Feedback every %,d inserts(s)",insertsPerFeedback);
        logMe("  logging to file %s",logFileName);
        logMe("  index technology = %s",indexTechnology);
        
        if (indexTechnology.toLowerCase().equals("tokumx")) {
            logMe("  + compression type = %s",compressionType);
            logMe("  + basement node size (bytes) = %d",basementSize);
        }
            
        logMe("  Run for %,d second(s)",numSeconds);
        logMe("--------------------------------------------------");

        try {
            writer = new BufferedWriter(new FileWriter(new File(logFileName)));
        } catch (IOException e) {
            e.printStackTrace();
        }

        if ((!indexTechnology.toLowerCase().equals("tokumx")) && (!indexTechnology.toLowerCase().equals("mongo"))) {
            // unknown index technology, abort
            logMe(" *** Unknown Indexing Technology %s, shutting down",indexTechnology);
            System.exit(1);
        }

        MongoClientOptions clientOptions = new MongoClientOptions.Builder().connectionsPerHost(2048).writeConcern(WriteConcern.FSYNC_SAFE).build();
        MongoClient m = new MongoClient("localhost", clientOptions);
        
        logMe("mongoOptions | " + m.getMongoOptions().toString());
        logMe("mongoWriteConcern | " + m.getWriteConcern().toString());
        
        DB db = m.getDB(dbName);
        
        jmongoiibench t = new jmongoiibench();

        Thread reporterThread = new Thread(t.new MyReporter());
        reporterThread.start();

        Thread[] tWriterThreads = new Thread[writerThreads];
        
        // start the loaders
        for (int i=0; i<writerThreads; i++) {
            globalWriterThreads.incrementAndGet();
            tWriterThreads[i] = new Thread(t.new MyWriter(writerThreads, i, numMaxInserts, db));
            tWriterThreads[i].start();
        }
        
        try {
            Thread.sleep(10000);
        } catch (Exception e) {
            e.printStackTrace();
        }

        // wait for reporter thread to terminate
        if (reporterThread.isAlive())
            reporterThread.join();

        // wait for writer threads to terminate
        for (int i=0; i<writerThreads; i++) {
            if (tWriterThreads[i].isAlive())
                tWriterThreads[i].join();
        }
        
        try {
            if (writer != null) {
                writer.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        
        // m.dropDatabase("mydb");

        m.close();
        
        logMe("Done!");
    }
    
    class MyWriter implements Runnable {
        int threadCount; 
        int threadNumber; 
        int numTables;
        int numMaxInserts;
        DB db;
        
        java.util.Random rand;
        
        MyWriter(int threadCount, int threadNumber, int numMaxInserts, DB db) {
            this.threadCount = threadCount;
            this.threadNumber = threadNumber;
            this.numMaxInserts = numMaxInserts;
            this.db = db;
            rand = new java.util.Random((long) threadNumber);
        }
        public void run() {
            String collectionName = "purchases_index";
            
            if (indexTechnology.toLowerCase().equals("tokumx")) {
                DBObject cmd = new BasicDBObject();
                cmd.put("create", collectionName);
                cmd.put("compression", compressionType);
                cmd.put("readPageSize", basementSize);
                //cmd.put("basementSize", basementSize);
                CommandResult result = db.command(cmd);
                //logMe(result.toString());
            } else if (indexTechnology.toLowerCase().equals("mongo")) {
                // nothing special to do for a regular mongo collection
                
            } else {
                // unknown index technology, abort
                logMe(" *** Unknown Indexing Technology %s, shutting down",indexTechnology);
                System.exit(1);
            }

            DBCollection coll = db.getCollection(collectionName);
        
            BasicDBObject idxOptions = new BasicDBObject();
            idxOptions.put("background","true");
        
            if (indexTechnology.toLowerCase().equals("tokumx")) {
                idxOptions.put("compression",compressionType);
                idxOptions.put("readPageSize",basementSize);
            }

            coll.ensureIndex(new BasicDBObject("price", 1).append("customerid", 1), idxOptions);
            coll.ensureIndex(new BasicDBObject("cashregisterid", 1).append("price", 1).append("customerid", 1), idxOptions);
            coll.ensureIndex(new BasicDBObject("price", 1).append("dateandtime", 1).append("customerid", 1), idxOptions);
            
            long numInserts = 0;
            int id = 0;
            
            try {
                logMe("Writer thread %d : started to load collection %s",threadNumber, collectionName);

                BasicDBObject[] aDocs = new BasicDBObject[documentsPerInsert];
                
                int numRounds = numMaxInserts / documentsPerInsert;
                
                for (int roundNum = 0; roundNum < numRounds; roundNum++) {
                    for (int i = 0; i < documentsPerInsert; i++) {
                        //id++;
                        double thisCustomerId = rand.nextInt(100000);
                        double thisPrice= (rand.nextDouble() * 500.0) + (double) thisCustomerId;
                        BasicDBObject doc = new BasicDBObject();
                        //doc.put("_id",id);
                        doc.put("dateandtime", System.nanoTime());
                        doc.put("cashregisterid", rand.nextInt(1000));
                        doc.put("customerid", thisCustomerId);
                        doc.put("productid", rand.nextInt(10000));
                        doc.put("price", thisPrice);
                        aDocs[i]=doc;
                    }

                    coll.insert(aDocs);
                    numInserts += documentsPerInsert;
                    globalInserts.addAndGet(documentsPerInsert);
                    
                    if (allDone == 1)
                        break;
                }

            } catch (Exception e) {
                logMe("Writer thread %d : EXCEPTION",threadNumber);
                e.printStackTrace();
            }
            
            long numWriters = globalWriterThreads.decrementAndGet();
            if (numWriters == 0)
                allDone = 1;
        }
    }
    
    
    // reporting thread, outputs information to console and file
    class MyReporter implements Runnable {
        public void run()
        {
            long t0 = System.currentTimeMillis();
            long lastInserts = 0;
            long lastMs = t0;
            long intervalNumber = 0;
            long nextFeedbackMillis = t0 + (1000 * secondsPerFeedback * (intervalNumber + 1));
            long nextFeedbackInserts = lastInserts + insertsPerFeedback;
            long thisInserts = 0;
            long endDueToTime = System.currentTimeMillis() + (1000 * numSeconds);

            while (allDone == 0)
            {
                try {
                    Thread.sleep(100);
                } catch (Exception e) {
                    e.printStackTrace();
                }
                
                long now = System.currentTimeMillis();
                
                if (now >= endDueToTime)
                {
                    allDone = 1;
                }
                
                thisInserts = globalInserts.get();
                if (((now > nextFeedbackMillis) && (secondsPerFeedback > 0)) ||
                    ((thisInserts >= nextFeedbackInserts) && (insertsPerFeedback > 0)))
                {
                    intervalNumber++;
                    nextFeedbackMillis = t0 + (1000 * secondsPerFeedback * (intervalNumber + 1));
                    nextFeedbackInserts = (intervalNumber + 1) * insertsPerFeedback;

                    long elapsed = now - t0;
                    long thisIntervalMs = now - lastMs;
                    
                    long thisIntervalInserts = thisInserts - lastInserts;
                    double thisIntervalInsertsPerSecond = thisIntervalInserts/(double)thisIntervalMs*1000.0;
                    double thisInsertsPerSecond = thisInserts/(double)elapsed*1000.0;
                    
                    if (secondsPerFeedback > 0)
                    {
                        logMe("%,d inserts : %,d seconds : cum ips=%,.2f : int ips=%,.2f", thisInserts, elapsed / 1000l, thisInsertsPerSecond, thisIntervalInsertsPerSecond);
                    } else {
                        logMe("%,d inserts : %,d seconds : cum ips=%,.2f : int ips=%,.2f", intervalNumber * insertsPerFeedback, elapsed / 1000l, thisInsertsPerSecond, thisIntervalInsertsPerSecond);
                    }
                    
                    try {
                        if (outputHeader)
                        {
                            writer.write("tot_inserts\telap_secs\tcum_ips\tint_ips\n");
                            outputHeader = false;
                        }
                            
                        String statusUpdate = "";
                        
                        if (secondsPerFeedback > 0)
                        {
                            statusUpdate = String.format("%d\t%d\t%.2f\t%.2f\n",thisInserts, elapsed / 1000l, thisInsertsPerSecond, thisIntervalInsertsPerSecond);
                        } else {
                            statusUpdate = String.format("%d\t%d\t%.2f\t%.2f\n",intervalNumber * insertsPerFeedback, elapsed / 1000l, thisInsertsPerSecond, thisIntervalInsertsPerSecond);
                        }
                        writer.write(statusUpdate);
                        writer.flush();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }

                    lastInserts = thisInserts;

                    lastMs = now;
                }
            }
            
            // output final numbers...
            long now = System.currentTimeMillis();
            thisInserts = globalInserts.get();
            intervalNumber++;
            nextFeedbackMillis = t0 + (1000 * secondsPerFeedback * (intervalNumber + 1));
            nextFeedbackInserts = (intervalNumber + 1) * insertsPerFeedback;
            long elapsed = now - t0;
            long thisIntervalMs = now - lastMs;
            long thisIntervalInserts = thisInserts - lastInserts;
            double thisIntervalInsertsPerSecond = thisIntervalInserts/(double)thisIntervalMs*1000.0;
            double thisInsertsPerSecond = thisInserts/(double)elapsed*1000.0;
            if (secondsPerFeedback > 0)
            {
                logMe("%,d inserts : %,d seconds : cum ips=%,.2f : int ips=%,.2f", thisInserts, elapsed / 1000l, thisInsertsPerSecond, thisIntervalInsertsPerSecond);
            } else {
                logMe("%,d inserts : %,d seconds : cum ips=%,.2f : int ips=%,.2f", intervalNumber * insertsPerFeedback, elapsed / 1000l, thisInsertsPerSecond, thisIntervalInsertsPerSecond);
            }
            try {
                if (outputHeader)
                {
                    writer.write("tot_inserts\telap_secs\tcum_ips\tint_ips\n");
                    outputHeader = false;
                }
                String statusUpdate = "";
                if (secondsPerFeedback > 0)
                {
                    statusUpdate = String.format("%d\t%d\t%.2f\t%.2f\n",thisInserts, elapsed / 1000l, thisInsertsPerSecond, thisIntervalInsertsPerSecond);
                } else {
                    statusUpdate = String.format("%d\t%d\t%.2f\t%.2f\n",intervalNumber * insertsPerFeedback, elapsed / 1000l, thisInsertsPerSecond, thisIntervalInsertsPerSecond);
                }
                writer.write(statusUpdate);
                writer.flush();
            } catch (IOException e) {
                e.printStackTrace();
            }
            
        }
    }


    public static void logMe(String format, Object... args) {
        System.out.println(Thread.currentThread() + String.format(format, args));
    }
}
