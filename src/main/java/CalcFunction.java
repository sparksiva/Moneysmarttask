import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.spark.api.java.function.Function;
import org.apache.spark.api.java.function.MapFunction;

public class CalcFunction implements Function<String, CommonPojo> {
    CommonPojo commonPojo=new CommonPojo();
    GeoHashCalculation geoHashCalculation=new GeoHashCalculation();

    @Override
    public CommonPojo call(String record) throws Exception {
        //1,1.01069:104.180
        String[] recordArray=record.split(",");
        commonPojo.setId(recordArray[0]);
        commonPojo.setGeoHash(geoHashCalculation.encode(recordArray[1]));
        commonPojo.setCustomerLocation(recordArray[1]);
        return commonPojo;
    }

}
