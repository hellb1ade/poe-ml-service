package com.hellblade.poepricer.api.client.trade;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(TradeClientProperties.class)
class TradeClientConfiguration {

    @Bean
    @ConditionalOnMissingBean(RestClient.Builder.class)
    @ConditionalOnProperty(prefix = "poe.trade", name = "enabled", havingValue = "true")
    RestClient.Builder restClientBuilder() {
        return RestClient.builder();
    }
}
