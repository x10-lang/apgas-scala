package apgas.scala.examples
package uts

import akka.actor._
import java.security.MessageDigest
import scala.util.{ Try, Success, Failure, Random }
import akka.pattern.ask
import akka.pattern.gracefulStop
import akka.util.Timeout
import scala.concurrent.duration._
import scala.concurrent.Await
import java.util.concurrent.TimeUnit

object UTSAkka {
  import Common._
  
  // workers protocol
  case class Steal(thief : ActorRef)
  case class Deal(work : Bag)
  case object NoDeal
  case object Work
  case class LifelineReq(actor : ActorRef)
  case class LifelineDeal(work : Bag)
  
  // init/termination
  case class Compute(seed : Int, depth : Int)
  case class InitWorkers(workers : Vector[ActorRef])
  case class WorkerReady(workerID : Int)
  case class LocalTermination(workerID : Int, requested : Int, dealt : Int)
  case object RequestCount
  case class Count(count : Long)
  
  def main(args : Array[String]) : Unit = {
    implicit val timeout : Timeout = Timeout(1 hour)
    
    val seed = 19
    val depth = try {
      args(0).toInt
    } catch {
      case _ : Throwable =>
        println("Defaulting to maxDepth=13...")
        13
    }
    
    val numWorkers = try {
      args(1).toInt
    } catch {
      case _ : Throwable =>
        println("Defaulting to 2 workers...")
        2
    }
    
    val system = ActorSystem("uts-system")
    
    implicit val dispatcher = system.dispatcher
    
    val master = system.actorOf(Props(new Master(numWorkers)), name = "master")
    
    println("Warmup...")
    Await.result(master ? Compute(seed, (depth - 2) max 5), 1 hour)
    
    println("Working...")
    val t0 = System.nanoTime()
    val result = Await.result(master ? Compute(seed, depth), 1 hour)
    
    result match {
      case Count(s) =>
        val t1 = System.nanoTime()
        printResult(s"actors-$numWorkers", depth, s.toInt, t0, t1)
        checkResult(depth, s)
         
      case _ =>
        println("Unexpected result: " + result)
    }
    
    system.shutdown()
  }
  
  class Master(numWorkers : Int) extends Actor with ActorLogging {
    val workers = Vector.tabulate[ActorRef](numWorkers) { i =>
      context.actorOf(Props(new Worker(self, i)), name = s"worker-$i")
    }
    
    var seed : Int = 0
    var maxDepth : Int = 0
    var asker : Option[ActorRef] = None
    var counts : List[Long] = Nil
    var masterWork : Option[Bag] = None
    var workersReady : Int = 0
    
    val dealtCounts = Array.fill[Int](numWorkers)(0)
    val requestedCounts = Array.fill[Int](numWorkers)(0)
    
    override def receive = {
      case Compute(s, d) =>
        seed = s
        maxDepth = d
        asker = Some(sender)
        counts = Nil
        workersReady = 0
        
        // Set-up the topology.
        for(worker <- workers) {
          worker ! InitWorkers(workers)
        }
        
        for(i <- 0 until numWorkers) {
          dealtCounts(i) = 0
          requestedCounts(i) = 0
        }
          
      case WorkerReady(_) =>
        workersReady += 1
        if(workersReady == numWorkers) {
          val b = new Bag()
          masterWork = Some(b)
          b.initialize(MessageDigest.getInstance("SHA-1"), seed, maxDepth)
        
          dealtCounts(numWorkers - 1) = 1
          workers(0) ! LifelineDeal(b)
        }
        
      case l @ LocalTermination(workerID, requested, dealt) =>
        requestedCounts(workerID) += requested
        dealtCounts(workerID)     += dealt
              
        var finished = true
        for(i <- 0 until numWorkers if finished) {
          if(requestedCounts(i) != dealtCounts((numWorkers + i - 1) % numWorkers)) {
            finished = false
          }
        }
        
        if(finished) {
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
    
    var dealtCount : Int = 0
    var requestedCount : Int = 0
        
    override def receive = idle
    
    def idle : Receive = {
      case InitWorkers(ws) =>
        work.clear()
        
        workers = ws
        if(id < workers.size - 1) {
          lifeline = Some(ws(id + 1))
        } else {
          lifeline = None
        }
        sender ! WorkerReady(id)
      
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
          for(t <- thieves) {
            t ! NoDeal
          }
          thieves = Nil
          
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
          for(l <- lifeline) {
            for(w <- work.split()) {
              dealtCount += 1
              l ! LifelineDeal(w)
              lifeline = None
            }
          }
          
          for(t <- thieves) {
            val msg = work.split().map(Deal(_)).getOrElse(NoDeal)
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
        work.merge(w)
        become(working)
        self ! Work
        
      case NoDeal =>
        val prev = workers((workers.size + id - 1) % workers.size)
        requestedCount += 1
        prev ! LifelineReq(self)
        master ! LocalTermination(id, requestedCount, dealtCount)
        requestedCount = 0
        dealtCount = 0
        become(idle)
        
      case Steal(t) =>
        t ! NoDeal
        
      case LifelineReq(l) =>
        assert(lifeline.isEmpty)
        lifeline = Some(l)
    }
  }
}