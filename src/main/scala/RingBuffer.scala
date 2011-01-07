package com.somethingsimilar.ring_buffer
import java.util.concurrent.atomic.{AtomicLong, AtomicReferenceArray}

trait RingBuffer[T] {
  def add(obj: T) : Long
  def get(slot: Long) : T
  def capacity: Long
  def latestSlot : AtomicLong
}

class InvalidPowerOfTwoForCapacity(msg:String) extends RuntimeException(msg) {
  def this(powerOfTwoForCapacity: Int) = {
    this("Power of two for RingBuffer capacity must be between 1 and 30 (inclusive) to ensure speedy modulus calculations. It was %d".format(powerOfTwoForCapacity))
  }
}

// Assumption: Old data is more good than new data. Block on writes.
// slots returned from #latestSlot and #get are in the range 0 to
// (2**63 - 1), inclusively.
class AtomicRingBuffer[T : ClassManifest](powerOfTwoForCapacity: Int) extends RingBuffer[T] {
  if (powerOfTwoForCapacity > 30 || powerOfTwoForCapacity < 1) {
    throw new InvalidPowerOfTwoForCapacity(powerOfTwoForCapacity)
  }

  // cap is volatile to allow a writer in another thread to busy-wait
  // if a buffer would overflow. Unnecessary until we get multiple writers.
  @volatile var cap = scala.math.pow(2, powerOfTwoForCapacity).toInt

  // FIXME atomic reference array?
  private val inner = new AtomicReferenceArray[T](cap) // The array to hold the items.
  private var innerWriteCount = new AtomicLong(-1)
  private var publicWriteCount = new AtomicLong(-1)

  // FIXME should provide a jvm bytecode version that does object reuse
  def add(obj: T) : Long = {
    // Claim our slot by getting the current count and incrementing
    // over its spot.
    // This and the `slot+1` on publicWriteCount#compareAndSet() mean
    // that latestSlot is always increasing.
    val slot = innerWriteCount.incrementAndGet()
    inner.getAndSet((slot % cap).toInt, obj)

    // This compareAndSet is how, I believe, more than one writer
    // thread can be maintained at a time. You may be confused and
    // wonder why we can't just `publicWriteCount += 1`. Consider if
    // the modulus calculation above freezes up for a while for slot
    // 1. If the add for slot 2 speeds past it, and it increments
    // publicWriteCount naively, we will have said that slot 1 was
    // ready, when it was not.
    while (!publicWriteCount.compareAndSet(slot-1, slot)) {
      // FIXME Should set an inconsistency flag on obj and throw an
      // error after so many iterations.
    }
    return slot
  }

  def get(slot: Long) : T = {
    while (slot > publicWriteCount.get()) {}
    return inner.get((slot % cap).toInt)
  }

  def capacity = cap

  // latestSlot is always increasing. Eventually, it will hit 2**63
  // and overflow. If an item comes in once a nanosecond, we will
  // have 292 years before that occurs. By then, the process is
  // likely to have been restarted.
  def latestSlot = publicWriteCount
}

// Use only one Writer per thread. We need the readers and the total
// number of Writers for that RingBuffer so that we can determine if
// we need to block on a write that would overflow the buffer before
// all of the Readers have read the data.

class Writer[T : ClassManifest](buf: RingBuffer[T], readers: List[Reader[T]], numOfWriters: Int) {

  var slot = new AtomicLong(-1) // atomic for testing
  val maxReaderDistanceFromWriter = buf.capacity - numOfWriters

  def write(obj: T) : Unit = {
    while (atLeastOneReaderIsTooFarBehind()) {}
    slot.set(buf.add(obj))
  }

  // The last slot written to by this writer
  def sequence : Long = slot.get

  private
  def atLeastOneReaderIsTooFarBehind() : Boolean = {
    readers.exists {
      reader =>
        sequence - reader.sequence > maxReaderDistanceFromWriter
    }
  }
}

// Use only one Reader per thread.
class Reader[T : ClassManifest](buf: RingBuffer[T]) {
  var slot = new AtomicLong(-1) // atomic for testing

  // Reads only one item from the buffer
  def read : T = {
    // get the index of the next slot (not the last one read) but
    // don't announce that we've read the slot by incrementing slot
    // before we actually have read it.
    val rSlot = slot.get + 1
    while (buf.latestSlot.get < rSlot) {}
    val ret = buf.get(rSlot)
    slot.incrementAndGet
    ret
  }

  // The latest slot this reader has grabbed
  def sequence : Long = slot.get
}
