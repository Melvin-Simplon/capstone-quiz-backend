package com.alderichoarau.azurequiz.config;

import com.alderichoarau.azurequiz.dto.CertificationSummaryDto;
import com.alderichoarau.azurequiz.dto.ModuleSummaryDto;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.type.CollectionType;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.autoconfigure.cache.RedisCacheManagerBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;

@Configuration
public class CacheConfig {
    @Bean
    public RedisCacheManagerBuilderCustomizer jsonCacheCustomizer() {
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();

        RedisCacheConfiguration defaults =
                RedisCacheConfiguration.defaultCacheConfig()
                        .entryTtl(Duration.ofMinutes(30))
                        .serializeValuesWith(
                                RedisSerializationContext.SerializationPair.fromSerializer(
                                        new GenericJackson2JsonRedisSerializer(mapper)));

        return builder ->
                builder.cacheDefaults(defaults)
                        .withCacheConfiguration(
                                "certifications", typedListConfig(mapper, CertificationSummaryDto.class))
                        .withCacheConfiguration("modules", typedListConfig(mapper, ModuleSummaryDto.class));
    }

    private RedisCacheConfiguration typedListConfig(ObjectMapper mapper, Class<?> elementType) {
        CollectionType listType =
                mapper.getTypeFactory().constructCollectionType(ArrayList.class, elementType);
        Jackson2JsonRedisSerializer<List<?>> serializer =
                new Jackson2JsonRedisSerializer<>(mapper, listType);

        return RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(30))
                .serializeValuesWith(
                        RedisSerializationContext.SerializationPair.fromSerializer(serializer));
    }
}
