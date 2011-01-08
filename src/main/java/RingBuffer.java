package com.somethingsimilar.ring_buffer;

interface RingBuffer<T> {
  long add(T obj);
  T get(long slot);
  int capacity();
  long latestSlot();
}
