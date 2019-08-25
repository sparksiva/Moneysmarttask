import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.spark.api.java.function.Function;

public class DriverAdaptor implements Function<ConsumerRecord<String, String>, DriverDetailsPojo> {
    DriverDetailsPojo driverDetailsPojo = new DriverDetailsPojo();
    GeoHashCalculation geoHashCalculation = new GeoHashCalculation();

    @Override
    public DriverDetailsPojo call(ConsumerRecord<String, String> record) throws Exception {
        String[] recordArray = record.value().split(",");
        driverDetailsPojo.setDriverId(recordArray[0]);
        driverDetailsPojo.setGeoHash(geoHashCalculation.encode(recordArray[1]));
        driverDetailsPojo.setSourceLocation(recordArray[2]);
        driverDetailsPojo.setDestinationLocation(recordArray[3]);
        driverDetailsPojo.setTripDuration(recordArray[4]);
        driverDetailsPojo.setDistanceCovered(getDistance(recordArray[2], recordArray[3]));

        return driverDetailsPojo;
    }

    public static String getDistance(String sourceLocation, String destinationLocation) {
        double lat1 = Double.parseDouble(sourceLocation.split(":")[0]);
        double lon1 = Double.parseDouble(sourceLocation.split(":")[1]);
        double lat2 = Double.parseDouble(destinationLocation.split(":")[0]);
        double lon2 = Double.parseDouble(destinationLocation.split(":")[1]);
        if ((lat1 == lat2) && (lon1 == lon2)) {
            return String.valueOf(0);
        } else {
            double theta = lon1 - lon2;
            double dist = Math.sin(Math.toRadians(lat1)) * Math.sin(Math.toRadians(lat2)) + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) * Math.cos(Math.toRadians(theta));
            dist = Math.acos(dist);
            dist = Math.toDegrees(dist);
            dist = dist * 60 * 1.1515;
            dist = dist * 1.609344;

            return String.valueOf(Math.round(dist));
        }
    }
}
