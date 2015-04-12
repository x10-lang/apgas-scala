package apgas
package scala.examples
package uts

import java.security.MessageDigest
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean

import _root_.scala.util.Random

import apgas.Configuration
import apgas.Place
import apgas.scala.util._
import apgas.scala._

object UTSAPGAS {
  def main(args : Array[String]) : Unit = {
    Common.setup(numPlaces = 2)   
    
    val seed = 19
    val maxDepth = try {
      args(0).toInt
    } catch {
      case _ : Throwable => 13
    }
    
    {
      // initialize a worker in each place
      val worker = PlaceLocal.forPlaces(places) { new Worker() }
      
      println("Warmup...")
      worker.seed(seed, maxDepth - 2)
      finish { worker.run() }
    }
    
    {
      // initialize a worker in each place
      val worker = PlaceLocal.forPlaces(places) { new Worker() }
      println("Starting...");
      val t0 = System.nanoTime()
      
      worker.seed(19, maxDepth)
      finish {
        worker.run()
      }
    
      var count = 0L
      
      for(p <- places) {
        val partialCount = at(p) { worker.bag.counted }
        count += partialCount
      }

      val t1 = System.nanoTime()
      println("Finished.");
      
      Common.printResult(s"uts-apgas-${worker.placeCount}", maxDepth, count, t0, t1)
      Common.checkResult(maxDepth, count)
    }
  }
  
  final class Worker extends PlaceLocal {
    val home       = here
    val placeCount = places.size
    val random     = new java.util.Random()
    val md         = Bag.encoder()
    val bag        = new Bag(64)
  
    val thieves : ConcurrentLinkedQueue[Place] = new ConcurrentLinkedQueue[Place]()
    val lifeline : AtomicBoolean = new AtomicBoolean(home.id != placeCount - 1)
  
    sealed trait State
    case object Idle extends State
    case object Work extends State
    case class Wait(stealingFrom : Int) extends State
    
    var state : State = Idle

    def seed(s : Int, d : Int) : Unit = {
      bag.initialize(md, s, d)
    }

    def run() : Unit = {
      synchronized {
        state = Work
      }
          
      while (bag.size > 0) {
        while (bag.size > 0) {
          var n = 500
          while((n > 0) && (bag.size > 0)) {
            bag.expand(md)
            n -= 1
          }
          distribute()
        }
        steal()
      }
          
      synchronized {
        state = Idle
      }
          
      distribute()
      lifelinesteal()
    }
    
    def lifelinesteal() : Unit = if (placeCount != 1) {
      asyncAt(place((home.id + placeCount - 1) % placeCount)) {
        lifeline.set(true)
      }
    }
    
    def steal() : Unit = if(placeCount != 1) {
      val from = home
      
      val p = {
        val r = Random.nextInt(placeCount - 1)
        if(r >= home.id) r + 1 else r            
      }
          
      synchronized {
        state = Wait(p)
      }
          
      uncountedAsyncAt(place(p)) {
        request(from)
      }
          
      synchronized {
        while (state.isInstanceOf[Wait]) {
          try {
            wait()
          } catch {
            case _ : InterruptedException => ;
          }
        }
      }
    }
    
    def request(p : Place) : Unit = {
      synchronized {
        if (state == Work) {
          thieves.add(p)
          return
        }
      }
      
      uncountedAsyncAt(p) {
        deal(None)
      }
    }
    
    def lifelinedeal(b : Bag) : Unit = {
      bag.merge(b)
      run()
    }
    
    def deal(b : Option[Bag]) : Unit = synchronized {  
      b.foreach(bag.merge(_))
      state = Work
      notifyAll()
    }
    
    def distribute() : Unit = {
      var p : Place = null
      
      if (lifeline.get()) {
        for(b <- bag.split()) {
          p = place((home.id + 1) % placeCount)
          lifeline.set(false)

          asyncAt(p) {
            lifelinedeal(b)
          }
        }
      }
          
      while ({ p = thieves.poll(); p != null}) {
        val b = bag.split()
              
        uncountedAsyncAt(p) {
          deal(b)
        }
      }
    }
  }
}