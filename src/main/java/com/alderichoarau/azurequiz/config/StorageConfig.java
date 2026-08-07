package com.alderichoarau.azurequiz.config;

import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobServiceClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The {@code BlobServiceClient} bean itself is auto-configured by {@code
 * spring-cloud-azure-starter-storage-blob} from {@code spring.cloud.azure.storage.blob.*}
 * (application.yml) -- account-name + this Web App's managed identity in prod, a plain
 * connection-string against local Azurite in dev. This class only narrows that down to the one
 * container the backend actually uses.
 */
@Configuration
public class StorageConfig {

    @Value("${app.storage.container-name}")
    private String containerName;

    @Bean
    public BlobContainerClient resultsContainerClient(BlobServiceClient blobServiceClient) {
        BlobContainerClient client = blobServiceClient.getBlobContainerClient(containerName);
        // In prod this container already exists (Terraform's storage.tf) -- createIfNotExists
        // is then a fast no-op. Locally, against a fresh Azurite instance, this is what actually
        // creates it, so there's no manual setup step either way.
        client.createIfNotExists();
        return client;
    }
}
