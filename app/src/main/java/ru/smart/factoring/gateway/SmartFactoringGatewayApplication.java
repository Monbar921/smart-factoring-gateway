package ru.smart.factoring.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
public class SmartFactoringGatewayApplication {
    public static void main(String[] args) {
        SpringApplication.run(SmartFactoringGatewayApplication.class, args);
    }
}
