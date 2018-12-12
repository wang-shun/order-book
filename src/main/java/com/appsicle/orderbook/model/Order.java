package com.appsicle.orderbook.model;


import com.questdb.std.Unsafe;

public final class Order {
    public static final int SIZE = 32;

    public static byte getSide(long orderAddress) {
        return Unsafe.getUnsafe().getByte(orderAddress);
    }

    public static long getPrice(long orderAddress) {
        return Unsafe.getUnsafe().getLong(orderAddress + 1);
    }

    public static long getSize(long orderAddress) {
        return Unsafe.getUnsafe().getLong(orderAddress + 9);
    }

    public static void setSide(long orderAddress, byte side) {
        Unsafe.getUnsafe().putByte(orderAddress, side);
    }

    public static void setPrice(long orderAddress, long price) {
        Unsafe.getUnsafe().putLong(orderAddress + 1, price);
    }

    public static void setSize(long orderAddress, long size) {
        Unsafe.getUnsafe().putLong(orderAddress + 9, size);
    }
}
