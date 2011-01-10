package com.somethingsimilar.ring_buffer;

import java.util.concurrent.atomic.AtomicLong;

// Use only one Writer per thread. We need the readers and the total
// number of Writers for that RingBuffer so that we can determine if
// we need to block on a write that would overflow the buffer before
// all of the Readers have read the data.

class Writer<T> {
  RingBuffer<T> buf;
  Reader<T>[] readers;
  int numOfWriters;
  int maxReaderDistanceFromWriter;

  AtomicLong slot = new AtomicLong(-1); // atomic for testing

  Writer(RingBuffer<T> buf, Reader<T>[] readers, int numOfWriters) {
    this.buf = buf;
    this.readers = readers;
    this.numOfWriters = numOfWriters;
    this.maxReaderDistanceFromWriter = buf.capacity() - numOfWriters;
  }

  void write(T obj) {
    while (atLeastOneReaderIsTooFarBehind()) {}
    slot.set(buf.add(obj));
  }

  // The last slot written to by this writer
  long sequence() { return slot.get(); }

  private
  boolean atLeastOneReaderIsTooFarBehind() {
    for (int i=0; i < readers.length; i++) {
      if (sequence() - readers[i].sequence() > maxReaderDistanceFromWriter) {
        return true;
      }
    }
    return false;
  }
}
