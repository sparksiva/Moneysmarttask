import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

public class GeoHashCalculation implements Serializable {

    private static final char[] BASE_32 = {'0', '1', '2', '3', '4', '5', '6',
            '7', '8', '9', 'b', 'c', 'd', 'e', 'f', 'g', 'h', 'j', 'k', 'm', 'n',
            'p', 'q', 'r', 's', 't', 'u', 'v', 'w', 'x', 'y', 'z'};

    private final static Map<Character, Integer> DECODE_MAP = new HashMap();

    private static final int PRECISION = 6;
    private static final int[] BITS = {16, 8, 4, 2, 1};

    static {
        for (int i = 0; i < BASE_32.length; i++) {
            DECODE_MAP.put(Character.valueOf(BASE_32[i]), Integer.valueOf(i));
        }
    }

    public GeoHashCalculation() {
    }

    public static String encode(String latlong) {
        String[] latlongarray=latlong.split(":");
        double latitude=Double.parseDouble(latlongarray[0]);
        double longitude=Double.parseDouble(latlongarray[1]);
        double[] latInterval = {-90.0, 90.0};
        double[] lngInterval = {-180.0, 180.0};

        final StringBuilder geohash = new StringBuilder();
        boolean isEven = true;

        int bit = 0;
        int ch = 0;

        while (geohash.length() < PRECISION) {
            double mid = 0.0;
            if (isEven) {
                mid = (lngInterval[0] + lngInterval[1]) / 2D;
                if (longitude > mid) {
                    ch |= BITS[bit];
                    lngInterval[0] = mid;
                } else {
                    lngInterval[1] = mid;
                }
            } else {
                mid = (latInterval[0] + latInterval[1]) / 2D;
                if (latitude > mid) {
                    ch |= BITS[bit];
                    latInterval[0] = mid;
                } else {
                    latInterval[1] = mid;
                }
            }

            isEven = !isEven;

            if (bit < 4) {
                bit++;
            } else {
                geohash.append(BASE_32[ch]);
                bit = 0;
                ch = 0;
            }
        }

        return geohash.toString();
    }

    }