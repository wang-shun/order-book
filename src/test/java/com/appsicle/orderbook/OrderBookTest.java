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

public class OrderBookTest {

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
        try (OrderBook orderBook = new OrderBook(100_00, 200_00, 1000_000, OrderBookTest::captureExecutionReport)) {

            final String expectedReports = "0,30,BUY\n" +
                    "2,30,SELL\n" +
                    "1,30,BUY\n" +
                    "2,30,SELL\n";

            long order = Unsafe.malloc(Order.SIZE);
            try {
                Order.setSize(order, 30);
                Order.setSide(order, OrderSides.BUY);
                Order.setPrice(order, 110_12);

                Assert.assertEquals(0, orderBook.limitOrder(order));
                Assert.assertEquals(1, orderBook.limitOrder(order));

                Assert.assertEquals(1, orderBook.getBidLevelCount());
                Assert.assertEquals(0, orderBook.getAskLevelCount());

                long pricePointEntry = orderBook.getEntryAtBidLevel(0);
                Assert.assertNotEquals(-1, pricePointEntry);
                Assert.assertEquals(110_12, orderBook.getPriceAtEntry(pricePointEntry));
                Assert.assertEquals(60, PricePointEntry.getSize(pricePointEntry));

                Assert.assertEquals(-1, orderBook.getEntryAtBidLevel(2));

                Order.setSize(order, 100);
                Order.setSide(order, OrderSides.SELL);
                Order.setPrice(order, 110_11);
                Assert.assertEquals(2, orderBook.limitOrder(order));

                Assert.assertEquals(1, orderBook.getAskLevelCount());
                Assert.assertEquals(expectedReports, sink.toString());

                sink.clear();
                orderBook.getOrderBook(OrderBookTest::printOrderBook);
                Assert.assertEquals("0,0,0,11011,40\n", sink.toString());

            } finally {
                Unsafe.free(order, Order.SIZE);
            }
        }

