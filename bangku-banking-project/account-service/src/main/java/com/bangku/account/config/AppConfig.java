package com.bangku.account.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;

@Configuration
public class AppConfig {

    public static final String EXCHANGE_BANGKU      = "bangku.events";
    public static final String QUEUE_ACCOUNT_EVENTS = "bangku.account.events";
    public static final String ROUTING_ACCOUNT      = "account.*";

    @Bean
    public TopicExchange bangkuExchange() {
        return new TopicExchange(EXCHANGE_BANGKU, true, false);
    }

    @Bean
    public Queue accountEventQueue() {
        return QueueBuilder.durable(QUEUE_ACCOUNT_EVENTS).build();
    }

    @Bean
    public Binding accountQueueBinding(Queue accountEventQueue, TopicExchange bangkuExchange) {
        return BindingBuilder
                .bind(accountEventQueue)
                .to(bangkuExchange)
                .with(ROUTING_ACCOUNT);
    }

    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter(objectMapper());
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory cf) {
        RabbitTemplate template = new RabbitTemplate(cf);
        template.setMessageConverter(jsonMessageConverter());
        return template;
    }

    @Bean
    @Primary
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        return mapper;
    }

    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory rcf) {
        RedisTemplate<String, Object> t = new RedisTemplate<>();
        t.setConnectionFactory(rcf);
        t.setKeySerializer(new StringRedisSerializer());
        t.setValueSerializer(new GenericJackson2JsonRedisSerializer(objectMapper()));
        return t;
    }

    @Bean
    public RedisCacheManager cacheManager(RedisConnectionFactory rcf) {
        RedisCacheConfiguration config = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(10))
                .serializeKeysWith(
                        RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(
                        RedisSerializationContext.SerializationPair.fromSerializer(
                                new GenericJackson2JsonRedisSerializer(objectMapper())));

        return RedisCacheManager.builder(rcf).cacheDefaults(config).build();
    }
}
