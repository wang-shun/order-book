package com.appsicle.orderbook;

import com.appsicle.orderbook.model.*;
import com.questdb.std.Unsafe;

import java.io.Closeable;

public class OrderBook implements Closeable {
    private final ExecutionReportHandler onExecution;
    private final long executionReport;
    private long maxBid;
    private long minAsk;
    private long pricePoints;
    private long minPrice;
    private long maxPrice;
    private long maxOrders;
    private long orderBook;
    private long orderID;
    private long pricePointMemSize;
    private long orderBookMemSize;
    private long bidLevelCount;
    private long askLevelCount;

    public OrderBook(long minPrice, long maxPrice, long maxOrders, ExecutionReportHandler onExecution) {
        this.minPrice = minPrice;
        this.maxPrice = maxPrice;
        this.pricePointMemSize = (maxPrice - minPrice + 1) * PricePointEntry.SIZE   ;
        this.pricePoints = Unsafe.malloc(this.pricePointMemSize);
        this.orderBookMemSize = maxOrders * OrderBookEntry.SIZE;
        Unsafe.getUnsafe().setMemory(this.pricePoints, this.pricePointMemSize, (byte) 0);
        this.minAsk = maxPrice + 1;
        this.maxBid = minPrice - 1;
        this.orderID = 0;
        this.orderBook = Unsafe.malloc(orderBookMemSize);
        Unsafe.getUnsafe().setMemory(this.orderBook, this.orderBookMemSize, (byte) 0);
        this.onExecution = onExecution;
        this.executionReport = Unsafe.malloc(ExecutionReport.SIZE);
        this.maxOrders = maxOrders;
    }

    @Override
    public void close() {
        Unsafe.free(orderBook, orderBookMemSize);
        Unsafe.free(pricePoints, pricePointMemSize);
        Unsafe.free(executionReport, ExecutionReport.SIZE);
    }

    private long getPricePointEntry(long price) {
        return pricePoints + (price - minPrice) * PricePointEntry.SIZE;
    }

    private long getOrderID(long orderBookEntry) {
        return (orderBookEntry - orderBook) / OrderBookEntry.SIZE;
    }

    public long limitOrder(long order) {

        assert orderID < maxOrders;

        long price = Order.getPrice(order);
        if (price < minPrice || price > maxPrice) {
            resizePricePoints(price);
        }

        long orderSize = Order.getSize(order);

        if (Order.getSide(order) == OrderSides.BUY) {
            while (orderSize > 0 && price >= minAsk) {
                long pricePointEntry = getPricePointEntry(minAsk);
                long ppSize = PricePointEntry.getSize(pricePointEntry);
                if (ppSize > 0) {

                    if (ppSize > orderSize) {
                        return executeAtPricePoint(orderSize, pricePointEntry, ppSize, OrderSides.SELL, OrderSides.BUY);
                    }
                    // price point entry is the same as order size or smaller
                    // we can execute trades on all orders at this price point
                    orderSize -= ppSize;
                    executeAllAtPricePoint(pricePointEntry, OrderSides.SELL, OrderSides.BUY);
                    askLevelCount--;
                }
                minAsk++;
            }

            if (orderSize > 0) {
                if (insertOrder(price, orderSize)) {
                    bidLevelCount++;
                }

                if (maxBid < price) {
                    maxBid = price;
                }
            }

            return orderID++;
        }

        if (Order.getSide(order) == OrderSides.SELL) {
            while (price <= maxBid) {
                long pricePointEntry = getPricePointEntry(maxBid);
                long ppSize = PricePointEntry.getSize(pricePointEntry);
                if (ppSize > 0) {
                    if (ppSize > orderSize) {
                        return executeAtPricePoint(orderSize, pricePointEntry, ppSize, OrderSides.BUY, OrderSides.SELL);
                    }
                    orderSize -= ppSize;
                    executeAllAtPricePoint(pricePointEntry, OrderSides.BUY, OrderSides.SELL);
                    bidLevelCount--;
                }
                maxBid--;
            }

            if (orderSize > 0 && insertOrder(price, orderSize)) {
                askLevelCount++;
            }
            if (minAsk > price) {
                minAsk = price;
            }

            return orderID++;
        }

        return -1;
    }

