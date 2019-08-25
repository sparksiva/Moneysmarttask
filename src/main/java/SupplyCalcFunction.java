import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.spark.api.java.function.MapFunction;

public class SupplyCalcFunction implements MapFunction<ConsumerRecord<String,String>,SupplyPojo> {
    SupplyPojo supply=new SupplyPojo();
    GeoHashCalculation geoHashCalculation=new GeoHashCalculation();
    @Override
    public SupplyPojo call(ConsumerRecord<String, String> record) throws Exception {
        //1,1.01069:104.180
        System.out.println("incoming supply record is: "+record.value());
        String[] recordArray=record.value().split(",");
        supply.setDriverId(recordArray[0]);
        supply.setGeoHash(geoHashCalculation.encode(recordArray[1]));
        return supply;
    }
}
