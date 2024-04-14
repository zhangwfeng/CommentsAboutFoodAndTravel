package com.hmdp.config;

import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @Author zwf
 * @date 2024/3/30 11:58
 */
@Configuration
public class MQconfig {

    @Bean
    public MessageConverter jsonMessageConverteronverter(){
        return new Jackson2JsonMessageConverter();
    }
}
