package com.appsicle.orderbook.model;


import com.questdb.std.Unsafe;

public final class PricePointEntry {

    // power of 2 aligned
    public static final int SIZE = 32;

    public static long getSize(long pricePointEntry) {
        return Unsafe.getUnsafe().getLong(pricePointEntry);
    }

    public static void setSize(long pricePointEntry, long size) {
        Unsafe.getUnsafe().putLong(pricePointEntry, size);
    }

    public static long getOrderListHead(long pricePointEntry) {
        return Unsafe.getUnsafe().getLong(pricePointEntry + 8);
    }

    public static void setOrderListHead(long pricePointEntry, long head) {
        Unsafe.getUnsafe().putLong(pricePointEntry + 8, head);
    }

    public static long getOrderListTail(long pricePointEntry) {
        return Unsafe.getUnsafe().getLong(pricePointEntry + 16);
    }

    public static void setOrderListTail(long pricePointEntry, long head) {
        Unsafe.getUnsafe().putLong(pricePointEntry + 16, head);
    }
}
