package ar.edu.unnoba.pdyc2026.notification.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableRabbit
public class RabbitConfig {

    public static final String EVENT_STATE_EXCHANGE = "event.state.exchange";
    public static final String EVENT_STATE_QUEUE = "event.state.queue";
    public static final String EVENT_STATE_ROUTING_KEY = "event.state.changed";

    @Bean
    public TopicExchange eventStateExchange() {
        return new TopicExchange(EVENT_STATE_EXCHANGE);
    }

    @Bean
    public Queue eventStateQueue() {
        return new Queue(EVENT_STATE_QUEUE, true);
    }

    @Bean
    public Binding eventStateBinding(Queue eventStateQueue, TopicExchange eventStateExchange) {
        return BindingBuilder.bind(eventStateQueue)
                .to(eventStateExchange)
                .with(EVENT_STATE_ROUTING_KEY);
    }

    @Bean
    public MessageConverter jacksonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
