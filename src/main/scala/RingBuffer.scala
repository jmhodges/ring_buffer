package com.somethingsimilar.ring_buffer
import java.util.concurrent.atomic.{AtomicLong, AtomicReferenceArray}

// Assumption: Old data is more good than new data. Block on writes.
// slots returned from #latestSlot and #get are in the range 0 to
// (2**63 - 1), inclusively.
class AtomicRingBuffer[T : ClassManifest](powerOfTwoForCapacity: Int) extends RingBuffer[T] {
  if (powerOfTwoForCapacity > 30 || powerOfTwoForCapacity < 1) {
    throw new InvalidPowerOfTwoForCapacity(powerOfTwoForCapacity)
  }

  // cap is volatile to allow a writer in another thread to busy-wait
  // if a buffer would overflow.
  @volatile var cap = scala.math.pow(2, powerOfTwoForCapacity).toInt

  // FIXME atomic reference array?
  private val inner = new AtomicReferenceArray[T](cap) // The array to hold the items.
  private var nextSequence = new AtomicLong(0)
  @volatile private var cursor = -1L

  // FIXME should provide a jvm bytecode version that does object reuse
  def add(obj: T) : Long = {
    val seq = nextSequence.getAndIncrement
    inner.getAndSet((seq % cap).toInt, obj)
    while (cursor != (seq - 1)) {}
    cursor = seq
    return seq
  }

  def get(slot: Long) : T = {
    while (slot > cursor) {}
    return inner.get((slot % cap).toInt)
  }

  def capacity = cap

  // latestSlot is always increasing. Eventually, it will hit 2**63
  // and overflow. If an item comes in once a nanosecond, we will
  // have 292 years before that occurs. By then, the process is
  // likely to have been restarted.
  def latestSlot = cursor
}
