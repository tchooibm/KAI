package com.example.kai;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

import com.example.kai.config.KaiConfig;
import com.example.kai.config.KaiProperties;

// Tests don't go through main(), so load ./kai.properties (project root) the same way
@SpringBootTest
class KaiApplicationTests {

	@TestConfiguration
	static class Settings {

		@Bean
		KaiProperties kaiProperties() throws KaiConfig.Invalid {
			return KaiConfig.load(new String[0]).properties();
		}
	}

	@Test
	void contextLoads() {
	}

}
