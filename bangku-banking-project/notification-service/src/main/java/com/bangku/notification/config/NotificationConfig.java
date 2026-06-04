package com.bangku.notification.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
@Configuration
public class NotificationConfig {

    public static final String EXCHANGE                 = "bangku.events";
    public static final String QUEUE_TRANSACTION_EVENTS = "bangku.transaction.events";
    public static final String QUEUE_ACCOUNT_EVENTS     = "bangku.account.events";

    @Bean
    public TopicExchange bangkuExchange() {
        return new TopicExchange(EXCHANGE, true, false);
    }

    @Bean
    public Queue transactionEventQueue() {
        return QueueBuilder.durable(QUEUE_TRANSACTION_EVENTS).build();
    }

    @Bean
    public Queue accountEventQueue() {
        return QueueBuilder.durable(QUEUE_ACCOUNT_EVENTS).build();
    }

    @Bean
    public Binding txBinding(Queue transactionEventQueue, TopicExchange bangkuExchange) {
        return BindingBuilder.bind(transactionEventQueue).to(bangkuExchange).with("transaction.*");
    }

    @Bean
    public Binding acBinding(Queue accountEventQueue, TopicExchange bangkuExchange) {
        return BindingBuilder.bind(accountEventQueue).to(bangkuExchange).with("account.*");
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
}
