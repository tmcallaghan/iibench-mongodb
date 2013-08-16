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
    public static AtomicLong globalQueryThreads = new AtomicLong(0);
    public static AtomicLong globalQueriesExecuted = new AtomicLong(0);
    public static AtomicLong globalQueriesTimeMs = new AtomicLong(0);
    public static AtomicLong globalQueriesStarted = new AtomicLong(0);
    
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
    public static Long numSeconds;
    public static Integer queriesPerInterval;
    public static Integer queryIntervalSeconds;
    public static double queriesPerMinute;
    public static Long msBetweenQueries;
    public static Integer queryLimit;
    public static Integer queryBeginNumDocs;
    public static Integer maxInsertsPerSecond;
    public static String myWriteConcern;
    
    public static int allDone = 0;
    
    public jmongoiibench() {
    }

    public static void main (String[] args) throws Exception {
        if (args.length != 16) {
            logMe("*** ERROR : CONFIGURATION ISSUE ***");
            logMe("jmongoiibench [database name] [number of writer threads] [documents per collection] [documents per insert] [inserts feedback] [seconds feedback] [log file name] [compression type] [basement node size (bytes)] [number of seconds to run] [queries per interval] [interval (seconds)] [query limit] [inserts for begin query] [max inserts per second] [writeconcern]");
            System.exit(1);
        }
        
        dbName = args[0];
        writerThreads = Integer.valueOf(args[1]);
        numMaxInserts = Integer.valueOf(args[2]);
        documentsPerInsert = Integer.valueOf(args[3]);
        insertsPerFeedback = Long.valueOf(args[4]);
        secondsPerFeedback = Long.valueOf(args[5]);
        logFileName = args[6];
        compressionType = args[7];
        basementSize = Integer.valueOf(args[8]);
        numSeconds = Long.valueOf(args[9]);
        queriesPerInterval = Integer.valueOf(args[10]);
        queryIntervalSeconds = Integer.valueOf(args[11]);
        queryLimit = Integer.valueOf(args[12]);
        queryBeginNumDocs = Integer.valueOf(args[13]);
        maxInsertsPerSecond = Integer.valueOf(args[14]);
        myWriteConcern = args[15];
        
        WriteConcern myWC = new WriteConcern();
        if (myWriteConcern.toLowerCase().equals("fsync_safe")) {
            myWC = WriteConcern.FSYNC_SAFE;
        }
        else if ((myWriteConcern.toLowerCase().equals("none"))) {
            myWC = WriteConcern.NONE;
        }
        else if ((myWriteConcern.toLowerCase().equals("normal"))) {
            myWC = WriteConcern.NORMAL;
        }
        else if ((myWriteConcern.toLowerCase().equals("replicas_safe"))) {
            myWC = WriteConcern.REPLICAS_SAFE;
        }
        else if ((myWriteConcern.toLowerCase().equals("safe"))) {
            myWC = WriteConcern.SAFE;
        } 
        else {
            logMe("*** ERROR : WRITE CONCERN ISSUE ***");
            logMe("  write concern %s is not supported",myWriteConcern);
            System.exit(1);
        }
        
        if ((queriesPerInterval <= 0) || (queryIntervalSeconds <= 0))
        {
            queriesPerMinute = 0.0;
            msBetweenQueries = 0l;
        }
        else
        {
            queriesPerMinute = (double)queriesPerInterval * (60.0 / (double)queryIntervalSeconds);
            msBetweenQueries = (long)((1000.0 * (double)queryIntervalSeconds) / (double)queriesPerInterval);
        }
        
        logMe("Application Parameters");
        logMe("--------------------------------------------------");
        logMe("  database name = %s",dbName);
        logMe("  %d writer thread(s)",writerThreads);
        logMe("  %,d documents per collection",numMaxInserts);
        logMe("  Documents Per Insert = %d",documentsPerInsert);
        logMe("  Maximum of %,d insert(s) per second",maxInsertsPerSecond);
        logMe("  Feedback every %,d seconds(s)",secondsPerFeedback);
        logMe("  Feedback every %,d inserts(s)",insertsPerFeedback);
        logMe("  logging to file %s",logFileName);
        logMe("  Run for %,d second(s)",numSeconds);
        if (queriesPerMinute > 0.0)
        {
            logMe("  Attempting %,.2f queries per minute",queriesPerMinute);
            logMe("  Queries limited to %,d document(s)",queryLimit);
            logMe("  Starting queries after %,d document(s) inserted",queryBeginNumDocs);
        }
        else
        {
            logMe("  NO queries, insert only benchmark");
        }
        logMe("  write concern = %s",myWriteConcern);
        
        MongoClientOptions clientOptions = new MongoClientOptions.Builder().connectionsPerHost(2048).writeConcern(myWC).build();
        MongoClient m = new MongoClient("localhost", clientOptions);
        
        logMe("mongoOptions | " + m.getMongoOptions().toString());
        logMe("mongoWriteConcern | " + m.getWriteConcern().toString());
        
        DB db = m.getDB(dbName);
        
        // determine server type : mongo or tokumx
        DBObject checkServerCmd = new BasicDBObject();
        CommandResult commandResult = db.command("buildInfo");
        
        // check if tokumxVersion exists, otherwise assume mongo
        if (commandResult.toString().contains("tokumxVersion")) {
            indexTechnology = "tokumx";
        }
        else
        {
            indexTechnology = "mongo";
        }
        
        if ((!indexTechnology.toLowerCase().equals("tokumx")) && (!indexTechnology.toLowerCase().equals("mongo"))) {
            // unknown index technology, abort
            logMe(" *** Unknown Indexing Technology %s, shutting down",indexTechnology);
            System.exit(1);
        }
        
        logMe("  index technology = %s",indexTechnology);
        
        if (indexTechnology.toLowerCase().equals("tokumx")) {
            logMe("  + compression type = %s",compressionType);
            logMe("  + basement node size (bytes) = %d",basementSize);
        }
        
        logMe("--------------------------------------------------");

        try {
            writer = new BufferedWriter(new FileWriter(new File(logFileName)));
        } catch (IOException e) {
            e.printStackTrace();
        }


        jmongoiibench t = new jmongoiibench();

        Thread reporterThread = new Thread(t.new MyReporter());
        reporterThread.start();

        Thread queryThread = new Thread(t.new MyQuery(1, 1, numMaxInserts, db));
        if (queriesPerMinute > 0.0) {
            queryThread.start();
        }

        Thread[] tWriterThreads = new Thread[writerThreads];
        
        // start the loaders
        for (int i=0; i<writerThreads; i++) {
            globalWriterThreads.incrementAndGet();
            tWriterThreads[i] = new Thread(t.new MyWriter(writerThreads, i, numMaxInserts, db, maxInsertsPerSecond));
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

        // wait for query thread to terminate
        if (queryThread.isAlive())
            queryThread.join();

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
        int numMaxInserts;
        int maxInsertsPerSecond;
        DB db;
        
        java.util.Random rand;
        
        MyWriter(int threadCount, int threadNumber, int numMaxInserts, DB db, int maxInsertsPerSecond) {
            this.threadCount = threadCount;
            this.threadNumber = threadNumber;
            this.numMaxInserts = numMaxInserts;
            this.maxInsertsPerSecond = maxInsertsPerSecond;
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
            long numLastInserts = 0;
            int id = 0;
            long nextMs = System.currentTimeMillis() + 1000;
            
            try {
                logMe("Writer thread %d : started to load collection %s",threadNumber, collectionName);

                BasicDBObject[] aDocs = new BasicDBObject[documentsPerInsert];
                
                int numRounds = numMaxInserts / documentsPerInsert;
                
                for (int roundNum = 0; roundNum < numRounds; roundNum++) {
                    if ((numInserts - numLastInserts) >= maxInsertsPerSecond) {
                        // pause until a second has passed
                        while (System.currentTimeMillis() < nextMs) {
                            try {
                                Thread.sleep(20);
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }
                        numLastInserts = numInserts;
                        nextMs = System.currentTimeMillis() + 1000;
                    }

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


    class MyQuery implements Runnable {
        int threadCount; 
        int threadNumber; 
        int numMaxInserts;
        DB db;

        java.util.Random rand;
        
        MyQuery(int threadCount, int threadNumber, int numMaxInserts, DB db) {
            this.threadCount = threadCount;
            this.threadNumber = threadNumber;
            this.numMaxInserts = numMaxInserts;
            this.db = db;
            rand = new java.util.Random((long) threadNumber);
        }
        public void run() {
            long t0 = System.currentTimeMillis();
            long lastMs = t0;
            long nextQueryMillis = t0;
            boolean outputWaiting = true;
            boolean outputStarted = true;
            
            String collectionName = "purchases_index";
            
            DBCollection coll = db.getCollection(collectionName);
        
            long numQueriesExecuted = 0;
            long numQueriesTimeMs = 0;
            int id = 0;
            
            try {
                logMe("Query thread %d : ready to query collection %s",threadNumber, collectionName);

                while (allDone == 0) {
                    //try {
                    //    Thread.sleep(10);
                    //} catch (Exception e) {
                    //    e.printStackTrace();
                    // }
                    
                    long thisNow = System.currentTimeMillis();
                    
                    // wait until my next runtime
                    if (thisNow > nextQueryMillis) {
                        nextQueryMillis = thisNow + msBetweenQueries;
                        
                        // check if number of inserts reached
                        if (globalInserts.get() >= queryBeginNumDocs) {
                            if (outputStarted)
                            {
                                logMe("Query thread %d : now running",threadNumber,queryBeginNumDocs);
                                outputStarted = false;
                                // set query start time
                                globalQueriesStarted.set(thisNow);
                            }
                            
                            String querySearchField = "";
                            int querySearchValue = 0;
                    
                            querySearchField = "cashregisterid";
                            querySearchValue = rand.nextInt(1000);
                            
// cashregisterid = ?  and price >= ?

                            BasicDBObject query = new BasicDBObject();
                            BasicDBObject keys = new BasicDBObject();
                            // query.put(querySearchField, new BasicDBObject("$gte", querySearchValue));
                            query.put(querySearchField, querySearchValue);
                            
                            // here is how you include particular fields
                            //keys.put("URI",1);
                            //keys.put("name",1);
                            // here is how you exclude particular fields
                            //keys.put("_id",0);
                            long now = System.currentTimeMillis();
                            DBCursor cursor = coll.find(query,keys).limit(queryLimit);
                            try {
                                while(cursor.hasNext()) {
                                    //System.out.println(cursor.next());
                                    cursor.next();
                                }
                            } finally {
                                cursor.close();
                            }
                            long elapsed = System.currentTimeMillis() - now;
                    
                            //logMe("Query thread %d : performing : %s",threadNumber,thisSelect);
                    
                            globalQueriesExecuted.incrementAndGet();
                            globalQueriesTimeMs.addAndGet(elapsed);
                        } else {
                            if (outputWaiting)
                            {
                                logMe("Query thread %d : waiting for %,d document insert(s) before starting",threadNumber,queryBeginNumDocs);
                                outputWaiting = false;
                            }
                        }
                    }
                }

            } catch (Exception e) {
                logMe("Query thread %d : EXCEPTION",threadNumber);
                e.printStackTrace();
            }
            
            long numQueries = globalQueryThreads.decrementAndGet();
        }
    }

    
    // reporting thread, outputs information to console and file
    class MyReporter implements Runnable {
        public void run()
        {
            long t0 = System.currentTimeMillis();
            long lastInserts = 0;
            long lastQueriesNum = 0;
            long lastQueriesMs = 0;
            long lastMs = t0;
            long intervalNumber = 0;
            long nextFeedbackMillis = t0 + (1000 * secondsPerFeedback * (intervalNumber + 1));
            long nextFeedbackInserts = lastInserts + insertsPerFeedback;
            long thisInserts = 0;
            long thisQueriesNum = 0;
            long thisQueriesMs = 0;
            long thisQueriesStarted = 0;
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
                thisQueriesNum = globalQueriesExecuted.get();
                thisQueriesMs = globalQueriesTimeMs.get();
                thisQueriesStarted = globalQueriesStarted.get();
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

                    long thisIntervalQueriesNum = thisQueriesNum - lastQueriesNum;
                    long thisIntervalQueriesMs = thisQueriesMs - lastQueriesMs;
                    double thisIntervalQueryAvgMs = 0;
                    double thisQueryAvgMs = 0;
                    double thisIntervalAvgQPM = 0;
                    double thisAvgQPM = 0;

                    if (thisIntervalQueriesNum > 0) {
                        thisIntervalQueryAvgMs = thisIntervalQueriesMs/(double)thisIntervalQueriesNum;
                    }
                    if (thisQueriesNum > 0) {
                        thisQueryAvgMs = thisQueriesMs/(double)thisQueriesNum;
                    }
                    
                    if (thisQueriesStarted > 0)
                    {
                        long adjustedElapsed = now - thisQueriesStarted;
                        if (adjustedElapsed > 0)
                        {
                            thisAvgQPM = (double)thisQueriesNum/((double)adjustedElapsed/1000.0/60.0);
                        }
                        if (thisIntervalMs > 0)
                        {
                            thisIntervalAvgQPM = (double)thisIntervalQueriesNum/((double)thisIntervalMs/1000.0/60.0);
                        }
                    }
                    
                    if (secondsPerFeedback > 0)
                    {
                        logMe("%,d inserts : %,d seconds : cum ips=%,.2f : int ips=%,.2f : cum avg qry=%,.2f : int avg qry=%,.2f : cum avg qpm=%,.2f : int avg qpm=%,.2f", thisInserts, elapsed / 1000l, thisInsertsPerSecond, thisIntervalInsertsPerSecond, thisQueryAvgMs, thisIntervalQueryAvgMs, thisAvgQPM, thisIntervalAvgQPM);
                    } else {
                        logMe("%,d inserts : %,d seconds : cum ips=%,.2f : int ips=%,.2f : cum avg qry=%,.2f : int avg qry=%,.2f : cum avg qpm=%,.2f : int avg qpm=%,.2f", intervalNumber * insertsPerFeedback, elapsed / 1000l, thisInsertsPerSecond, thisIntervalInsertsPerSecond, thisQueryAvgMs, thisIntervalQueryAvgMs, thisAvgQPM, thisIntervalAvgQPM);
                    }
                    
                    try {
                        if (outputHeader)
                        {
                            writer.write("tot_inserts\telap_secs\tcum_ips\tint_ips\tcum_qry_avg\tint_qry_avg\tcum_qpm\tint_qpm\n");
                            outputHeader = false;
                        }
                            
                        String statusUpdate = "";
                        
                        if (secondsPerFeedback > 0)
                        {
                            statusUpdate = String.format("%d\t%d\t%.2f\t%.2f\t%.2f\t%.2f\t%.2f\t%.2f\n",thisInserts, elapsed / 1000l, thisInsertsPerSecond, thisIntervalInsertsPerSecond, thisQueryAvgMs, thisIntervalQueryAvgMs, thisAvgQPM, thisIntervalAvgQPM);
                        } else {
                            statusUpdate = String.format("%d\t%d\t%.2f\t%.2f\t%.2f\t%.2f\t%.2f\t%.2f\n",intervalNumber * insertsPerFeedback, elapsed / 1000l, thisInsertsPerSecond, thisIntervalInsertsPerSecond, thisQueryAvgMs, thisIntervalQueryAvgMs, thisAvgQPM, thisIntervalAvgQPM);
                        }
                        writer.write(statusUpdate);
                        writer.flush();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }

                    lastInserts = thisInserts;
                    lastQueriesNum = thisQueriesNum;
                    lastQueriesMs = thisQueriesMs;

                    lastMs = now;
                }
            }
            
            // output final numbers...
            long now = System.currentTimeMillis();
            thisInserts = globalInserts.get();
            thisQueriesNum = globalQueriesExecuted.get();
            thisQueriesMs = globalQueriesTimeMs.get();
            thisQueriesStarted = globalQueriesStarted.get();
            intervalNumber++;
            nextFeedbackMillis = t0 + (1000 * secondsPerFeedback * (intervalNumber + 1));
            nextFeedbackInserts = (intervalNumber + 1) * insertsPerFeedback;
            long elapsed = now - t0;
            long thisIntervalMs = now - lastMs;
            long thisIntervalInserts = thisInserts - lastInserts;
            double thisIntervalInsertsPerSecond = thisIntervalInserts/(double)thisIntervalMs*1000.0;
            double thisInsertsPerSecond = thisInserts/(double)elapsed*1000.0;
            long thisIntervalQueriesNum = thisQueriesNum - lastQueriesNum;
            long thisIntervalQueriesMs = thisQueriesMs - lastQueriesMs;
            double thisIntervalQueryAvgMs = 0;
            double thisQueryAvgMs = 0;
            double thisIntervalAvgQPM = 0;
            double thisAvgQPM = 0;
            if (thisIntervalQueriesNum > 0) {
                thisIntervalQueryAvgMs = thisIntervalQueriesMs/(double)thisIntervalQueriesNum;
            }
            if (thisQueriesNum > 0) {
                thisQueryAvgMs = thisQueriesMs/(double)thisQueriesNum;
            }
            if (thisQueriesStarted > 0)
            {
                long adjustedElapsed = now - thisQueriesStarted;
                if (adjustedElapsed > 0)
                {
                    thisAvgQPM = (double)thisQueriesNum/((double)adjustedElapsed/1000.0/60.0);
                }
                if (thisIntervalMs > 0)
                {
                    thisIntervalAvgQPM = (double)thisIntervalQueriesNum/((double)thisIntervalMs/1000.0/60.0);
                }
            }
            if (secondsPerFeedback > 0)
            {
                logMe("%,d inserts : %,d seconds : cum ips=%,.2f : int ips=%,.2f : cum avg qry=%,.2f : int avg qry=%,.2f : cum avg qpm=%,.2f : int avg qpm=%,.2f", thisInserts, elapsed / 1000l, thisInsertsPerSecond, thisIntervalInsertsPerSecond, thisQueryAvgMs, thisIntervalQueryAvgMs, thisAvgQPM, thisIntervalAvgQPM);
            } else {
                logMe("%,d inserts : %,d seconds : cum ips=%,.2f : int ips=%,.2f : cum avg qry=%,.2f : int avg qry=%,.2f : cum avg qpm=%,.2f : int avg qpm=%,.2f", intervalNumber * insertsPerFeedback, elapsed / 1000l, thisInsertsPerSecond, thisIntervalInsertsPerSecond, thisQueryAvgMs, thisIntervalQueryAvgMs, thisAvgQPM, thisIntervalAvgQPM);
            }
            try {
                if (outputHeader)
                {
                    writer.write("tot_inserts\telap_secs\tcum_ips\tint_ips\tcum_qry_avg\tint_qry_avg\tcum_qpm\tint_qpm\n");
                    outputHeader = false;
                }
                String statusUpdate = "";
                if (secondsPerFeedback > 0)
                {
                    statusUpdate = String.format("%d\t%d\t%.2f\t%.2f\t%.2f\t%.2f\t%.2f\t%.2f\n",thisInserts, elapsed / 1000l, thisInsertsPerSecond, thisIntervalInsertsPerSecond, thisQueryAvgMs, thisIntervalQueryAvgMs, thisAvgQPM, thisIntervalAvgQPM);
                } else {
                    statusUpdate = String.format("%d\t%d\t%.2f\t%.2f\t%.2f\t%.2f\t%.2f\t%.2f\n",intervalNumber * insertsPerFeedback, elapsed / 1000l, thisInsertsPerSecond, thisIntervalInsertsPerSecond, thisQueryAvgMs, thisIntervalQueryAvgMs, thisAvgQPM, thisIntervalAvgQPM);
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
