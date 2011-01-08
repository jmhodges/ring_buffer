package com.somethingsimilar.ring_buffer;

class InvalidPowerOfTwoForCapacity extends RuntimeException {
  static String msg = "Power of two for RingBuffer capacity must be between 1 and 30 (inclusive) to ensure speedy modulus calculations. It was %d";
  InvalidPowerOfTwoForCapacity(int powerOfTwoForCapacity) {
    super(String.format(msg, powerOfTwoForCapacity));
  }
}
