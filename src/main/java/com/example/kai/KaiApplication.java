package com.example.kai;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan // picks up @ConfigurationProperties records, e.g. config.KaiProperties
public class KaiApplication {

	public static void main(String[] args) {
		SpringApplication.run(KaiApplication.class, args);
	}

}