    private void resizePricePoints(long price) {
        if (price < minPrice) {
            long size = (maxPrice - price + 1) * PricePointEntry.SIZE;
            long mem = Unsafe.malloc(size);
            Unsafe.getUnsafe().copyMemory(pricePoints, mem + (minPrice - price) * PricePointEntry.SIZE, pricePointMemSize);
            Unsafe.getUnsafe().setMemory(mem, (minPrice - price) * PricePointEntry.SIZE, (byte) 0);
            Unsafe.free(pricePoints, pricePointMemSize);
            pricePointMemSize = size;
            pricePoints = mem;
            minPrice = price;
        } else {
            // price > maxPrice
            long size = (price - minPrice + 1) * PricePointEntry.SIZE;
            long mem = Unsafe.malloc(size);
            Unsafe.getUnsafe().copyMemory(pricePoints, mem, pricePointMemSize);
            Unsafe.getUnsafe().setMemory(mem + pricePointMemSize, (price - maxPrice) * PricePointEntry.SIZE, (byte) 0);
            Unsafe.free(pricePoints, pricePointMemSize);
            pricePointMemSize = size;
            pricePoints = mem;
            maxPrice = price;
        }
    }

    private void executeAllAtPricePoint(long pricePointEntry, byte side1, byte side2) {
        long orderBookEntry = PricePointEntry.getOrderListHead(pricePointEntry);
        while (orderBookEntry > 0) {
            final long orderBookEntrySize = OrderBookEntry.getSize(orderBookEntry);
            executeTrade(getOrderID(orderBookEntry), side1, orderID, side2, orderBookEntrySize);
            orderBookEntry = OrderBookEntry.getNext(orderBookEntry);
        }
        PricePointEntry.setSize(pricePointEntry, 0);
        PricePointEntry.setOrderListHead(pricePointEntry, 0);
    }

    private long executeAtPricePoint(long orderSize, long pricePointEntry, long ppSize, byte side1, byte side2) {
        // no need to store this order
        // it can be fully crossed with existing SELL orders
        //
        // we will set new order head to the last partially matched order
        PricePointEntry.setSize(pricePointEntry, ppSize - orderSize);
        long orderBookEntry = PricePointEntry.getOrderListHead(pricePointEntry);
        while (orderSize > 0) {
            long orderBookEntrySize = OrderBookEntry.getSize(orderBookEntry);
            if (orderBookEntrySize > orderSize) {
                executeTrade(getOrderID(orderBookEntry), side1, orderID, side2, orderSize);
                OrderBookEntry.setSize(orderBookEntry, orderBookEntrySize - orderSize);
                PricePointEntry.setOrderListHead(pricePointEntry, orderBookEntry);
                break;
            } else {
                executeTrade(getOrderID(orderBookEntry), side1, orderID, side2, orderBookEntrySize);
                orderSize -= orderBookEntrySize;
                orderBookEntry = OrderBookEntry.getNext(orderBookEntry);
            }
        }
        return orderID++;
    }


    private void executeTrade(long sellOrderID, byte side1, long buyOrderID, byte side2, long orderSize) {
        ExecutionReport.setOrderID(executionReport, sellOrderID);
        ExecutionReport.setOrderSize(executionReport, orderSize);
        ExecutionReport.setOrderSide(executionReport, side1);
        execute();

        ExecutionReport.setOrderID(executionReport, buyOrderID);
        ExecutionReport.setOrderSide(executionReport, side2);
        // order size will propagate from previous set
        execute();
    }

    private void execute() {
        onExecution.onExecution(executionReport);
    }

