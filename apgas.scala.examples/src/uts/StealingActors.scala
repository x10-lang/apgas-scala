package apgas.scala.examples
package uts

import akka.actor._
import java.security.MessageDigest
import scala.util.{ Try, Success, Failure, Random }
import akka.pattern.ask
import akka.util.Timeout
import scala.concurrent.duration._
import scala.concurrent.Await
import java.util.concurrent.TimeUnit

object StealingActors {
  case class InitWorkers(workers : Vector[ActorRef])
  case class LifelineDeal(work : Bag)
  case class Steal(thief : ActorRef)
  case class Deal(work : Bag)
  case object NoDeal
  case class LifelineReq(actor : ActorRef)  
  case object Work
  
  case class LocalTermination(inc : Int)
  case object RequestCount
  case class Count(count : Long)
  
  case class Compute(seed : Int, depth : Int)
  
  //val counter = new java.util.concurrent.atomic.AtomicInteger(0)
  
  def main(args : Array[String]) : Unit = {
    implicit val timeout : Timeout = Timeout(1 hour)
    
    val seed = 19
    val depth = try {
      args(0).toInt
    } catch {
      case _ : Throwable => 13
    }
    
    val system = ActorSystem("uts-system")
    
    val numWorkers : Int = 2
    
    implicit val dispatcher = system.dispatcher
    
    {
      println("Warmup...")
      val master = system.actorOf(Props(new Master(numWorkers)), name = "master-warmup")
      Await.result(master ? Compute(seed, (depth - 2) max 5), 1 hour)
      master ! Kill
    }
    
    {
      println("Working...")
      val master = system.actorOf(Props(new Master(numWorkers)), name = "master")
      val t0 = System.nanoTime()
      val result = Await.result(master ? Compute(seed, depth), 1 hour)
    
      result match {
        case Count(s) =>
          val t1 = System.nanoTime()
          Serial.printResult("actors", depth, s.toInt, t0, t1)
          Serial.checkResult(depth, s)
          
        case _ =>
          println("Unexpected result: " + result)
      }
      master ! Kill
    }
    
    system.shutdown()
  }
  
  class Master(numWorkers : Int) extends Actor with ActorLogging {
    val workers = Vector.tabulate[ActorRef](numWorkers) { i =>
      context.actorOf(Props(new Worker(self, i)), name = s"worker-$i")
    }
    
    // Set-up the topology.
    for(worker <- workers) {
      worker ! InitWorkers(workers)
    }
    
    var asker : Option[ActorRef] = None
    var counts : List[Long] = Nil
    var masterWork : Option[Bag] = None
    
    var delta : Int = 0
      
    override def receive = {
      case Compute(s, d) =>
        asker = Some(sender)
        val b = new Bag()
        masterWork = Some(b)
        b.initialize(MessageDigest.getInstance("SHA-1"), s, d)
        
        //log.info("DELTA : " + delta)
        delta = 1
        //log.info("DELTA : " + delta)
        workers(0) ! LifelineDeal(b)
        
      case l @ LocalTermination(inc) =>
        delta += inc
        
        //log.info("" + l + " " + delta)
        
        if(delta == 0) {
          workers.foreach(_ ! RequestCount)
        }
      
      case Count(c) =>
        counts = c :: counts
        if(counts.length == workers.length) {
          val workersTotal = counts.sum
          val masterTotal = masterWork.map(_.counted).getOrElse(0L)
          asker.foreach(_ ! Count(workersTotal + masterTotal))
          asker = None
          masterWork = None
        }
        
      case msg => println(msg)
    }
  }
  
  class Worker(val master : ActorRef, id : Int) extends Actor with ActorLogging {
    import context.{ actorOf, become }
    
    val work = new Bag()
    val md = MessageDigest.getInstance("SHA-1")
    
    var workers = Vector.empty[ActorRef]
    
    var thieves : List[ActorRef] = Nil
    
    var lifeline : Option[ActorRef] = None
        
    override def receive = {
      case InitWorkers(ws) =>
        workers = ws
        if(id > 0) {
          workers((workers.size + id - 1) % workers.size) ! LifelineReq(self)
        }
        become(idle)
    }
    
    def idle : Receive = {
      case LifelineDeal(w) =>
        work.merge(w)
        become(working)
        self ! Work
        
      case Steal(t) =>
        t ! NoDeal
        
      case LifelineReq(l) =>
        assert(lifeline.isEmpty)
        lifeline = Some(l)
        
      case RequestCount =>
        //log.info("RequestCount received. Size of work here: " + work.getSize())
        sender ! Count(work.counted)
    }
    
    def working : Receive = {
      case Work =>
        var n : Int = 500
        
        while(n > 0 && work.size != 0) {
          work.expand(md)
          n -= 1
        }
        
        if(work.size == 0) {
          // Notify stealers that work is unavailable here.
          for(t <- thieves) {
            t ! NoDeal
          }
          thieves = Nil
          
          // Go steal some work.
          val victim = if(workers.size > 1) {
            workers({
              val r = Random.nextInt(workers.size - 1)
              if(r >= id) r + 1 else r            
            })
          } else {
            self
          }
          
          victim ! Steal(self)
          become(waiting)
        } else {
          // Send work to the lifeline first if we can.
          for(l <- lifeline) {
            for(w <- work.split()) {
              master ! LocalTermination(1)
              l ! LifelineDeal(w)
              lifeline = None
            }
          }
          
          for(t <- thieves) {
            val msg = work.split().map(w => Deal(w)).getOrElse(NoDeal)
            //val msg = w.map(Deal(_)).getOrElse(NoDeal)
            t ! msg
          }
          thieves = Nil
          
          self ! Work
        }
      
      case Steal(t) =>
        thieves = t :: thieves
        
      case LifelineReq(l) =>
        assert(lifeline.isEmpty)
        lifeline = Some(l)
    }

    // State in which actor has attempted to steal and is expecting an answer.
    def waiting : Receive = {
      case Deal(w) =>
        //val osz = work.size
        //val nsz = w.size
        work.merge(w)
        //log.info(s"Received regular deal. Size was s=$osz, added s=$nsz")
        become(working)
        self ! Work
        
      case NoDeal =>
        val next = workers((workers.size + id - 1) % workers.size)
        next ! LifelineReq(self)
        master ! LocalTermination(-1)
        become(idle)
        
      case Steal(t) =>
        t ! NoDeal
        
      case LifelineReq(l) =>
        assert(lifeline.isEmpty)
        lifeline = Some(l)
    }
  }
}