import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.spark.SparkConf;
import org.apache.spark.api.java.JavaRDD;
import org.apache.spark.api.java.function.Function;
import org.apache.spark.api.java.function.PairFunction;
import org.apache.spark.api.java.function.VoidFunction;
import org.apache.spark.sql.*;
import org.apache.spark.sql.expressions.Window;
import org.apache.spark.sql.expressions.WindowSpec;
import org.apache.spark.streaming.Durations;
import org.apache.spark.streaming.api.java.JavaDStream;
import org.apache.spark.streaming.api.java.JavaInputDStream;
import org.apache.spark.streaming.api.java.JavaPairDStream;
import org.apache.spark.streaming.api.java.JavaStreamingContext;
import org.apache.spark.streaming.kafka010.ConsumerStrategies;
import org.apache.spark.streaming.kafka010.KafkaUtils;
import org.apache.spark.streaming.kafka010.LocationStrategies;
import scala.Tuple2;
import scala.collection.Seq;

import java.io.Serializable;
import java.util.*;

public final class PricingDriver implements Serializable {
    private static final long serialVersionUID = 4L;

    public static void main(String[] args) throws Exception {

        System.setProperty("hadoop.home.dir", "C:\\hadoop\\");
        SparkConf conf = new SparkConf().setMaster("local[2]").setAppName("sivasspark");
        JavaStreamingContext jssc = new JavaStreamingContext(conf, Durations.seconds(6));
        jssc.sparkContext().setLogLevel("ERROR");
        jssc.checkpoint("C:\\Users\\sivae\\OneDrive\\Desktop\\siva'snotes\\LocalMode\\checkpointdir");

        JavaInputDStream<ConsumerRecord<String, String>> directStream = configureKafka(jssc);
        JavaPairDStream<String, String> pairedDStream = directStream.mapToPair(new PairFunction<ConsumerRecord<String, String>, String, String>() {
            @Override
            public Tuple2<String, String> call(ConsumerRecord<String, String> record) throws Exception {
                return new Tuple2<String, String>(record.topic(), record.value());
            }
        });

        JavaDStream<CommonPojo> dataDStream = pairedDStream.filter(record -> (!record._2.equals(null) && !record._2.equals("")) && !(record._2.split(",").length < 2))
                .mapValues(new CalcFunction())
                .map(new Function<Tuple2<String, CommonPojo>, CommonPojo>() {

                    @Override
                    public CommonPojo call(Tuple2<String, CommonPojo> stringDemandPojoTuple2) throws Exception {
                        CommonPojo commonPojo = stringDemandPojoTuple2._2;
                        commonPojo.setType(stringDemandPojoTuple2._1);
                        return commonPojo;
                    }
                });

        dataDStream.foreachRDD(new VoidFunction<JavaRDD<CommonPojo>>() {
            @Override
            public void call(JavaRDD<CommonPojo> rdd) throws Exception {
                SparkSession spark = SparkSession.builder().config(rdd.context().getConf()).getOrCreate();
                Dataset<Row> commonPojoDataset = spark.createDataFrame(rdd, CommonPojo.class);

                Dataset<Row> groupedCommonPojoDataset = commonPojoDataset.groupBy("geoHash", "type").count().as("count");

                WindowSpec window = Window.partitionBy("geoHash").orderBy("type");
                Dataset<Row> leaddf = groupedCommonPojoDataset.withColumn("num_drivers", functions.lead("count", 1).over(window));

                Dataset<Row> leaddf1 = leaddf.withColumn("surgePriceMultiplier",
                        functions.when(leaddf.col("num_drivers").isNotNull(), leaddf.col("count").divide(leaddf.col("num_drivers"))).otherwise(0));

                List lis = new ArrayList();
                lis.add("geoHash");
                lis.add("type");
                Seq<String> seq = scala.collection.JavaConversions.asScalaBuffer(lis).toSeq();
                Dataset<Row> pricedDataset = leaddf1.join(commonPojoDataset, seq);

                pricedDataset = pricedDataset.withColumnRenamed("type", "supplyDemandType");
                pricedDataset = pricedDataset.withColumnRenamed("id", "customerID");
                pricedDataset = pricedDataset.withColumnRenamed("count", "num_customers");
                pricedDataset = pricedDataset.withColumn("measuredTimestamp", functions.lit(functions.current_timestamp()));
                pricedDataset = pricedDataset.withColumn("asOfDate", org.apache.spark.sql.functions.lit(functions.current_date()));

                pricedDataset = pricedDataset.filter(pricedDataset.col("supplyDemandType").equalTo("demanddata"));
                System.out.println("reding from mysql");
                Properties prop = new Properties();
                prop.put("dbtable", "customer_behaviour");
                prop.put("user", "root");
                prop.put("password", "JobSearch@2019");
                String url = "jdbc:mysql://localhost:3306/poc?useUnicode=true&useJDBCCompliantTimezoneShift=true&useLegacyDatetimeCode=false&serverTimezone=UTC";

                Dataset<Row> customerBehaviourDF = spark.read().jdbc(url, "customer_behaviour", prop);
                //customerBehaviourDF.show();
                List customeridlist = new ArrayList();
                customeridlist.add("customerId");

                Seq<String> customeridSeq = scala.collection.JavaConversions.asScalaBuffer(customeridlist).toSeq();
                // |customerId|buy_history|search_history|item|recorded_date|geoHash asOfDate

                Dataset<Row> matchedCustomerDF = pricedDataset.join(customerBehaviourDF, customeridSeq);
                matchedCustomerDF = matchedCustomerDF.select("customerId", "geoHash", "buy_history", "search_history", "item", "recorded_date", "asOfDate");
                WindowSpec windowSpecCB = Window.partitionBy("customerId").orderBy(functions.col("recorded_date").desc());
                matchedCustomerDF = matchedCustomerDF.withColumn("latestRank", functions.rank().over(windowSpecCB));
                matchedCustomerDF = matchedCustomerDF.filter(matchedCustomerDF.col("latestRank").equalTo(1));
                matchedCustomerDF.show(1000, false);
                matchedCustomerDF.selectExpr("customerId as key", "search_history as value").write().format("kafka").option("kafka.bootstrap.servers", "localhost:9092").option("topic", "customerbehaviour").save();

                //pricedDataset.write().format("csv").mode(SaveMode.Append).save("hdfs://172.31.29.243/hivedir/surgepricemultiplier/");
                pricedDataset.write().format("orc").mode("append").option("url", "jdbc:mysql://localhost:3306/poc?useUnicode=true&useJDBCCompliantTimezoneShift=true&useLegacyDatetimeCode=false&serverTimezone=UTC").option("dbtable", "surgepricemultiplier").option("user", "root").option("password", "JobSearch@2019").save();

                pricedDataset.show(1000, false);
            }
        });

        JavaDStream<DriverDetailsPojo> driverDataset = directStream.filter(record -> (!record.value().equals(null) && !record.value().equals(""))).filter(record -> record.topic().equals("supplydata") && !(record.value().split(",").length < 5))
                .map(new DriverAdaptor());
        driverDataset.foreachRDD(new VoidFunction<JavaRDD<DriverDetailsPojo>>() {

            @Override
            public void call(JavaRDD<DriverDetailsPojo> rdd) throws Exception {
                SparkSession spark = SparkSession.builder().config(rdd.context().getConf()).getOrCreate();
                Dataset<Row> commonPojoDataset = spark.createDataFrame(rdd, DriverDetailsPojo.class);
                WindowSpec windowSpec = Window.partitionBy("geoHash");
                Dataset<Row> networkTrafficDataset = commonPojoDataset.withColumn("speedPerKM",
                        functions.round((windowSpec.withAggregate(functions.sum(commonPojoDataset.col("distanceCovered"))).divide(
                                windowSpec.withAggregate(functions.sum(commonPojoDataset.col("tripDuration"))))), 1).multiply(100));
                networkTrafficDataset = networkTrafficDataset.withColumn("trafficStatus",
                        functions.when(networkTrafficDataset.col("speedPerKM").between("0", "30"), "Heavy Traffic")
                                .when(networkTrafficDataset.col("speedPerKM").between("30", "60"), "Medium Traffic")
                                .otherwise("Free Traffic"));

                networkTrafficDataset = networkTrafficDataset.withColumn("measuredTimestamp", functions.lit(functions.current_timestamp()));
                networkTrafficDataset = networkTrafficDataset.withColumn("asOfDate", org.apache.spark.sql.functions.lit(functions.current_date()));
                //networkTrafficDataset.write().mode(SaveMode.Append).format("csv").save("hdfs://172.31.29.243/hivedir/trafficcongestionestimates/");
                List networkTrafficDatasetlist = new ArrayList();
                networkTrafficDatasetlist.add(networkTrafficDataset.col("destinationLocation"));
                networkTrafficDatasetlist.add(networkTrafficDataset.col("distanceCovered"));
                networkTrafficDatasetlist.add(networkTrafficDataset.col("driverId"));
                networkTrafficDatasetlist.add(networkTrafficDataset.col("geoHash"));
                networkTrafficDatasetlist.add(networkTrafficDataset.col("sourceLocation"));
                networkTrafficDatasetlist.add(networkTrafficDataset.col("tripDuration"));
                networkTrafficDatasetlist.add(networkTrafficDataset.col("speedPerKM"));
                networkTrafficDatasetlist.add(networkTrafficDataset.col("trafficStatus"));
                networkTrafficDatasetlist.add(networkTrafficDataset.col("measuredTimestamp"));
                networkTrafficDatasetlist.add(networkTrafficDataset.col("asOfDate"));
                Seq<Column> networkTrafficDatasetSeq = scala.collection.JavaConversions.asScalaBuffer(networkTrafficDatasetlist).toSeq();
                networkTrafficDataset.select(networkTrafficDatasetSeq).write().format("jdbc").mode("append").option("url", "jdbc:mysql://localhost:3306/poc?useUnicode=true&useJDBCCompliantTimezoneShift=true&useLegacyDatetimeCode=false&serverTimezone=UTC").option("dbtable", "driverDetails").option("user", "root").option("password", "JobSearch@2019").save();
                networkTrafficDataset.show(1000, false);
                Dataset<Row> networkTrafficDatasetGeoHash = networkTrafficDataset.drop("driverId").drop("speedPerKM").drop("destinationLocation").drop("distanceCovered").drop("sourceLocation").drop("tripDuration");

                List networkTrafficDatasetGeoHashlist = new ArrayList();
                networkTrafficDatasetGeoHashlist.add(networkTrafficDataset.col("geoHash"));
                networkTrafficDatasetGeoHashlist.add(networkTrafficDataset.col("trafficStatus"));
                networkTrafficDatasetGeoHashlist.add(networkTrafficDataset.col("measuredTimestamp"));
                networkTrafficDatasetGeoHashlist.add(networkTrafficDataset.col("asOfDate"));
                Seq<Column> networkTrafficDatasetGeoHashSeq = scala.collection.JavaConversions.asScalaBuffer(networkTrafficDatasetGeoHashlist).toSeq();

                networkTrafficDatasetGeoHash.distinct().select(networkTrafficDatasetGeoHashSeq).write().format("jdbc").mode("append").option("url", "jdbc:mysql://localhost:3306/poc?useUnicode=true&useJDBCCompliantTimezoneShift=true&useLegacyDatetimeCode=false&serverTimezone=UTC").option("dbtable", "trafficcongestionestimates").option("user", "root").option("password", "JobSearch@2019").save();
                networkTrafficDatasetGeoHash.show(1000, false);
            }
        });
        jssc.start();
        jssc.awaitTermination();
    }

    public static JavaInputDStream<ConsumerRecord<String, String>> configureKafka(JavaStreamingContext jssc) {
        Map<String, Object> kmap = new HashMap();
        kmap.put("bootstrap.servers", "localhost:9092");
        kmap.put("key.deserializer", StringDeserializer.class);
        kmap.put("value.deserializer", StringDeserializer.class);
        //kmap.put("serializedClass", RawRating.class);
        kmap.put("group.id", "mygroup");
        kmap.put("auto.offset.reset", "earliest");
        kmap.put("enable.auto.commit", "false");
        Collection<String> topics = Arrays.asList("supplydata", "demanddata");

        return KafkaUtils.createDirectStream(
                jssc,
                LocationStrategies.PreferConsistent(),
                ConsumerStrategies.<String, String>Subscribe(topics, kmap));

    }

}
