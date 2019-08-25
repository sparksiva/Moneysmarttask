import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.spark.api.java.function.MapFunction;

public class DemandCalcFunction implements MapFunction<ConsumerRecord<String,String>,DemandPojo> {
    DemandPojo demand=new DemandPojo();
    GeoHashCalculation geoHashCalculation=new GeoHashCalculation();
    @Override
    public DemandPojo call(ConsumerRecord<String, String> record) throws Exception {
        //1,1.01069:104.180
        System.out.println("incoming demand record is: "+record.value());
        String[] recordArray=record.value().split(",");
        demand.setCustomerId(recordArray[0]);
        demand.setGeoHash(geoHashCalculation.encode(recordArray[1]));
        return demand;
    }
}
