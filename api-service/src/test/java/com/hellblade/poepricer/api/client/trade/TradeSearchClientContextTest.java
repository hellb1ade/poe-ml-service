package com.hellblade.poepricer.api.client.trade;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
        "poe.trade.enabled=true",
        "poe.trade.user-agent=poe-pricer-test/0.1 (contact: test@example.com)",
        "spring.autoconfigure.exclude="
                + "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration,"
                + "org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration,"
                + "org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration"
})
class TradeSearchClientContextTest {

    @Autowired
    private TradeSearchClient tradeSearchClient;

    @Test
    void createsHttpTradeSearchClientWhenEnabled() {
        assertInstanceOf(HttpTradeSearchClient.class, tradeSearchClient);
    }
}
