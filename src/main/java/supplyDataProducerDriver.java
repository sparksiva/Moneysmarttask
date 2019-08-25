import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.LongSerializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.Properties;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

public class supplyDataProducerDriver {
    public static void main(String[] args) {
supplyDataProducerDriver supplyDataProducerDriver=new supplyDataProducerDriver();
        try {
            supplyDataProducerDriver.supplyProducer();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            e.printStackTrace();
        } catch (ExecutionException e) {
            e.printStackTrace();
        } catch (TimeoutException e) {
            e.printStackTrace();
        }
    }

    public void supplyProducer()  throws IOException, ExecutionException, InterruptedException, TimeoutException {
        Properties prop=new Properties();
        prop.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG,"localhost:9092");
        prop.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, LongSerializer.class);
        prop.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        prop.put(ProducerConfig.CLIENT_ID_CONFIG,"SupplyProducer");
        prop.put(ProducerConfig.MAX_REQUEST_SIZE_CONFIG,5000000);
        //prop.put("security.protocol","SASL_PLAINTEXT");
        //prop.put("sasl.kerberos.service.name","kafka");
        KafkaProducer kafkaProducer = new KafkaProducer(prop);
        String topic = "supplydata";
        BufferedReader br = null;
        String message;
        br = new BufferedReader(new FileReader("C:/Users/sivae/OneDrive/Desktop/siva'snotes/LocalMode/InputFiles/SupplyInput.txt"));

        message = br.readLine();
        while (message != null) {
            ProducerRecord producerRecord = new ProducerRecord(topic, message);
            kafkaProducer.send(producerRecord).get();
            message = br.readLine();
        }


    }

}
