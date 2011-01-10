package com.somethingsimilar.ring_buffer;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReferenceArray;

// Assumption: Old data is more good than new data. Block on writes.
// slots returned from #latestSlot and #get are in the range 0 to
// (2**63 - 1), inclusively.
public class AtomicRingBuffer<T> implements RingBuffer<T> {
  private AtomicLong nextSequence = new AtomicLong(0);
  volatile private long cursor = -1L;
  int cap;
  private T[] inner;

  @SuppressWarnings("unchecked")
  public AtomicRingBuffer(int powerOfTwoForCapacity) {
    if (powerOfTwoForCapacity > 30 || powerOfTwoForCapacity < 1) {
      throw new InvalidPowerOfTwoForCapacity(powerOfTwoForCapacity);
    }

    this.cap = 1 << powerOfTwoForCapacity;

    this.inner = (T[]) new Object[cap];
  }

  // FIXME should provide a jvm bytecode version that does object reuse
  public long add(T obj) {
    long seq = nextSequence.getAndIncrement();
    inner[(int) (seq % ((long) cap))] = obj;
    while (cursor != (seq - 1)) {}
    this.cursor = seq;
    return seq;
  }

  public T get(long slot) {
    while (slot > cursor) {}
    return inner[(int) (slot % ((long) cap))];
  }

  public int capacity() { return cap; }

  // latestSlot is always increasing. Eventually, it will hit 2**63
  // and overflow. If an item comes in once a nanosecond, we will
  // have 292 years before that occurs. By then, the process is
  // likely to have been restarted.
  public long latestSlot() { return cursor; }
}
