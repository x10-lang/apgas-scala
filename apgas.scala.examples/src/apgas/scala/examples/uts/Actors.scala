package apgas.scala.examples
package uts

import akka.actor._
import akka.pattern.ask
import akka.util.Timeout

import scala.util.{ Try, Success, Failure }

import scala.concurrent.duration._
import java.util.concurrent.TimeUnit


object Actors {
  def main(args : Array[String]) : Unit = {
    val seed = 19
    val depth = try {
      args(0).toInt
    } catch {
      case _ : Throwable => 13
    }
    
    implicit val timeout : Timeout = Timeout(10, TimeUnit.HOURS)
    
    val system = ActorSystem("uts-system")
    
    implicit val dispatcher = system.dispatcher
    
    val master = system.actorOf(Props(new Master(2)), name = "master")
    
    val t0 = System.nanoTime()
    
    (master ? ComputeTreeSize(seed, depth)).onComplete { t =>
      t match {
        case Success(TreeSize(s)) =>
          val t1 = System.nanoTime()
          Serial.printResult("actors", depth, s, t0, t1)
          Serial.checkResult(depth, s)
          
        case _ =>
           println("Unexpected result: " + t)
      }
      system.shutdown()
    }
  }
  
  case class ComputeTreeSize(seed : Int, maxDepth : Int)
  case class Distribute(workList : WorkList)
  case class TreeSize(size : Int)
  
  case class Work(workList : WorkList)
  case class Worked(workerID : Int, count : Int, leftover : WorkList)
  
  class Master(numWorkers : Int) extends Actor {
    private val workers = Vector.tabulate[ActorRef](numWorkers) { i =>
      context.actorOf(Props(new Worker(i)), name = s"worker-$i")
    }
    private val working = Array.fill[Boolean](numWorkers)(false)
    
    private var asker : Option[ActorRef] = None
    private var count : Int = 0
    
    def receive = {
      case ComputeTreeSize(seed, maxDepth) =>
        asker = Some(sender)
        val wl = WorkList.init(Node.initial(seed, maxDepth))
        self ! Distribute(wl)
        
      case Distribute(workList) =>
        val idle = working.count(!_)
        
        var wl = workList
        var n = 0
        
        println(s"** $idle idle workers")
        println(wl)
        
        for(i <- 0 until numWorkers) {
          if(!working(i)) {
            if(n < idle - 1) {
              val (wl1, wl2) = wl.split
              workers(i) ! Work(wl1)
              working(i) = true
              wl = wl2
              println(s" * gave partial to $i")
              println(wl1)
            } else {
              workers(i) ! Work(wl)
              working(i) = true
              println(s" * gave full    to $i")
              println(wl)
            }
            n += 1
          }
        }
        
      case Worked(i, s, l) =>
        count += s
        
        working(i) = false
        
        if(!l.isEmpty) {
          self ! Distribute(l)
        } else if(working.forall(!_)) {
          asker.foreach(_ ! TreeSize(count))
          asker = None
        }
    }
  }
  
  class Worker(id : Int) extends Actor {
    def receive = {
      case Work(wl) =>
        //println(s"Worker $id is working...")
        
        val (count, leftover) = Serial.countMax(wl, 500)
        
        //println(s"Worked $id is done [$count]...")
        sender ! Worked(id, count, leftover)
    }
  }
}