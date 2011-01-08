package com.somethingsimilar.ring_buffer

import org.specs.Specification

class AtomicRingBufferSpec extends Specification {
  "AtomicRingBuffer" should {

    "be fine with one write and one read" in {
      val buf = new AtomicRingBuffer[Int](2)
      val reader = new Reader[Int](buf)
      val writer = new Writer[Int](buf, Array(reader), 1)
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
      val numOfWritesToMake = 10000
      val endSlot = (numOfWritesToMake - 1).toLong
      val numOfReaders = 50

      val readers = 0.until(numOfReaders).map(i => new Reader[Int](buf)).toArray
      val writer = new Writer[Int](buf, readers, 1)

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

      val writerThread = new Thread {
        override def run = {
          for (i <- 0.until(numOfWritesToMake)) {
            writer.write(i)
          }
        }
      }
      writerThread.start

      // stopAt is set to stop the checks at 30 seconds in the future.
      val stopAt = System.currentTimeMillis + (30 * 1000)

      while (stopAt > System.currentTimeMillis && readers.map(_.sequence).min < endSlot) {
        // nada
      }

      val expectedReadSlots = 0.until(numOfReaders).map( i => endSlot ).toList
      val actualReadSlots = readers.map(_.sequence)
      actualReadSlots.toList must containInOrder(expectedReadSlots)
      buf.latestSlot mustEqual endSlot
    }

    "handle lots of writers with more items than capacity" in {
      val buf = new AtomicRingBuffer[Int](10)
      val numOfWritesToMake = 3000
      val endSlot = (numOfWritesToMake - 1).toLong
      val numOfWriters = 50

      val reader = new Reader[Int](buf)
      val writers = 0.until(numOfWriters).map{
        i =>
          new Writer(buf, Array(reader), numOfWriters)
      }.toList

      val writerThreads = writers.zipWithIndex.map {
        case (w,i) =>
          new Thread {
            override def run = {
              var item = i
              while (item < numOfWritesToMake) {
                w.write(item)
                item += numOfWriters
              }
            }
          }
      }
      writerThreads.map(_.start)
      var readQ = new java.util.concurrent.ArrayBlockingQueue[Int](numOfWritesToMake)

      val readerThread = new Thread {
        override def run() = {
          for (i <- 0.until(numOfWritesToMake)) {
            readQ.add(reader.read)
          }
        }
      }
      readerThread.start

      // stopAt is set to stop the checks at 30 seconds in the future.
      val stopAt = (System.currentTimeMillis) + (30 * 1000)

      while (stopAt > System.currentTimeMillis && readQ.size() < numOfWritesToMake) {
        // nada
      }
      var readItems = List[Int]()

      val qSize = readQ.size
      0.until(qSize).foreach {
        i =>
          readItems = readQ.take :: readItems
      }

      val sortedItems = readItems.sortWith(_ < _)
      val expectedReadItems = 0.until(numOfWritesToMake).toList
      qSize mustEqual expectedReadItems.size
      sortedItems must containInOrder(expectedReadItems)

      reader.sequence mustEqual endSlot
    }
  }
}
