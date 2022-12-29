import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
//import com.mongodb.MongoClientURI;
//import com.mongodb.DB;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Projections;
import com.mongodb.client.model.Indexes;
import com.mongodb.client.model.BulkWriteOptions;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.WriteModel;
import com.mongodb.client.model.InsertOneModel;
//import com.mongodb.DBCollection;
//import com.mongodb.DBCursor;
//import com.mongodb.BasicDBObject;
//import com.mongodb.DBObject;
//import com.mongodb.CommandResult;
//import com.mongodb.BulkWriteOperation;

import org.bson.Document;
import org.bson.conversions.Bson;

import java.util.ArrayList;
import java.util.List;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.File;
import java.io.Writer;
import java.nio.file.FileAlreadyExistsException;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicLong;

import java.text.SimpleDateFormat;
import java.time.Instant;
import java.util.Date;
import java.util.TimeZone;

public class jmongoiibench {
    public static AtomicLong globalInserts = new AtomicLong(0);
    public static AtomicLong globalWriterThreads = new AtomicLong(0);
    public static AtomicLong globalQueryThreads = new AtomicLong(0);
    public static AtomicLong globalQueriesExecuted = new AtomicLong(0);
    public static AtomicLong globalQueriesTimeMs = new AtomicLong(0);
    public static AtomicLong globalQueriesStarted = new AtomicLong(0);
    public static AtomicLong globalInsertExceptions = new AtomicLong(0);
    
    public static Writer writer = null;
    public static boolean outputHeader = true;
    
    public static int numCashRegisters = 1000;
    public static int numProducts = 10000;
    public static int numCustomers = 100000;
    public static double maxPrice = 500.0;

    public static String dbName;
    public static String collName;
    public static int writerThreads;
    public static long numMaxInserts;
    public static int documentsPerInsert;
    public static long insertsPerFeedback;
    public static long secondsPerFeedback;
    public static String logFileName;
    public static Long numSeconds;
    public static Integer queriesPerInterval;
    public static Integer queryIntervalSeconds;
    public static double queriesPerMinute;
    public static Long msBetweenQueries;
    public static Integer queryLimit;
    public static Integer queryBeginNumDocs;
    public static Integer maxInsertsPerSecond;
    public static Integer maxThreadInsertsPerSecond;
    public static String createCollection;
    public static int numCharFields;
    public static int lengthCharFields;
    public static int numSecondaryIndexes;
    public static int percentCompressible;
    public static int numCompressibleCharacters;
    public static int numUncompressibleCharacters;
    public static boolean suppressExceptions = false;
    public static int exceptionPauseMs = 1000;

    public static int randomStringLength = 4*1024*1024;
    public static String randomStringHolder;
    public static int compressibleStringLength =  4*1024*1024;
    public static String compressibleStringHolder;
    public static String connectionString;
    
    public static int allDone = 0;
    
    public jmongoiibench() {
    }

