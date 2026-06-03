package com.kovanlabs.logservice.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kovanlabs.logservice.model.LogDto;

@Configuration
public class RedisConfig {

    @Bean
    public RedisTemplate<String, LogDto> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, LogDto> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        // String serialization for keys
        StringRedisSerializer stringSerializer = new StringRedisSerializer();
        template.setKeySerializer(stringSerializer);
        template.setHashKeySerializer(stringSerializer);

        // Jackson2JsonRedisSerializer for values
        Jackson2JsonRedisSerializer<LogDto> jsonSerializer = new Jackson2JsonRedisSerializer<>(LogDto.class);
        
        template.setValueSerializer(jsonSerializer);
        template.setHashValueSerializer(jsonSerializer);

        template.afterPropertiesSet();
        return template;
    }
}