        Assert.assertEquals(expectedMem, Unsafe.getMemUsed());
    }

    @Test
    public void testMatchSellUnderAtPricePoint() {
        long expectedMem = Unsafe.getMemUsed();
        try (OrderBook orderBook = new OrderBook(100_00, 200_00, 1000_000, OrderBookTest::captureExecutionReport)) {

            final String expectedReports = "0,150,BUY\n" +
                    "2,150,SELL\n" +
                    "1,50,BUY\n" +
                    "2,50,SELL\n";


            long order = Unsafe.malloc(Order.SIZE);
            try {
                Order.setSize(order, 150);
                Order.setSide(order, OrderSides.BUY);
                Order.setPrice(order, 110_12);
                Assert.assertEquals(0, orderBook.limitOrder(order));

                Order.setSize(order, 180);
                Order.setSide(order, OrderSides.BUY);
                Order.setPrice(order, 110_12);
                Assert.assertEquals(1, orderBook.limitOrder(order));

                orderBook.getOrderBook(OrderBookTest::printOrderBook);
                Assert.assertEquals("0,11012,330,0,0\n", sink.toString());

                sink.clear();
                Order.setSize(order, 200);
                Order.setSide(order, OrderSides.SELL);
                Order.setPrice(order, 110_11);
                Assert.assertEquals(2, orderBook.limitOrder(order));

                Assert.assertEquals(0, orderBook.getAskLevelCount());
                Assert.assertEquals(expectedReports, sink.toString());

                sink.clear();
                orderBook.getOrderBook(OrderBookTest::printOrderBook);
                Assert.assertEquals("0,11012,130,0,0\n", sink.toString());
            } finally {
                Unsafe.free(order, Order.SIZE);
            }
        }

        Assert.assertEquals(expectedMem, Unsafe.getMemUsed());
    }

    @Test
    public void testMatchBuyUnderAtPricePoint() {
        long expectedMem = Unsafe.getMemUsed();
        try (OrderBook orderBook = new OrderBook(100_00, 200_00, 1000_000, OrderBookTest::captureExecutionReport)) {


            long order = Unsafe.malloc(Order.SIZE);
            try {
                Order.setSize(order, 150);
                Order.setSide(order, OrderSides.SELL);
                Order.setPrice(order, 110_12);
                Assert.assertEquals(0, orderBook.limitOrder(order));

                Order.setSize(order, 180);
                Order.setSide(order, OrderSides.SELL);
                Order.setPrice(order, 110_20);
                Assert.assertEquals(1, orderBook.limitOrder(order));

                orderBook.getOrderBook(OrderBookTest::printOrderBook);
                Assert.assertEquals("0,0,0,11012,150\n" +
                        "1,0,0,11020,180\n", sink.toString());

                sink.clear();
                Order.setSize(order, 200);
                Order.setSide(order, OrderSides.BUY);
                Order.setPrice(order, 110_50);
                Assert.assertEquals(2, orderBook.limitOrder(order));

                Assert.assertEquals(1, orderBook.getAskLevelCount());
                Assert.assertEquals("0,150,SELL\n" +
                                "2,150,BUY\n" +
                                "1,50,SELL\n" +
                                "2,50,BUY\n",
                        sink.toString()
                );

                sink.clear();
                orderBook.getOrderBook(OrderBookTest::printOrderBook);
                Assert.assertEquals("0,0,0,11020,130\n", sink.toString());
            } finally {
                Unsafe.free(order, Order.SIZE);
            }
        }

        Assert.assertEquals(expectedMem, Unsafe.getMemUsed());
    }

    @Test
    public void testMatchBuyExactAtPricePoint() {
        long expectedMem = Unsafe.getMemUsed();
        try (OrderBook orderBook = new OrderBook(100_00, 200_00, 1000_000, OrderBookTest::captureExecutionReport)) {


            long order = Unsafe.malloc(Order.SIZE);
            try {
                Order.setSize(order, 150);
                Order.setSide(order, OrderSides.SELL);
                Order.setPrice(order, 110_12);
                Assert.assertEquals(0, orderBook.limitOrder(order));

                Order.setSize(order, 180);
                Order.setSide(order, OrderSides.SELL);
                Order.setPrice(order, 110_20);
                Assert.assertEquals(1, orderBook.limitOrder(order));

                orderBook.getOrderBook(OrderBookTest::printOrderBook);
                Assert.assertEquals("0,0,0,11012,150\n" +
                        "1,0,0,11020,180\n", sink.toString());

                sink.clear();
                Order.setSize(order, 330);
                Order.setSide(order, OrderSides.BUY);
                Order.setPrice(order, 110_50);
                Assert.assertEquals(2, orderBook.limitOrder(order));

                Assert.assertEquals(0, orderBook.getAskLevelCount());
                Assert.assertEquals(0, orderBook.getBidLevelCount());
                Assert.assertEquals("0,150,SELL\n" +
                                "2,150,BUY\n" +
                                "1,180,SELL\n" +
                                "2,180,BUY\n",
                        sink.toString()
                );

                sink.clear();
                orderBook.getOrderBook(OrderBookTest::printOrderBook);
                Assert.assertEquals("", sink.toString());
            } finally {
                Unsafe.free(order, Order.SIZE);
            }
        }

        Assert.assertEquals(expectedMem, Unsafe.getMemUsed());
    }

    @Test
    public void testBidDoesNotCrossBid() {
        long expectedMem = Unsafe.getMemUsed();
        try (OrderBook orderBook = new OrderBook(100_00, 200_00, 1000_000, OrderBookTest::captureExecutionReport)) {


            long order = Unsafe.malloc(Order.SIZE);
            try {
                Order.setSize(order, 150);
                Order.setSide(order, OrderSides.SELL);
                Order.setPrice(order, 110_12);
                Assert.assertEquals(0, orderBook.limitOrder(order));

                Order.setSize(order, 180);
                Order.setSide(order, OrderSides.SELL);
                Order.setPrice(order, 110_20);
                Assert.assertEquals(1, orderBook.limitOrder(order));

                Order.setSize(order, 330);
                Order.setSide(order, OrderSides.BUY);
                Order.setPrice(order, 110_09);
                Assert.assertEquals(2, orderBook.limitOrder(order));

                orderBook.getOrderBook(OrderBookTest::printOrderBook);
                Assert.assertEquals("0,11009,330,11012,150\n" +
                                "1,0,0,11020,180\n",
                        sink.toString()
                );

                Order.setSize(order, 330);
                Order.setSide(order, OrderSides.BUY);
                Order.setPrice(order, 110_50);
                Assert.assertEquals(3, orderBook.limitOrder(order));

                sink.clear();
                orderBook.getOrderBook(OrderBookTest::printOrderBook);
                Assert.assertEquals("0,11009,330,0,0\n", sink.toString());
            } finally {
                Unsafe.free(order, Order.SIZE);
            }
        }

        Assert.assertEquals(expectedMem, Unsafe.getMemUsed());
    }

    @Test
    public void testZeroSizeOrders() {
        long expectedMem = Unsafe.getMemUsed();
        try (OrderBook orderBook = new OrderBook(100_00, 200_00, 1000_000, OrderBookTest::captureExecutionReport)) {
            long order = Unsafe.malloc(Order.SIZE);
            try {
                Order.setSize(order, 150);
                Order.setSide(order, OrderSides.SELL);
                Order.setPrice(order, 110_12);
                Assert.assertEquals(0, orderBook.limitOrder(order));

                Order.setSize(order, 180);
                Order.setSide(order, OrderSides.SELL);
                Order.setPrice(order, 110_20);
                Assert.assertEquals(1, orderBook.limitOrder(order));

                Order.setSize(order, 0);
                Order.setSide(order, OrderSides.BUY);
                Order.setPrice(order, 110_09);
                Assert.assertEquals(2, orderBook.limitOrder(order));

                Order.setSize(order, 0);
                Order.setSide(order, OrderSides.SELL);
                Order.setPrice(order, 110_09);
                Assert.assertEquals(3, orderBook.limitOrder(order));

                orderBook.getOrderBook(OrderBookTest::printOrderBook);
                Assert.assertEquals("0,0,0,11012,150\n" +
                                "1,0,0,11020,180\n",
                        sink.toString()
                );
            } finally {
                Unsafe.free(order, Order.SIZE);
            }
        }

        Assert.assertEquals(expectedMem, Unsafe.getMemUsed());
    }

    @Test
    public void testLowerEdgeAsk() {
        long expectedMem = Unsafe.getMemUsed();
        try (OrderBook orderBook = new OrderBook(100_00, 200_00, 1000_000, OrderBookTest::captureExecutionReport)) {
            long order = Unsafe.malloc(Order.SIZE);
            try {
                Order.setSize(order, 140);
                Order.setSide(order, OrderSides.SELL);
                Order.setPrice(order, 100_00);
                Assert.assertEquals(0, orderBook.limitOrder(order));

                Order.setSize(order, 250);
                Order.setSide(order, OrderSides.SELL);
                Order.setPrice(order, 100_00);
                Assert.assertEquals(1, orderBook.limitOrder(order));

                orderBook.getOrderBook(OrderBookTest::printOrderBook);
                Assert.assertEquals("0,0,0,10000,390\n",
                        sink.toString()
                );
            } finally {
                Unsafe.free(order, Order.SIZE);
            }
        }

        Assert.assertEquals(expectedMem, Unsafe.getMemUsed());
    }

    @Test
    public void testLowerEdgeBid() {
        long expectedMem = Unsafe.getMemUsed();
        try (OrderBook orderBook = new OrderBook(100_00, 200_00, 1000_000, OrderBookTest::captureExecutionReport)) {
            long order = Unsafe.malloc(Order.SIZE);
            try {
                Order.setSize(order, 140);
                Order.setSide(order, OrderSides.BUY);
                Order.setPrice(order, 100_00);
                Assert.assertEquals(0, orderBook.limitOrder(order));

                Order.setSize(order, 250);
                Order.setSide(order, OrderSides.BUY);
                Order.setPrice(order, 100_00);
                Assert.assertEquals(1, orderBook.limitOrder(order));

                orderBook.getOrderBook(OrderBookTest::printOrderBook);
                Assert.assertEquals("0,10000,390,0,0\n",
                        sink.toString()
                );
            } finally {
                Unsafe.free(order, Order.SIZE);
            }
        }

        Assert.assertEquals(expectedMem, Unsafe.getMemUsed());
    }

    @Test
    public void testResizeLowerEdge() {
        long expectedMem = Unsafe.getMemUsed();
        try (OrderBook orderBook = new OrderBook(100_00, 200_00, 1000_000, OrderBookTest::captureExecutionReport)) {
            long order = Unsafe.malloc(Order.SIZE);
            try {
                Order.setSize(order, 140);
                Order.setSide(order, OrderSides.BUY);
                Order.setPrice(order, 88_00);
                Assert.assertEquals(0, orderBook.limitOrder(order));

                orderBook.getOrderBook(OrderBookTest::printOrderBook);
                Assert.assertEquals("0,8800,140,0,0\n",
                        sink.toString()
                );
            } finally {
                Unsafe.free(order, Order.SIZE);
            }
        }

        Assert.assertEquals(expectedMem, Unsafe.getMemUsed());
    }

    @Test
    public void testResizeUpperEdge() {
        long expectedMem = Unsafe.getMemUsed();
        try (OrderBook orderBook = new OrderBook(100_00, 200_00, 1000_000, OrderBookTest::captureExecutionReport)) {
            long order = Unsafe.malloc(Order.SIZE);
            try {
                Order.setSize(order, 140);
                Order.setSide(order, OrderSides.BUY);
                Order.setPrice(order, 210_00);
                Assert.assertEquals(0, orderBook.limitOrder(order));

                orderBook.getOrderBook(OrderBookTest::printOrderBook);
                Assert.assertEquals("0,21000,140,0,0\n",
                        sink.toString()
                );
            } finally {
                Unsafe.free(order, Order.SIZE);
            }
        }

        Assert.assertEquals(expectedMem, Unsafe.getMemUsed());
    }

    @Test
    public void testUpperEdgeBid() {
        long expectedMem = Unsafe.getMemUsed();
        try (OrderBook orderBook = new OrderBook(100_00, 200_00, 1000_000, OrderBookTest::captureExecutionReport)) {
            long order = Unsafe.malloc(Order.SIZE);
            try {
                Order.setSize(order, 140);
                Order.setSide(order, OrderSides.BUY);
                Order.setPrice(order, 200_00);
                Assert.assertEquals(0, orderBook.limitOrder(order));

                Order.setSize(order, 250);
                Order.setSide(order, OrderSides.BUY);
                Order.setPrice(order, 200_00);
                Assert.assertEquals(1, orderBook.limitOrder(order));

                orderBook.getOrderBook(OrderBookTest::printOrderBook);
                Assert.assertEquals("0,20000,390,0,0\n",
                        sink.toString()
                );
            } finally {
                Unsafe.free(order, Order.SIZE);
            }
        }

        Assert.assertEquals(expectedMem, Unsafe.getMemUsed());
    }

    @Test
    public void testUpperEdgeAsk() {
        long expectedMem = Unsafe.getMemUsed();
        try (OrderBook orderBook = new OrderBook(100_00, 200_00, 1000_000, OrderBookTest::captureExecutionReport)) {
            long order = Unsafe.malloc(Order.SIZE);
            try {
                Order.setSize(order, 140);
                Order.setSide(order, OrderSides.SELL);
                Order.setPrice(order, 200_00);
                Assert.assertEquals(0, orderBook.limitOrder(order));

                Order.setSize(order, 250);
                Order.setSide(order, OrderSides.SELL);
                Order.setPrice(order, 200_00);
                Assert.assertEquals(1, orderBook.limitOrder(order));

                orderBook.getOrderBook(OrderBookTest::printOrderBook);
                Assert.assertEquals("0,0,0,20000,390\n",
                        sink.toString()
                );
            } finally {
                Unsafe.free(order, Order.SIZE);
            }
        }

        Assert.assertEquals(expectedMem, Unsafe.getMemUsed());
    }

    @Test
    public void testMatchBuyOverAtPricePoint() {
        long expectedMem = Unsafe.getMemUsed();
        try (OrderBook orderBook = new OrderBook(100_00, 200_00, 1000_000, OrderBookTest::captureExecutionReport)) {

            long order = Unsafe.malloc(Order.SIZE);
            try {
                Order.setSize(order, 150);
                Order.setSide(order, OrderSides.SELL);
                Order.setPrice(order, 110_12);
                Assert.assertEquals(0, orderBook.limitOrder(order));

                Order.setSize(order, 180);
                Order.setSide(order, OrderSides.SELL);
                Order.setPrice(order, 110_20);
                Assert.assertEquals(1, orderBook.limitOrder(order));

                orderBook.getOrderBook(OrderBookTest::printOrderBook);
                Assert.assertEquals("0,0,0,11012,150\n" +
                        "1,0,0,11020,180\n", sink.toString());

                sink.clear();
                Order.setSize(order, 400);
                Order.setSide(order, OrderSides.BUY);
                Order.setPrice(order, 110_50);
                Assert.assertEquals(2, orderBook.limitOrder(order));

                Assert.assertEquals(0, orderBook.getAskLevelCount());
                Assert.assertEquals(1, orderBook.getBidLevelCount());
                Assert.assertEquals("0,150,SELL\n" +
                                "2,150,BUY\n" +
                                "1,180,SELL\n" +
                                "2,180,BUY\n",
                        sink.toString()
                );

                sink.clear();
                orderBook.getOrderBook(OrderBookTest::printOrderBook);
                Assert.assertEquals("0,11050,70,0,0\n", sink.toString());
            } finally {
                Unsafe.free(order, Order.SIZE);
            }
        }

        Assert.assertEquals(expectedMem, Unsafe.getMemUsed());
    }

    @Test
    public void testMatchSellExactAtPricePoint() {
        long expectedMem = Unsafe.getMemUsed();
        try (OrderBook orderBook = new OrderBook(100_00, 200_00, 1000_000, OrderBookTest::captureExecutionReport)) {

            final String expectedReports = "0,150,BUY\n" +
                    "2,150,SELL\n" +
                    "1,180,BUY\n" +
                    "2,180,SELL\n";


            long order = Unsafe.malloc(Order.SIZE);
            try {
                Order.setSize(order, 150);
                Order.setSide(order, OrderSides.BUY);
                Order.setPrice(order, 110_12);
                Assert.assertEquals(0, orderBook.limitOrder(order));

                Order.setSize(order, 180);
                Order.setSide(order, OrderSides.BUY);
                Order.setPrice(order, 110_12);
                Assert.assertEquals(1, orderBook.limitOrder(order));

                orderBook.getOrderBook(OrderBookTest::printOrderBook);
                Assert.assertEquals("0,11012,330,0,0\n", sink.toString());

                sink.clear();
                Order.setSize(order, 330);
                Order.setSide(order, OrderSides.SELL);
                Order.setPrice(order, 110_11);
                Assert.assertEquals(2, orderBook.limitOrder(order));

                Assert.assertEquals(0, orderBook.getAskLevelCount());
                Assert.assertEquals(0, orderBook.getBidLevelCount());
                Assert.assertEquals(expectedReports, sink.toString());

                sink.clear();
                orderBook.getOrderBook(OrderBookTest::printOrderBook);
                Assert.assertEquals("", sink.toString());
            } finally {
                Unsafe.free(order, Order.SIZE);
            }
        }

        Assert.assertEquals(expectedMem, Unsafe.getMemUsed());
    }

    @Test
    public void testBadSide() {
        long expectedMem = Unsafe.getMemUsed();
        try (OrderBook orderBook = new OrderBook(100_00, 200_00, 1000_000, OrderBookTest::captureExecutionReport)) {
            long order = Unsafe.malloc(Order.SIZE);
            try {
                Order.setSize(order, 150);
                Order.setSide(order, (byte) 9);
                Order.setPrice(order, 110_12);
                Assert.assertEquals(-1, orderBook.limitOrder(order));
            } finally {
                Unsafe.free(order, Order.SIZE);
            }
        }

        Assert.assertEquals(expectedMem, Unsafe.getMemUsed());
    }

    @Test
    public void testLevelAccess() {
        long expectedMem = Unsafe.getMemUsed();
        try (OrderBook orderBook = new OrderBook(100_00, 200_00, 1000_000, OrderBookTest::captureExecutionReport)) {

            final String expectedReports = "";


            long order = Unsafe.malloc(Order.SIZE);
            try {
                Order.setSize(order, 10_000);
                Order.setSide(order, OrderSides.BUY);
                Order.setPrice(order, 110_12);
                Assert.assertEquals(0, orderBook.limitOrder(order));

                Order.setSize(order, 20000);
                Order.setSide(order, OrderSides.BUY);
                Order.setPrice(order, 110_10);
                Assert.assertEquals(1, orderBook.limitOrder(order));


                Order.setSize(order, 10_000);
                Order.setSide(order, OrderSides.SELL);
                Order.setPrice(order, 110_16);
                Assert.assertEquals(2, orderBook.limitOrder(order));

                Order.setSize(order, 100_000);
                Order.setSide(order, OrderSides.SELL);
                Order.setPrice(order, 110_18);
                Assert.assertEquals(3, orderBook.limitOrder(order));

                Order.setSize(order, 1_000_000);
                Order.setSide(order, OrderSides.SELL);
                Order.setPrice(order, 110_19);
                Assert.assertEquals(4, orderBook.limitOrder(order));


                // bid and ask levels
                Assert.assertEquals(2, orderBook.getBidLevelCount());
                Assert.assertEquals(3, orderBook.getAskLevelCount());

                // prices and sizes at expected levels
                Assert.assertEquals(110_12, orderBook.getPriceAtEntry(orderBook.getEntryAtBidLevel(0)));
                Assert.assertEquals(10_000, PricePointEntry.getSize(orderBook.getEntryAtBidLevel(0)));

                Assert.assertEquals(110_16, orderBook.getPriceAtEntry(orderBook.getEntryAtAskLevel(0)));
                Assert.assertEquals(10_000, PricePointEntry.getSize(orderBook.getEntryAtAskLevel(0)));

                Assert.assertEquals(110_10, orderBook.getPriceAtEntry(orderBook.getEntryAtBidLevel(1)));
                Assert.assertEquals(20_000, PricePointEntry.getSize(orderBook.getEntryAtBidLevel(1)));

                Assert.assertEquals(110_18, orderBook.getPriceAtEntry(orderBook.getEntryAtAskLevel(1)));
                Assert.assertEquals(100_000, PricePointEntry.getSize(orderBook.getEntryAtAskLevel(1)));

                Assert.assertEquals(-1, orderBook.getEntryAtBidLevel(2));
                Assert.assertEquals(110_19, orderBook.getPriceAtEntry(orderBook.getEntryAtAskLevel(2)));
                Assert.assertEquals(1_000_000, PricePointEntry.getSize(orderBook.getEntryAtAskLevel(2)));

                Assert.assertEquals(-1, orderBook.getEntryAtAskLevel(3));

                Assert.assertEquals(expectedReports, sink.toString());

                sink.clear();
                orderBook.getOrderBook(OrderBookTest::printOrderBook);

                Assert.assertEquals("0,11012,10000,11016,10000\n" +
                        "1,11010,20000,11018,100000\n" +
                        "2,0,0,11019,1000000\n", sink.toString());
            } finally {
                Unsafe.free(order, Order.SIZE);
            }

        }

        Assert.assertEquals(expectedMem, Unsafe.getMemUsed());
    }

    @Test
    public void testOnlyBids() {
        long expectedMem = Unsafe.getMemUsed();
        try (OrderBook orderBook = new OrderBook(100_00, 200_00, 1000_000, OrderBookTest::captureExecutionReport)) {
            long order = Unsafe.malloc(Order.SIZE);
            try {
                Order.setSize(order, 10_000);
                Order.setSide(order, OrderSides.BUY);
                Order.setPrice(order, 110_12);
                Assert.assertEquals(0, orderBook.limitOrder(order));

                Order.setSize(order, 20000);
                Order.setSide(order, OrderSides.BUY);
                Order.setPrice(order, 110_10);
                Assert.assertEquals(1, orderBook.limitOrder(order));

                sink.clear();
                orderBook.getOrderBook(OrderBookTest::printOrderBook);

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
        try (OrderBook orderBook = new OrderBook(100_00, 200_00, 1000_000, OrderBookTest::captureExecutionReport)) {
            long order = Unsafe.malloc(Order.SIZE);
            try {
                Order.setSize(order, 10_000);
                Order.setSide(order, OrderSides.SELL);
                Order.setPrice(order, 110_12);
                Assert.assertEquals(0, orderBook.limitOrder(order));

                Order.setSize(order, 20_000);
                Order.setSide(order, OrderSides.SELL);
                Order.setPrice(order, 110_14);
                Assert.assertEquals(1, orderBook.limitOrder(order));

                sink.clear();
                orderBook.getOrderBook(OrderBookTest::printOrderBook);

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