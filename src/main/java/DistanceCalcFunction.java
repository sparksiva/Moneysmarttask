import java.util.*;
import java.lang.*;
import java.io.*;

public class DistanceCalcFunction implements Serializable{

    public static void main(String[] args) {
String dd="driver1,1.01069:104.180,1.01069:102.180,1.01069:104.180,60";
        String[] recordArray=dd.split(",");
        System.out.println("distance is: "+distance(recordArray[2],recordArray[3]));

    }
    public static String distance(String sourceLocation, String destinationLocation) {
        double lat1=Double.parseDouble(sourceLocation.split(":")[0]);
        double lon1=Double.parseDouble(sourceLocation.split(":")[1]);
        double lat2=Double.parseDouble(destinationLocation.split(":")[0]);
        double lon2=Double.parseDouble(destinationLocation.split(":")[1]);
        if ((lat1 == lat2) && (lon1 == lon2)) {
            return String.valueOf(0);
        } else {
            double theta = lon1 - lon2;
            double dist = Math.sin(Math.toRadians(lat1)) * Math.sin(Math.toRadians(lat2)) + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) * Math.cos(Math.toRadians(theta));
            dist = Math.acos(dist);
            dist = Math.toDegrees(dist);
            dist = dist * 60 * 1.1515;
            dist = dist * 1.609344;

            return String.valueOf(dist);
        }
    }
}
