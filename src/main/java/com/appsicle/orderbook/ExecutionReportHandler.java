package com.appsicle.orderbook;

/**
 * Consumes execution reports emitted by MatchingEngine when orders are crossed.
 * To access execution report attributes use {@link com.appsicle.orderbook.model.ExecutionReport}
 */
@FunctionalInterface
public interface ExecutionReportHandler {
    void onExecution(long executionReport);
}
