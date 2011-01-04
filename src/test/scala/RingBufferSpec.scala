package com.somethingsimilar.ring_buffer

import org.specs.Specification

class AtomicRingBufferSpec extends Specification {
  "AtomicRingBuffer" should {

    "be fine with one write and one read" in {
      val buf = new AtomicRingBuffer[Int](2)
      val reader = new Reader[Int](buf)
      val writer = new Writer[Int](buf)
      reader.sequence mustEqual -1 // uninitialized
      writer.sequence mustEqual -1 // uninitialized

      writer.write(26)
      writer.sequence mustEqual 0

      val lastReadSlot = reader.read
      lastReadSlot mustEqual 26

      reader.sequence mustEqual 0
    }

    "handle lots of readers with more items than capacity" in {
      // 100000 is much larger than 2**10. This is allow our exit
      // condition (r.sequence + numOfReaders) < 1000000 usable
      // while still testing the behavior.
      val buf = new AtomicRingBuffer[Int](10)
      val numOfWritesToMake = 100000
      val endSlot = (numOfWritesToMake - 1).toLong
      val numOfReaders = 50

      val readers = 0.until(numOfReaders).map(i => new Reader[Int](buf)).toList
      val writer = new Writer[Int](buf)

      val readerThreads = readers.map {
        r =>
          new Thread {
           override def run() = {
             while (r.sequence < endSlot) {
               r.read
             }
           }
          }
      }
      readerThreads.map(_.start)

      for (i <- 0.until(numOfWritesToMake)) {
        writer.write(i)
      }

      // stopAt is set to stop the checks at 30 seconds in the future.
      val stopAt = (System.currentTimeMillis) + (30 * 1000)

      while (stopAt > System.currentTimeMillis && readers.map(_.sequence).min < endSlot) {
        // nada
      }

      val expectedReadSlots = 0.until(numOfReaders).map( i => endSlot ).toList
      val actualReadSlots = readers.map(_.sequence)
      actualReadSlots mustEqual expectedReadSlots
    }
  }
}
