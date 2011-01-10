AtomicRingBuffer is a lock-free ring buffer written for the JVM. This is a
basic primitive required for the Disruptor architecture described in the [LMAX
presentation](http://www.infoq.com/presentations/LMAX) at QCon San Francisco
2010.

Use the Writer and Reader classes to write and read from a RingBuffer
safely. While the `sequence` methods on both are safe to use from any thread,
Writers and Readers in the Disruptor pattern are meant to be used in only one
thread.

It would be desirable convenient read batching and the cute object
pre-allocation as described in above presentation.
