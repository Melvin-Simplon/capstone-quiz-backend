package com.alderichoarau.azurequiz;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;

@EnableCaching
@SpringBootApplication
public class AzureQuizBackendApplication {
	public static void main(String[] args) {
		SpringApplication.run(AzureQuizBackendApplication.class, args);
	}
}
