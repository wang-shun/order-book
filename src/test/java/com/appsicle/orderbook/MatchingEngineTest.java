package com.appsicle.orderbook;

import com.appsicle.orderbook.model.ExecutionReport;
import com.appsicle.orderbook.model.Order;
import com.appsicle.orderbook.model.OrderSides;
import com.appsicle.orderbook.model.PricePointEntry;
import com.questdb.std.Unsafe;
import com.questdb.std.str.StringSink;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class MatchingEngineTest {

    private static final StringSink sink = new StringSink();

    private static void captureExecutionReport(long executionReport) {
        sink.put(ExecutionReport.getOrderID(executionReport)).put(',');
        sink.put(ExecutionReport.getOrderSize(executionReport)).put(',');
        byte side = ExecutionReport.getOrderSide(executionReport);
        if (side == OrderSides.BUY) {
            sink.put("BUY\n");
        } else if (side == OrderSides.SELL) {
            sink.put("SELL\n");
        } else {
            sink.put("-\n");
        }
    }

    private static void printOrderBook(long level, long bid, long bidSize, long ask, long askSize) {
        sink.put(level).put(',').put(bid).put(',').put(bidSize).put(',').put(ask).put(',').put(askSize).put('\n');
    }

    @Before
    public void setUp() {
        sink.clear();
    }

    @Test
    public void testMatchSellOverAtPricePoint() {
        long expectedMem = Unsafe.getMemUsed();
        try (MatchingEngine me = new MatchingEngine(100_00, 200_00, 1000_000, MatchingEngineTest::captureExecutionReport)) {

            final String expectedReports = "0,30,BUY\n" +
                    "2,30,SELL\n" +
                    "1,30,BUY\n" +
                    "2,30,SELL\n";


            long order = Unsafe.malloc(Order.SIZE);
            try {
                Order.setSize(order, 30);
                Order.setSide(order, OrderSides.BUY);
                Order.setPrice(order, 110_12);

                Assert.assertEquals(0, me.limitOrder(order));
                Assert.assertEquals(1, me.limitOrder(order));

                Assert.assertEquals(1, me.getBidLevelCount());

                long pricePointEntry = me.getEntryAtBidLevel(0);
                Assert.assertNotEquals(-1, pricePointEntry);
                Assert.assertEquals(110_12, me.getPriceAtEntry(pricePointEntry));
                Assert.assertEquals(60, PricePointEntry.getSize(pricePointEntry));

                Assert.assertEquals(-1, me.getEntryAtBidLevel(2));

                Order.setSize(order, 100);
                Order.setSide(order, OrderSides.SELL);
                Order.setPrice(order, 110_11);
                Assert.assertEquals(2, me.limitOrder(order));

                Assert.assertEquals(1, me.getAskLevelCount());
                Assert.assertEquals(expectedReports, sink.toString());

                sink.clear();
                me.getOrderBook(MatchingEngineTest::printOrderBook);
                Assert.assertEquals("0,11010,0,11011,40\n", sink.toString());

            } finally {
                Unsafe.free(order, Order.SIZE);
            }
        }

        Assert.assertEquals(expectedMem, Unsafe.getMemUsed());
    }

    @Test
    public void testMatchSellUnderAtPricePoint() {
        long expectedMem = Unsafe.getMemUsed();
        try (MatchingEngine me = new MatchingEngine(100_00, 200_00, 1000_000, MatchingEngineTest::captureExecutionReport)) {

            final String expectedReports = "0,150,BUY\n" +
                    "2,150,SELL\n" +
                    "1,50,BUY\n" +
                    "2,50,SELL\n";


            long order = Unsafe.malloc(Order.SIZE);
            try {
                Order.setSize(order, 150);
                Order.setSide(order, OrderSides.BUY);
                Order.setPrice(order, 110_12);
                Assert.assertEquals(0, me.limitOrder(order));

                Order.setSize(order, 180);
                Order.setSide(order, OrderSides.BUY);
                Order.setPrice(order, 110_12);
                Assert.assertEquals(1, me.limitOrder(order));

                me.getOrderBook(MatchingEngineTest::printOrderBook);
                Assert.assertEquals("0,11012,330,0,0\n", sink.toString());

                sink.clear();
                Order.setSize(order, 200);
                Order.setSide(order, OrderSides.SELL);
                Order.setPrice(order, 110_11);
                Assert.assertEquals(2, me.limitOrder(order));

                Assert.assertEquals(0, me.getAskLevelCount());
                Assert.assertEquals(expectedReports, sink.toString());

                sink.clear();
                me.getOrderBook(MatchingEngineTest::printOrderBook);
                Assert.assertEquals("0,11012,130,0,0\n", sink.toString());
            } finally {
                Unsafe.free(order, Order.SIZE);
            }
        }

        Assert.assertEquals(expectedMem, Unsafe.getMemUsed());
    }

    @Test
    public void testMatchSellExactAtPricePoint() {
        long expectedMem = Unsafe.getMemUsed();
        try (MatchingEngine me = new MatchingEngine(100_00, 200_00, 1000_000, MatchingEngineTest::captureExecutionReport)) {

            final String expectedReports = "0,150,BUY\n" +
                    "2,150,SELL\n" +
                    "1,180,BUY\n" +
                    "2,180,SELL\n";


            long order = Unsafe.malloc(Order.SIZE);
            try {
                Order.setSize(order, 150);
                Order.setSide(order, OrderSides.BUY);
                Order.setPrice(order, 110_12);
                Assert.assertEquals(0, me.limitOrder(order));

                Order.setSize(order, 180);
                Order.setSide(order, OrderSides.BUY);
                Order.setPrice(order, 110_12);
                Assert.assertEquals(1, me.limitOrder(order));

                me.getOrderBook(MatchingEngineTest::printOrderBook);
                Assert.assertEquals("0,11012,330,0,0\n", sink.toString());

                sink.clear();
                Order.setSize(order, 330);
                Order.setSide(order, OrderSides.SELL);
                Order.setPrice(order, 110_11);
                Assert.assertEquals(2, me.limitOrder(order));

                Assert.assertEquals(0, me.getAskLevelCount());
                Assert.assertEquals(0, me.getBidLevelCount());
                Assert.assertEquals(expectedReports, sink.toString());

                sink.clear();
                me.getOrderBook(MatchingEngineTest::printOrderBook);
                Assert.assertEquals("", sink.toString());
            } finally {
                Unsafe.free(order, Order.SIZE);
            }
        }

        Assert.assertEquals(expectedMem, Unsafe.getMemUsed());
    }

    @Test
    public void testLevelAccess() {
        long expectedMem = Unsafe.getMemUsed();
        try (MatchingEngine me = new MatchingEngine(100_00, 200_00, 1000_000, MatchingEngineTest::captureExecutionReport)) {

            final String expectedReports = "";


            long order = Unsafe.malloc(Order.SIZE);
            try {
                Order.setSize(order, 10_000);
                Order.setSide(order, OrderSides.BUY);
                Order.setPrice(order, 110_12);
                Assert.assertEquals(0, me.limitOrder(order));

                Order.setSize(order, 20000);
                Order.setSide(order, OrderSides.BUY);
                Order.setPrice(order, 110_10);
                Assert.assertEquals(1, me.limitOrder(order));


                Order.setSize(order, 10_000);
                Order.setSide(order, OrderSides.SELL);
                Order.setPrice(order, 110_16);
                Assert.assertEquals(2, me.limitOrder(order));

                Order.setSize(order, 100_000);
                Order.setSide(order, OrderSides.SELL);
                Order.setPrice(order, 110_18);
                Assert.assertEquals(3, me.limitOrder(order));

                Order.setSize(order, 1_000_000);
                Order.setSide(order, OrderSides.SELL);
                Order.setPrice(order, 110_19);
                Assert.assertEquals(4, me.limitOrder(order));


                // bid and ask levels
                Assert.assertEquals(2, me.getBidLevelCount());
                Assert.assertEquals(3, me.getAskLevelCount());

                // prices and sizes at expected levels
                Assert.assertEquals(110_12, me.getPriceAtEntry(me.getEntryAtBidLevel(0)));
                Assert.assertEquals(10_000, PricePointEntry.getSize(me.getEntryAtBidLevel(0)));

                Assert.assertEquals(110_16, me.getPriceAtEntry(me.getEntryAtAskLevel(0)));
                Assert.assertEquals(10_000, PricePointEntry.getSize(me.getEntryAtAskLevel(0)));

                Assert.assertEquals(110_10, me.getPriceAtEntry(me.getEntryAtBidLevel(1)));
                Assert.assertEquals(20_000, PricePointEntry.getSize(me.getEntryAtBidLevel(1)));

                Assert.assertEquals(110_18, me.getPriceAtEntry(me.getEntryAtAskLevel(1)));
                Assert.assertEquals(100_000, PricePointEntry.getSize(me.getEntryAtAskLevel(1)));

                Assert.assertEquals(-1, me.getEntryAtBidLevel(2));
                Assert.assertEquals(110_19, me.getPriceAtEntry(me.getEntryAtAskLevel(2)));
                Assert.assertEquals(1_000_000, PricePointEntry.getSize(me.getEntryAtAskLevel(2)));

                Assert.assertEquals(expectedReports, sink.toString());

                sink.clear();
                me.getOrderBook(MatchingEngineTest::printOrderBook);

                Assert.assertEquals("0,11012,10000,11016,10000\n" +
                        "1,11010,20000,11018,100000\n" +
                        "2,11009,0,11019,1000000\n", sink.toString());
            } finally {
                Unsafe.free(order, Order.SIZE);
            }

        }

        Assert.assertEquals(expectedMem, Unsafe.getMemUsed());
    }

    @Test
    public void testOnlyBids() {
        long expectedMem = Unsafe.getMemUsed();
        try (MatchingEngine me = new MatchingEngine(100_00, 200_00, 1000_000, MatchingEngineTest::captureExecutionReport)) {
            long order = Unsafe.malloc(Order.SIZE);
            try {
                Order.setSize(order, 10_000);
                Order.setSide(order, OrderSides.BUY);
                Order.setPrice(order, 110_12);
                Assert.assertEquals(0, me.limitOrder(order));

                Order.setSize(order, 20000);
                Order.setSide(order, OrderSides.BUY);
                Order.setPrice(order, 110_10);
                Assert.assertEquals(1, me.limitOrder(order));

                sink.clear();
                me.getOrderBook(MatchingEngineTest::printOrderBook);

                Assert.assertEquals("0,11012,10000,0,0\n" +
                                "1,11010,20000,0,0\n",
                        sink.toString()
                );
            } finally {
                Unsafe.free(order, Order.SIZE);
            }

        }

        Assert.assertEquals(expectedMem, Unsafe.getMemUsed());
    }

    @Test
    public void testOnlyAsks() {
        long expectedMem = Unsafe.getMemUsed();
        try (MatchingEngine me = new MatchingEngine(100_00, 200_00, 1000_000, MatchingEngineTest::captureExecutionReport)) {
            long order = Unsafe.malloc(Order.SIZE);
            try {
                Order.setSize(order, 10_000);
                Order.setSide(order, OrderSides.SELL);
                Order.setPrice(order, 110_12);
                Assert.assertEquals(0, me.limitOrder(order));

                Order.setSize(order, 20_000);
                Order.setSide(order, OrderSides.SELL);
                Order.setPrice(order, 110_14);
                Assert.assertEquals(1, me.limitOrder(order));

                sink.clear();
                me.getOrderBook(MatchingEngineTest::printOrderBook);

                Assert.assertEquals("0,0,0,11012,10000\n" +
                                "1,0,0,11014,20000\n",
                        sink.toString()
                );
            } finally {
                Unsafe.free(order, Order.SIZE);
            }

        }

        Assert.assertEquals(expectedMem, Unsafe.getMemUsed());
    }


}