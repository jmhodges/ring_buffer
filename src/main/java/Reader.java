package com.somethingsimilar.ring_buffer;

import java.util.concurrent.atomic.AtomicLong;

// Use only one Reader per thread.
class Reader<T> {
  AtomicLong slot = new AtomicLong(-1); // atomic for testing
  RingBuffer<T> buf;

  Reader(RingBuffer<T> buf) {
    this.buf = buf;
  }

  // Reads only one item from the buffer
  T read() {
    // get the index of the next slot (not the last one read) but
    // don't announce that we've read the slot by incrementing slot
    // before we actually have read it.
    long rSlot = slot.get() + 1;
    while (buf.latestSlot() < rSlot) {}
    T ret = buf.get(rSlot);
    slot.incrementAndGet();
    return ret;
  }

  // The latest slot this reader has grabbed
  long sequence() { return slot.get(); }
}