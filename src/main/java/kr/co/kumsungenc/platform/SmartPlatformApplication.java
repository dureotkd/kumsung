package kr.co.kumsungenc.platform;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class SmartPlatformApplication {
    public static void main(String[] args) {
        SpringApplication.run(SmartPlatformApplication.class, args);
    }
}
