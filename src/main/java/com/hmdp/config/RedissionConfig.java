package com.hmdp.config;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;

/**
 * @Author zwf
 * @date 2024/3/28 13:38
 */

@Configuration
//@ConfigurationProperties(prefix = "zwf.redisson")
@Data
public class RedissionConfig {

    private String addr="redis://127.0.0.1:6379";
    private String password="123";

    @Bean
    public RedissonClient redissonClient(){

        //配置
        Config config = new Config();
        config.useSingleServer().setAddress(addr).setPassword(password);

        //创建RedissonClient对象
        return Redisson.create(config);
    }
}
