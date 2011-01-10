A lock-less ring buffer written for the JVM. This is a basic primitive
required for the Disruptor architecture described in the [LMAX
presentation](http://www.infoq.com/presentations/LMAX) at QCon San Francisco
2010.


Use the Writer and Reader classes to write and read from a RingBuffer safely.

It would be desirable convenient read batching and the cute object
pre-allocation as described in above presentation.
