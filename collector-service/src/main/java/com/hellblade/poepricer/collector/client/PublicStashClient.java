package com.hellblade.poepricer.collector.client;

import com.hellblade.poepricer.collector.client.dto.PublicStashResponse;

public interface PublicStashClient {

    PublicStashResponse getNextPage(String nextChangeId);
}
