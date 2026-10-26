package th.taxbook;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class TaxbookApplication {
    public static void main(String[] args) {
        SpringApplication.run(TaxbookApplication.class, args);
    }
}
