package com.appsicle.orderbook.model;

import com.questdb.std.Unsafe;

public final class ExecutionReport {
    public static final int SIZE = 32;

    public static long getOrderID(long er) {
        return Unsafe.getUnsafe().getLong(er);
    }

    public static void setOrderID(long er, long orderID) {
        Unsafe.getUnsafe().putLong(er, orderID);
    }

    public static long getOrderSize(long er) {
        return Unsafe.getUnsafe().getLong(er + 8);
    }

    public static void setOrderSize(long er, long orderSize) {
        Unsafe.getUnsafe().putLong(er + 8, orderSize);
    }

    public static byte getOrderSide(long er) {
        return Unsafe.getUnsafe().getByte(er + 16);
    }

    public static void setOrderSide(long er, byte side) {
        Unsafe.getUnsafe().putByte(er + 16, side);
    }
}
