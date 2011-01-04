package com.somethingsimilar.ring_buffer
import java.util.concurrent.atomic.AtomicLong

trait RingBuffer[T] {
  def add(obj: T) : Long
  def get(slot: Long) : T
  def capacity: Long
  def writeCount : AtomicLong
}

class InvalidPowerOfTwoForCapacity(msg:String) extends RuntimeException(msg) {
  def this(powerOfTwoForCapacity: Int) = {
    this("Power of two for RingBuffer capacity must be between 1 and 30 (inclusive). It was %d".format(powerOfTwoForCapacity))
  }
}

// Only works well with one writer that can check the readers
// readCount against CASRingBuffer#writeCount. Multiple readers can be
// maintained if they check their readCount against
// CASRingBuffer#writeCount.
class CASRingBuffer[T : ClassManifest](powerOfTwoForCapacity: Long) {
  if (powerOfTwoForCapacity > 30 || powerOfTwoForCapacity < 1) {
    throw new InvalidPowerOfTwoForCapacity(powerOfTwoForCapacity)
  }

  val cap = scala.math.pow(2, powerOfTwoForCapacity).toInt
  private val inner = new Array[T](cap)
  private var innerWriteCount = new AtomicLong(0)
  var publicWriteCount = new AtomicLong(0)

  // FIXME should provide a jvm bytecode version that does object reuse
  def add(obj: T) : Long = {
    // claim our slot by getting the current count and incrementing
    // over its spot.
    val slot = innerWriteCount.getAndIncrement()
    inner((slot % cap).toInt) = obj

    // FIXME Should set an inconsistency flag on obj (and give up)
    // after so many iterations.
    while (!publicWriteCount.compareAndSet(slot, slot+1)) {}
    return slot
  }

  def get(slot: Long) : T = {
    while (slot > publicWriteCount.get()) {}
    return inner((slot % cap).toInt)
  }

  def capacity = cap
  def writeCount = publicWriteCount
}
