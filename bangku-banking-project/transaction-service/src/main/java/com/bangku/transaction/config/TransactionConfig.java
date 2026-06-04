package com.bangku.transaction.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.web.reactive.function.client.WebClient;
@Configuration
public class TransactionConfig {

    public static final String EXCHANGE               = "bangku.events";
    public static final String QUEUE_TRANSACTIONS     = "bangku.transaction.events";
    public static final String ROUTING_TRANSACTIONS   = "transaction.*";

    @Bean
    public TopicExchange bangkuExchange() {
        return new TopicExchange(EXCHANGE, true, false);
    }

    @Bean
    public Queue transactionEventQueue() {
        return QueueBuilder.durable(QUEUE_TRANSACTIONS).build();
    }

    @Bean
    public Binding transactionQueueBinding(Queue transactionEventQueue, TopicExchange bangkuExchange) {
        return BindingBuilder.bind(transactionEventQueue).to(bangkuExchange).with(ROUTING_TRANSACTIONS);
    }

    @Bean
    @Primary
    public ObjectMapper objectMapper() {
        return new ObjectMapper().registerModule(new JavaTimeModule());
    }

    @Bean
    public Jackson2JsonMessageConverter messageConverter(ObjectMapper objectMapper) {
        return new Jackson2JsonMessageConverter(objectMapper);
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory cf, Jackson2JsonMessageConverter converter) {
        RabbitTemplate t = new RabbitTemplate(cf);
        t.setMessageConverter(converter);
        return t;
    }

    @Bean
    public WebClient.Builder webClientBuilder() {
        return WebClient.builder();
    }
}
