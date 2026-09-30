package com.discgolfbagtips.api;

import com.discgolfbagtips.api.config.BagTipsProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableAsync
@EnableScheduling
@EnableConfigurationProperties(BagTipsProperties.class)
public class DiscGolfBagTipsApplication {

    public static void main(String[] args) {
        SpringApplication.run(DiscGolfBagTipsApplication.class, args);
    }
}
