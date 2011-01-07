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
      buf.latestSlot.get mustEqual endSlot
    }

    class LoggingWriter(buf: RingBuffer[(Int, Thread)]) extends Writer[(Int, Thread)](buf) {
      var oldObj = -1
      override def write(obj: (Int, Thread)) = {
        // println("Got obj %d".format(obj), Thread.currentThread)
        val newObj = obj._1
        if (oldObj == newObj) {
          throw new RuntimeException("damn (%d, %d)".format(oldObj, newObj))
        }
        oldObj = newObj
        super.write(obj)
      }
    }

    class WritingThread(w: Writer[(Int, Thread)],
                        i: Int,
                        delta: Int, max: Int) extends Thread {
      override def run = {
        var item = i
        while (item < max) {
          w.write((item, this))
          item += delta
        }
      }
    }

    "handle lots of writers with more items than capacity" in {
      val buf = new AtomicRingBuffer[(Int, Thread)](10)
      val numOfWritesToMake = 2000
      val endSlot = (numOfWritesToMake - 1).toLong
      val numOfWriters = 2

      val writers = 0.until(numOfWriters).map(i => new LoggingWriter(buf)).toList
      val reader = new Reader[(Int, Thread)](buf)
      println("Hum")
      val writerThreads = writers.zipWithIndex.map {
        case (w,i) =>
          new WritingThread(w, i, numOfWriters, numOfWritesToMake)
      }
      println("MY THREADS ", writerThreads)
      writerThreads.map(_.start)
      println("Fuu")
      var readQ = new java.util.concurrent.ArrayBlockingQueue[(Int, Thread)](numOfWritesToMake)

      val readerThread = new Thread {
        override def run() = {
          for (i <- 0.until(numOfWritesToMake)) {
            readQ.add(reader.read)
          }
        }
      }
      readerThread.start
      println("Nope")
      // stopAt is set to stop the checks at 30 seconds in the future.
      val stopAt = (System.currentTimeMillis) + (60 * 1000)

      while (stopAt > System.currentTimeMillis && readQ.size() < numOfWritesToMake) {
        // nada
      }
      println("Past")
      var threadItems = List[(Int, Thread)]()
      val qSize = readQ.size
      println("qSize is %d", qSize)
      0.until(qSize).foreach {
        i =>
          threadItems = readQ.take :: threadItems
      }

      println(threadItems)
      println("\nDIFF\n")
      println(threadItems.sortWith(_._1 < _._1))
      val readItems = threadItems.map{case (i, _) => i}
      val sortedItems = readItems.sortWith(_ < _)
      println("HRM")
      println("Really past")
      val expectedReadItems = 0.until(numOfWritesToMake).toList
      println("Sigh",  expectedReadItems.size, sortedItems.size, qSize)
      qSize mustEqual expectedReadItems.size
      println("Okay")
      // println(sortedItems)
      sortedItems must containInOrder(expectedReadItems)
      sortedItems(0) mustEqual expectedReadItems(0)
      println("WTF", sortedItems(numOfWritesToMake-1), expectedReadItems(numOfWritesToMake-1))
      sortedItems(numOfWritesToMake-1) mustEqual expectedReadItems(numOfWritesToMake-1)
      println("There")
      reader.sequence mustEqual endSlot
    }
  }
}
