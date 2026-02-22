package com.learning;

import com.learning.config.AppProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties({AppProperties.class})
public class AiTextSpeechApplication {

    public static void main(String[] args) {
        SpringApplication.run(AiTextSpeechApplication.class, args);
    }

}
