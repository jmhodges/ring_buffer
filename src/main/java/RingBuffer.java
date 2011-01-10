package com.somethingsimilar.ring_buffer;

public interface RingBuffer<T> {
  public long add(T obj);
  public T get(long slot);
  public int capacity();
  public long latestSlot();
}
