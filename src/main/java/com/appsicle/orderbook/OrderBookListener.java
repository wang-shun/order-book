package com.appsicle.orderbook;

@FunctionalInterface
public interface OrderBookListener {
    void onLevel(long level, long bid, long bidSize, long ask, long askSize);
}
