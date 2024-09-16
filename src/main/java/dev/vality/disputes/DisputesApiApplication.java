package dev.vality.disputes;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.servlet.ServletComponentScan;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@ServletComponentScan
@SpringBootApplication(scanBasePackages = {"dev.vality.disputes", "dev.vality.swag"})
public class DisputesApiApplication extends SpringApplication {

    public static void main(String[] args) {
        SpringApplication.run(DisputesApiApplication.class, args);
    }

}
