package com.appsicle.orderbook.model;


import com.questdb.std.Unsafe;

public final class OrderBookEntry {
    // size must be power of 2 aligned
    public static final int SIZE = 16;

    public static long getSize(long orderAddress) {
        return Unsafe.getUnsafe().getLong(orderAddress);
    }

    public static void setSize(long orderAddress, long size) {
        Unsafe.getUnsafe().putLong(orderAddress, size);
    }

    public static long getNext(long orderAddress) {
        return Unsafe.getUnsafe().getLong(orderAddress + 8);
    }

    public static void setNext(long orderAddress, long next) {
        Unsafe.getUnsafe().putLong(orderAddress + 8, next);
    }
}
