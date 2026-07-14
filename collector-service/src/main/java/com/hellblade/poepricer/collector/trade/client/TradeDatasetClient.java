package com.hellblade.poepricer.collector.trade.client;

import java.util.List;

import com.hellblade.poepricer.collector.trade.client.dto.TradeFetchResponse;
import com.hellblade.poepricer.collector.trade.client.dto.TradeSearchRequest;
import com.hellblade.poepricer.collector.trade.client.dto.TradeSearchResponse;

public interface TradeDatasetClient {

    TradeSearchResponse search(String league, TradeSearchRequest request);

    TradeFetchResponse fetch(String searchId, List<String> listingIds);
}
