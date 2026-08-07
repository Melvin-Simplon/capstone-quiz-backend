package com.alderichoarau.azurequiz;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;

// EnableCaching backs the @Cacheable annotations in CertificationService/ModuleService with
// Redis (spring.cache.type=redis, application.yml) — see redis.tf / app-service.tf in the
// infra repo for how the connection is provisioned.
@EnableCaching
@SpringBootApplication
public class AzureQuizBackendApplication {

	public static void main(String[] args) {
		SpringApplication.run(AzureQuizBackendApplication.class, args);
	}

}
