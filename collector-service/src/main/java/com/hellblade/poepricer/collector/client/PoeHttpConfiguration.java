package com.hellblade.poepricer.collector.client;

import java.net.http.HttpClient;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import com.hellblade.poepricer.collector.config.PoeApiProperties;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "poe.collector", name = "enabled", havingValue = "true")
class PoeHttpConfiguration {

    @Bean
    PoeRateLimitGate poeRateLimitGate() {
        return new PoeRateLimitGate();
    }

    @Bean
    @Qualifier("poeApiRestClient")
    RestClient poeApiRestClient(PoeApiProperties properties, PoeRateLimitGate rateLimitGate) {
        properties.validate();
        return builder(properties)
                .baseUrl(properties.baseUrl().toString())
                .defaultHeader(HttpHeaders.ACCEPT, "application/json")
                .defaultHeader(HttpHeaders.USER_AGENT, properties.userAgent())
                .requestInterceptor(new PoeRateLimitInterceptor(rateLimitGate))
                .build();
    }

    @Bean
    @Qualifier("poeOAuthRestClient")
    RestClient poeOAuthRestClient(PoeApiProperties properties) {
        properties.validate();
        return builder(properties)
                .baseUrl(properties.oauthBaseUrl().toString())
                .defaultHeader(HttpHeaders.ACCEPT, "application/json")
                .defaultHeader(HttpHeaders.USER_AGENT, properties.userAgent())
                .build();
    }

    private RestClient.Builder builder(PoeApiProperties properties) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(properties.connectTimeout())
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(properties.readTimeout());
        return RestClient.builder().requestFactory(requestFactory);
    }
}
