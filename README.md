### Design considerations

This is single-symbol implementation of limit OrderBook. Systems designed to work with multiple symbols will have multiple instances of OrderBook. One OrderBook per symbol.

This implementation is not thread-safe. Systems with multiple "writers" will have to queue orders via multiple-producer-single-consumer queue to a single threaded OrderBook.
I have implemented queues for this purpose before and described them in my blog post: http://blog.questdb.org/2016/08/the-art-of-thread-messaging.html
