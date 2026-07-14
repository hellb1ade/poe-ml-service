package com.hellblade.poepricer.collector.trade.client;

import java.net.http.HttpClient;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import com.hellblade.poepricer.collector.config.TradeDatasetProperties;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "poe.trade-dataset", name = "enabled", havingValue = "true")
class TradeDatasetHttpConfiguration {

    @Bean
    @Qualifier("tradeDatasetRestClient")
    RestClient tradeDatasetRestClient(TradeDatasetProperties properties) {
        properties.validate();
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(properties.connectTimeout())
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        JdkClientHttpRequestFactory requestFactory =
                new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(properties.readTimeout());

        return RestClient.builder()
                .requestFactory(requestFactory)
                .baseUrl(properties.baseUrl().toString())
                .defaultHeader(HttpHeaders.ACCEPT, "application/json")
                .defaultHeader(HttpHeaders.USER_AGENT, properties.userAgent())
                .requestInterceptor(new TradeRateLimitLoggingInterceptor())
                .build();
    }
}
