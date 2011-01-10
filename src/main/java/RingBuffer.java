package com.somethingsimilar.ring_buffer;

// Here because I'm assuming I'll have to do interesting things when writing
// the object pre-allocating version of the AtomicRingBuffer.
public interface RingBuffer<T> {
  public long add(T obj);
  public T get(long slot);
  public int capacity();
  public long latestSlot();
}