    public static void main (String[] args) throws Exception {
        if (args.length != 21) {
            logMe("*** ERROR : CONFIGURATION ISSUE ***");
            logMe("jmongoiibench [database name] [collection name] [number of writer threads] [documents per collection] [documents per insert] "
                     +"[inserts feedback] [seconds feedback] [log file name] [number of seconds to run] [queries per interval] [interval (seconds)] "
                     +"[query limit] [inserts for begin query] [max inserts per second] [num char fields] [length char fields] [num secondary indexes] "
                     +"[percent compressible] [create collection] [suppress exceptions (0 or 1)] [connection string]");
            System.exit(1);
        }
        
        dbName = args[0];
        collName = args[1];
        writerThreads = Integer.valueOf(args[2]);
        numMaxInserts = Long.valueOf(args[3]);
        documentsPerInsert = Integer.valueOf(args[4]);
        insertsPerFeedback = Long.valueOf(args[5]);
        secondsPerFeedback = Long.valueOf(args[6]);
        logFileName = args[7];
        numSeconds = Long.valueOf(args[8]);
        queriesPerInterval = Integer.valueOf(args[9]);
        queryIntervalSeconds = Integer.valueOf(args[10]);
        queryLimit = Integer.valueOf(args[11]);
        queryBeginNumDocs = Integer.valueOf(args[12]);
        maxInsertsPerSecond = Integer.valueOf(args[13]);
        numCharFields = Integer.valueOf(args[14]);
        lengthCharFields = Integer.valueOf(args[15]);
        numSecondaryIndexes = Integer.valueOf(args[16]);
        percentCompressible = Integer.valueOf(args[17]);
        createCollection = args[18].toLowerCase();
        if (Integer.valueOf(args[19]) == 1) {
	        suppressExceptions = true;
	    };
        connectionString = args[20];
        
        maxThreadInsertsPerSecond = (maxInsertsPerSecond / writerThreads);

        if ((numSecondaryIndexes < 0) || (numSecondaryIndexes > 3)) {
            logMe("*** ERROR : INVALID NUMBER OF SECONDARY INDEXES, MUST BE >=0 and <= 3 ***");
            logMe("  %d secondary indexes is not supported",numSecondaryIndexes);
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
        
        if ((percentCompressible < 0) || (percentCompressible > 100)) {
            logMe("*** ERROR : INVALID PERCENT COMPRESSIBLE, MUST BE >=0 and <= 100 ***");
            logMe("  %d secondary indexes is not supported",percentCompressible);
            System.exit(1);
        }
        
        numCompressibleCharacters = (int) (((double) percentCompressible / 100.0) * (double) lengthCharFields);
        numUncompressibleCharacters = (int) (((100.0 - (double) percentCompressible) / 100.0) * (double) lengthCharFields);

        logMe("Application Parameters");
        logMe("--------------------------------------------------");
        logMe("  namespace = %s.%s",dbName,collName);
        logMe("  %d writer thread(s)",writerThreads);
        logMe("  %,d documents per collection",numMaxInserts);
        logMe("  %d character fields",numCharFields);
        logMe("  %d bytes per character field",lengthCharFields);
        logMe("  %d secondary indexes",numSecondaryIndexes);
        logMe("  Documents Per Insert = %d",documentsPerInsert);
        logMe("  Maximum of %,d insert(s) per second",maxInsertsPerSecond);
        logMe("  Maximum of %,d insert(s) per second per writer thread",maxThreadInsertsPerSecond);
        logMe("  Feedback every %,d seconds(s)",secondsPerFeedback);
        logMe("  Feedback every %,d inserts(s)",insertsPerFeedback);
        logMe("  logging to file %s",logFileName);
        logMe("  Run for %,d second(s)",numSeconds);
        logMe("  Extra character fields are %d percent compressible",percentCompressible);
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

        //String truststore = "./rds-truststore.jks";
        //String truststorePassword = "secret";

        //System.setProperty("javax.net.ssl.trustStore", truststore);
        //System.setProperty("javax.net.ssl.trustStorePassword", truststorePassword);

        MongoClient m = MongoClients.create(connectionString);

        MongoDatabase db = m.getDatabase(dbName);
        
        // determine server type : MongoDB or DocumentDB
        //CommandResult commandResult = db.command("buildInfo");
        
        logMe("--------------------------------------------------");
        
        if (writerThreads > 1) {
            numMaxInserts = numMaxInserts / writerThreads;
        }

        try {
            writer = new BufferedWriter(new FileWriter(new File(logFileName)));
        } catch (IOException e) {
            e.printStackTrace();
        }

        // test if collection exists
        boolean collectionExists = db.listCollectionNames().into(new ArrayList()).contains(collName);
        //logMe(" ** collection exists = " + collectionExists);

        if (createCollection.equals("n"))
        {
            logMe("Skipping collection creation");
        }
        else
        {
            // create the collection
            MongoCollection coll = db.getCollection(collName);

            // drop the collection, if it exists
            if (collectionExists) {
                logMe(" *** dropping collection " + dbName + "." + collName);
                coll.drop();
            }
    
            IndexOptions idxOptions = new IndexOptions().background(false);
            //idxOptions.put("background",false);
            //idxOptions.background(false);
    
            if (numSecondaryIndexes >= 1) {
                logMe(" *** creating secondary index on price + customerid");
                //coll.createIndex(new Document("price", 1).append("customerid", 1), idxOptions);
                coll.createIndex(Indexes.ascending("price", "customerid"), idxOptions);
            }
            if (numSecondaryIndexes >= 2) {
                logMe(" *** creating secondary index on cashregisterid + price + customerid");
                coll.createIndex(Indexes.ascending("cashregisterid", "price", "customerid"), idxOptions);
            }
            if (numSecondaryIndexes >= 3) {
                logMe(" *** creating secondary index on price + dateandtime + customerid");
                coll.createIndex(Indexes.ascending("price", "dateandtime", "customerid"), idxOptions);
            }
        }

        java.util.Random rand = new java.util.Random();

        // create random string holder
        logMe("  creating %,d bytes of random character data...",randomStringLength);
        char[] tempString = new char[randomStringLength];
        for (int i = 0 ; i < randomStringLength ; i++) { 
            tempString[i] = (char) (rand.nextInt(26) + 'a');
        }
        randomStringHolder = new String(tempString);

        // create compressible string holder
        logMe("  creating %,d bytes of compressible character data...",compressibleStringLength);
        char[] tempStringCompressible = new char[compressibleStringLength];
        for (int i = 0 ; i < compressibleStringLength ; i++) { 
            tempStringCompressible[i] = 'a';
        }
        compressibleStringHolder = new String(tempStringCompressible);


        jmongoiibench t = new jmongoiibench();

        Thread reporterThread = new Thread(t.new MyReporter());
        reporterThread.start();

        Thread queryThread = new Thread(t.new MyQuery(1, 1, numMaxInserts, db, collName));
        if (queriesPerMinute > 0.0) {
            queryThread.start();
        }

        Thread[] tWriterThreads = new Thread[writerThreads];
        
        // start the loaders
        for (int i=0; i<writerThreads; i++) {
            globalWriterThreads.incrementAndGet();
            tWriterThreads[i] = new Thread(t.new MyWriter(writerThreads, i, numMaxInserts, db, maxThreadInsertsPerSecond, collName));
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
        
        m.close();
        
        logMe("Done!");
    }
    
    class MyWriter implements Runnable {
        int threadCount; 
        int threadNumber; 
        long numMaxInserts;
        int maxInsertsPerSecond;
        MongoDatabase db;
        String collectionName;
        
        java.util.Random rand;
        
        MyWriter(int threadCount, int threadNumber, long numMaxInserts, MongoDatabase db, int maxInsertsPerSecond, String collectionName) {
            this.threadCount = threadCount;
            this.threadNumber = threadNumber;
            this.numMaxInserts = numMaxInserts;
            this.maxInsertsPerSecond = maxInsertsPerSecond;
            this.db = db;
            this.collectionName = collectionName;
            rand = new java.util.Random((long) threadNumber);
        }
        public void run() {
            MongoCollection<Document> coll = db.getCollection(collectionName);
        
            long numInserts = 0;
            long numLastInserts = 0;
            //int id = 0;
            long nextMs = System.currentTimeMillis() + 1000;

            try {
                logMe("Writer thread %d : started to load collection %s",threadNumber, collectionName);

                //Document[] aDocs = new Document[documentsPerInsert];
                List<WriteModel<Document>> bulkOperations = new ArrayList<>();
                BulkWriteOptions bwOptions = new BulkWriteOptions().ordered(false);
                
                int numRounds = (int) numMaxInserts / documentsPerInsert;
               
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

          	        //BulkWriteOperation bulk = coll.initializeUnorderedBulkOperation();
                    //BulkWriteOperation bulk = coll.initializeOrderedBulkOperation();

                    for (int i = 0; i < documentsPerInsert; i++) {
                        //id++;
                        int thisCustomerId = rand.nextInt(numCustomers);
                        double thisPrice= ((rand.nextDouble() * maxPrice) + (double) thisCustomerId) / 100.0;
                        Document doc = new Document();
                        //doc.put("_id",id);
                        doc.put("dateandtime", System.currentTimeMillis());
                        doc.put("cashregisterid", rand.nextInt(numCashRegisters));
                        doc.put("customerid", thisCustomerId);
                        doc.put("productid", rand.nextInt(numProducts));
                        doc.put("price", thisPrice);
                        for (int charField = 1; charField <= numCharFields; charField++) {
                            int startPosition = rand.nextInt(randomStringLength-lengthCharFields);
                            doc.put("cf"+Integer.toString(charField), randomStringHolder.substring(startPosition,startPosition+numUncompressibleCharacters) + compressibleStringHolder.substring(startPosition,startPosition+numCompressibleCharacters));
                        }
                        //aDocs[i]=doc;
			            //bulk.insert(doc);
                        bulkOperations.add(new InsertOneModel<>(doc));
                    }

                    try {
                        //coll.insert(aDocs);
			            //bulk.execute();
                        coll.bulkWrite(bulkOperations, bwOptions);
                        bulkOperations.clear();
                        numInserts += documentsPerInsert;
                        globalInserts.addAndGet(documentsPerInsert);
                        
                    } catch (Exception e) {
			            if (!suppressExceptions) {
                            logMe("Writer thread %d : EXCEPTION",threadNumber);
                            e.printStackTrace();
			            }
                        globalInsertExceptions.incrementAndGet();

                        try {
                            Thread.sleep(exceptionPauseMs);
                        } catch (Exception e1) {
                            e1.printStackTrace();
                        }
                    }
                    
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
        long numMaxInserts;
        MongoDatabase db;
        String collectionName;

        java.util.Random rand;
        
        MyQuery(int threadCount, int threadNumber, long numMaxInserts, MongoDatabase db, String collectionName) {
            this.threadCount = threadCount;
            this.threadNumber = threadNumber;
            this.numMaxInserts = numMaxInserts;
            this.db = db;
            this.collectionName = collectionName;
            rand = new java.util.Random((long) threadNumber);
        }
        public void run() {
            long t0 = System.currentTimeMillis();
            //long lastMs = t0;
            long nextQueryMillis = t0;
            boolean outputWaiting = true;
            boolean outputStarted = true;
            
            MongoCollection coll = db.getCollection(collectionName);
        
            //long numQueriesExecuted = 0;
            //long numQueriesTimeMs = 0;
            
            int whichQuery = 0;

            Bson projectionFields = Projections.fields(
                    Projections.include("cashregisterid", "dateandtime", "customerid", "price"),
                    Projections.excludeId());
            
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
                            
                            whichQuery++;
                            if (whichQuery > 3) {
                                whichQuery = 1;
                            }
                            
                            int thisCustomerId = rand.nextInt(numCustomers);
                            double thisPrice = ((rand.nextDouble() * maxPrice) + (double) thisCustomerId) / 100.0;
                            int thisCashRegisterId = rand.nextInt(numCashRegisters);
                            //int thisProductId = rand.nextInt(numProducts);
                            long thisRandomTime = t0 + (long) ((double) (thisNow - t0) * rand.nextDouble());
                            
                            Document query = new Document();
                            Document keys = new Document();

                            // query <NOT RUNNING>
                            // *** WE ARE NOT CURRENTLY RUNNING THIS QUERY, THE _id VALUES ARE AUTO-GENERATED ***
                            /*
                            def generate_pk_query(row_count, start_time):
                              if FLAGS.with_max_table_rows and row_count > FLAGS.max_table_rows :
                                pk_txid = row_count - FLAGS.max_table_rows + random.randrange(FLAGS.max_table_rows)
                              else:
                                pk_txid = random.randrange(max(row_count,1))
                            
                              sql = 'SELECT transactionid FROM %s WHERE '\
                                    '(transactionid >= %d) LIMIT %d' % (
                                  FLAGS.table_name, pk_txid, FLAGS.rows_per_query)
                              return sql
                            */
                            
                            if (whichQuery == 1) {
                                // query 1
                                /*
                                def generate_pdc_query(row_count, start_time):
                                  customerid = random.randrange(0, FLAGS.customers)
                                  price = ((random.random() * FLAGS.max_price) + customerid) / 100.0
                                
                                  random_time = ((time.time() - start_time) * random.random()) + start_time
                                  when = random_time + (random.randrange(max(row_count,1)) / 100000.0)
                                  datetime = time.strftime('%Y-%m-%d %H:%M:%S', time.localtime(when))
                                
                                  sql = 'SELECT price,dateandtime,customerid FROM %s FORCE INDEX (pdc) WHERE '\
                                        '(price=%.2f and dateandtime="%s" and customerid>=%d) OR '\
                                        '(price=%.2f and dateandtime>"%s") OR '\
                                        '(price>%.2f) LIMIT %d' % (FLAGS.table_name, price,
                                                                   datetime, customerid,
                                                                   price, datetime, price,
                                                                   FLAGS.rows_per_query)
                                  return sql
                                  
    db.purchases_index.find({$or: [ {price: <price>, dateandtime: <dateandtime>, customerid: {$gte: <customerid>}},
                                    {price: <price>, dateandtime: {$gt: <dateandtime>}},
                                    {price: {$gt: <price>}} ]}, 
                            {price:1, dateandtime:1, customerid:1, _id:0}).limit(1000);
                                */
                                
                                Document query1a = new Document();
                                query1a.put("price", thisPrice);
                                query1a.put("dateandtime", thisRandomTime);
                                query1a.put("customerid", new Document("$gte", thisCustomerId));
                                
                                Document query1b = new Document();
                                query1b.put("price", thisPrice);
                                query1b.put("dateandtime", new Document("$gt", thisRandomTime));
                                
                                Document query1c = new Document();
                                query1c.put("price", new Document("$gt", thisPrice));
                                
                                ArrayList<Document> list1 = new ArrayList<Document>();
                                list1.add(query1a);
                                list1.add(query1b);
                                list1.add(query1c);
                                
                                query.put("$or", list1);
                                
                                keys.put("price",1);
                                keys.put("dateandtime",1);
                                keys.put("customerid",1);
                                keys.put("_id",0);
                                
                            } else if (whichQuery == 2) {
                                // query 2
                                /*
                                def generate_market_query(row_count, start_time):
                                  customerid = random.randrange(0, FLAGS.customers)
                                  price = ((random.random() * FLAGS.max_price) + customerid) / 100.0
                                
                                  sql = 'SELECT price,customerid FROM %s FORCE INDEX (marketsegment) WHERE '\
                                        '(price=%.2f and customerid>=%d) OR '\
                                        '(price>%.2f) LIMIT %d' % (
                                      FLAGS.table_name, price, customerid, price, FLAGS.rows_per_query)
                                  return sql
    
    db.purchases_index.find({$or: [ {price: <price>, customerid: {$gte: <customerid>} },
                                    {price: {$gt: <price>}} ]}, 
                            {price:1, customerid:1, _id:0}).limit(1000);
                                */
                                
                                Document query2a = new Document();
                                query2a.put("price", thisPrice);
                                query2a.put("customerid", new Document("$gte", thisCustomerId));
                                
                                Document query2b = new Document();
                                query2b.put("price", new Document("$gt", thisPrice));
                                
                                ArrayList<Document> list2 = new ArrayList<Document>();
                                list2.add(query2a);
                                list2.add(query2b);
                                
                                query.put("$or", list2);
                                
                                keys.put("price",1);
                                keys.put("customerid",1);
                                keys.put("_id",0);
                                
                            } else if (whichQuery == 3) {
                                // query 3
                                /*
                                def generate_register_query(row_count, start_time):
                                  customerid = random.randrange(0, FLAGS.customers)
                                  price = ((random.random() * FLAGS.max_price) + customerid) / 100.0
                                  cashregisterid = random.randrange(0, FLAGS.cashregisters)
                                
                                  sql = 'SELECT cashregisterid,price,customerid FROM %s '\
                                        'FORCE INDEX (registersegment) WHERE '\
                                        '(cashregisterid=%d and price=%.2f and customerid>=%d) OR '\
                                        '(cashregisterid=%d and price>%.2f) OR '\
                                        '(cashregisterid>%d) LIMIT %d' % (
                                      FLAGS.table_name, cashregisterid, price, customerid,
                                      cashregisterid, price, cashregisterid, FLAGS.rows_per_query)
                                  return sql                        
                                  
    db.purchases_index.find({$or: [ {cashregisterid: <cashregisterid>, price: <price>, customerid: {$gte: <customerid>} },
                                    {cashregisterid: <cashregisterid>, price: {$gt: <price>}},
                                    {cashregisterid: {$gt: <cashregisterid>}} ]}, 
                            {cashregisterid:1, price:1, customerid:1, _id:0}).limit(1000);;
                                */
                                
                                Document query3a = new Document();
                                query3a.put("cashregisterid", thisCashRegisterId);
                                query3a.put("price", thisPrice);
                                query3a.put("customerid", new Document("$gte", thisCustomerId));
                                
                                Document query3b = new Document();
                                query3b.put("cashregisterid", thisCashRegisterId);
                                query3b.put("price", new Document("$gt", thisPrice));
                                
                                Document query3c = new Document();
                                query3c.put("cashregisterid", new Document("$gt", thisCashRegisterId));
                                
                                ArrayList<Document> list3 = new ArrayList<Document>();
                                list3.add(query3a);
                                list3.add(query3b);
                                list3.add(query3c);
                                
                                query.put("$or", list3);
                                
                                keys.put("cashregisterid",1);
                                keys.put("price",1);
                                keys.put("customerid",1);
                                keys.put("_id",0);
                                
                            }
                            
                            //logMe("Executed query %d",whichQuery);
                            long now = System.currentTimeMillis();
                            //DBCursor cursor = coll.find(query,keys).limit(queryLimit);
                            MongoCursor<Document> cursor = coll.find(query).projection(projectionFields).limit(queryLimit).iterator();
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
            
            //long numQueries = globalQueryThreads.decrementAndGet();
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
                    
                    long thisInsertExceptions = globalInsertExceptions.get();

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
                        logMe("%,d inserts : %,d seconds : cum ips=%,.2f : int ips=%,.2f : cum avg qry=%,.2f : int avg qry=%,.2f : cum avg qpm=%,.2f : int avg qpm=%,.2f : exceptions=%,d", thisInserts, elapsed / 1000l, thisInsertsPerSecond, thisIntervalInsertsPerSecond, thisQueryAvgMs, thisIntervalQueryAvgMs, thisAvgQPM, thisIntervalAvgQPM, thisInsertExceptions);
                    } else {
                        logMe("%,d inserts : %,d seconds : cum ips=%,.2f : int ips=%,.2f : cum avg qry=%,.2f : int avg qry=%,.2f : cum avg qpm=%,.2f : int avg qpm=%,.2f : exceptions=%,d", intervalNumber * insertsPerFeedback, elapsed / 1000l, thisInsertsPerSecond, thisIntervalInsertsPerSecond, thisQueryAvgMs, thisIntervalQueryAvgMs, thisAvgQPM, thisIntervalAvgQPM, thisInsertExceptions);
                    }
                    
                    try {
                        if (outputHeader)
                        {
                            writer.write("tot_inserts\telap_secs\tcum_ips\tint_ips\tcum_qry_avg\tint_qry_avg\tcum_qpm\tint_qpm\texceptions\n");
                            outputHeader = false;
                        }
                            
                        String statusUpdate = "";
                        
                        if (secondsPerFeedback > 0)
                        {
                            statusUpdate = String.format("%d\t%d\t%.2f\t%.2f\t%.2f\t%.2f\t%.2f\t%.2f\t%,d\n",thisInserts, elapsed / 1000l, thisInsertsPerSecond, thisIntervalInsertsPerSecond, thisQueryAvgMs, thisIntervalQueryAvgMs, thisAvgQPM, thisIntervalAvgQPM, thisInsertExceptions);
                        } else {
                            statusUpdate = String.format("%d\t%d\t%.2f\t%.2f\t%.2f\t%.2f\t%.2f\t%.2f\t%,d\n",intervalNumber * insertsPerFeedback, elapsed / 1000l, thisInsertsPerSecond, thisIntervalInsertsPerSecond, thisQueryAvgMs, thisIntervalQueryAvgMs, thisAvgQPM, thisIntervalAvgQPM, thisInsertExceptions);
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
        Date currentUtcTime = Date.from(Instant.now());
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss z");
        sdf.setTimeZone(TimeZone.getTimeZone("Etc/UTC"));
        // System.out.println("Current UTC time is " + sdf.format(currentUtcTime));

        System.out.println(Thread.currentThread().toString() + ' ' + sdf.format(currentUtcTime) + " | " + String.format(format, args));
    }
}
