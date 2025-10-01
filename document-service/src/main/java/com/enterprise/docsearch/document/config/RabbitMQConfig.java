package com.enterprise.docsearch.document.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {
    
    public static final String EXCHANGE = "document.topic";
    public static final String INDEX_QUEUE = "indexing.queue";
    public static final String DELETE_QUEUE = "deletion.queue";
    public static final String INDEX_DLQ = "indexing.dlq";
    public static final String DELETE_DLQ = "deletion.dlq";
    
    public static final String INDEX_ROUTING_KEY = "document.index";
    public static final String DELETE_ROUTING_KEY = "document.delete";
    
    @Bean
    public TopicExchange exchange() {
        return new TopicExchange(EXCHANGE, true, false);
    }
    
    @Bean
    public Queue indexQueue() {
        return QueueBuilder.durable(INDEX_QUEUE)
                .withArgument("x-dead-letter-exchange", "")
                .withArgument("x-dead-letter-routing-key", INDEX_DLQ)
                .build();
    }
    
    @Bean
    public Queue deleteQueue() {
        return QueueBuilder.durable(DELETE_QUEUE)
                .withArgument("x-dead-letter-exchange", "")
                .withArgument("x-dead-letter-routing-key", DELETE_DLQ)
                .build();
    }
    
    @Bean
    public Queue indexDLQ() {
        return QueueBuilder.durable(INDEX_DLQ).build();
    }
    
    @Bean
    public Queue deleteDLQ() {
        return QueueBuilder.durable(DELETE_DLQ).build();
    }
    
    @Bean
    public Binding indexBinding(Queue indexQueue, TopicExchange exchange) {
        return BindingBuilder.bind(indexQueue)
                .to(exchange)
                .with(INDEX_ROUTING_KEY);
    }
    
    @Bean
    public Binding deleteBinding(Queue deleteQueue, TopicExchange exchange) {
        return BindingBuilder.bind(deleteQueue)
                .to(exchange)
                .with(DELETE_ROUTING_KEY);
    }
    
    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
    
    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(jsonMessageConverter());
        return template;
    }
}