    private boolean insertOrder(long price, long orderSize) {
        long orderBookEntry = orderBook + (orderID * OrderBookEntry.SIZE);
        long pricePointEntry = getPricePointEntry(price);

        OrderBookEntry.setSize(orderBookEntry, orderSize);
        PricePointEntry.setSize(pricePointEntry, PricePointEntry.getSize(pricePointEntry) + orderSize);
        if (PricePointEntry.getOrderListHead(pricePointEntry) == 0) {
            PricePointEntry.setOrderListHead(pricePointEntry, orderBookEntry);
            PricePointEntry.setOrderListTail(pricePointEntry, orderBookEntry);
            return true;
        }
        OrderBookEntry.setNext(PricePointEntry.getOrderListTail(pricePointEntry), orderBookEntry);
        return false;
    }

    /**
     * Finds entry address for the required order book ASK level. Level attributes
     * can be requested using the return value of this method. Retrieving first attribute
     * incurs search penalty but subsequent attributes will be read with O(1) complexity.
     *
     * @param level 0-based level. This value has to be below bidLevelCount.
     * @return address of order book entry at given level.
     */
    public long getEntryAtBidLevel(long level) {
        if (level < bidLevelCount) {
            long levelsRemaining = level;
            long bid = maxBid;

            do {
                assert bid < maxPrice;
                long pricePointEntry = getPricePointEntry(bid);
                long ppSize = PricePointEntry.getSize(pricePointEntry);
                if (ppSize > 0) {
                    if (levelsRemaining < 1) {
                        return pricePointEntry;
                    }
                    levelsRemaining--;
                }
                bid--;
            } while (true);
        }
        return -1;
    }

    public long getEntryAtAskLevel(long level) {
        if (level < askLevelCount) {
            long levelsRemaining = level;
            long ask = minAsk;
            do {
                assert ask > minPrice;

                long pricePointEntry = getPricePointEntry(ask);
                long ppSize = PricePointEntry.getSize(pricePointEntry);
                if (ppSize > 0) {
                    if (levelsRemaining < 1) {
                        return pricePointEntry;
                    }
                    levelsRemaining--;
                }
                ask++;
            } while (true);
        }
        return -1;
    }

    public long getPriceAtEntry(long pricePointEntry) {
        assert pricePointEntry != -1;
        return (pricePointEntry - pricePoints) / PricePointEntry.SIZE + minPrice;
    }

    public void getOrderBook(OrderBookListener listener) {
        long levelsRemaining = Math.max(bidLevelCount, askLevelCount);
        long level = 0;
        long bid = maxBid;
        long ask = minAsk;
        while (level < levelsRemaining) {

            assert ask >= minPrice;
            assert bid <= maxPrice;

            long bidEntry;
            long bidSize;
            long bidPrice;
            long askEntry;
            long askSize;
            long askPrice;

            if (level < bidLevelCount) {
                do {
                    bidEntry = getPricePointEntry(bid);
                    bidSize = PricePointEntry.getSize(bidEntry);

                    if (bidSize == 0 && level < bidLevelCount) {
                        bid--;
                    } else {
                        break;
                    }
                } while (true);
                bidPrice = getPriceAtEntry(bidEntry);
            } else {
                bidSize = 0;
                bidPrice = 0;
            }


            if (level < askLevelCount) {
                do {
                    askEntry = getPricePointEntry(ask);
                    askSize = PricePointEntry.getSize(askEntry);

                    if (askSize == 0 && level < askLevelCount) {
                        ask++;
                    } else {
                        break;
                    }
                } while (true);
                askPrice = getPriceAtEntry(askEntry);
            } else {
                askSize = 0;
                askPrice = 0;
            }
            listener.onLevel(level, bidPrice, bidSize, askPrice, askSize);

            bid--;
            ask++;
            level++;
        }
    }

    public long getBidLevelCount() {
        return bidLevelCount;
    }

    public long getAskLevelCount() {
        return askLevelCount;
    }
}
