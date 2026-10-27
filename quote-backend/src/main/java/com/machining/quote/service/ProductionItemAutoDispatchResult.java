package com.machining.quote.service;

public record ProductionItemAutoDispatchResult(
        Integer total,
        Integer success,
        Integer failed,
        Integer skipped,
        Integer rounds,
        Long durationMs) {
}
