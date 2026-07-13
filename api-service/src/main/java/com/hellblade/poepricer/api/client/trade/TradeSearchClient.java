package com.hellblade.poepricer.api.client.trade;

import java.util.List;

import com.hellblade.poepricer.api.client.trade.dto.TradeFetchResponse;
import com.hellblade.poepricer.api.client.trade.dto.TradeSearchRequest;
import com.hellblade.poepricer.api.client.trade.dto.TradeSearchResponse;

public interface TradeSearchClient {

    TradeSearchResponse search(String league, TradeSearchRequest request);

    TradeFetchResponse fetch(String searchId, List<String> listingIds);
}
