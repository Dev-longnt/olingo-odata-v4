package com.example;

import com.example.util.ODataQueryBuilder; // Added this line
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

@Configuration
public class AppConfig {

    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }

    @Bean
    public ODataQueryBuilder oDataQueryBuilder() {
        return new ODataQueryBuilder();
    }
}
