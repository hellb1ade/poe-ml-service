package com.hellblade.poepricer.collector.trade.model;

import java.util.UUID;

public record ExistingTradeDatasetRun(UUID id, String status, int storedItems) {
}
