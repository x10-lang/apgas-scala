package apgas.scala.examples.kmeans

import akka.actor._
import akka.pattern.ask
import akka.util.Timeout
import scala.concurrent.duration._
import scala.concurrent.Await
import java.util.concurrent.TimeUnit
import scala.collection.mutable.ArrayBuffer

object KMeansAkka {
  import Common._
  
  def main(args : Array[String]) : Unit = {
    implicit val timeout : Timeout = Timeout(1 hour)
    
    val numWorkers = Common.NUM_PLACES
    
    val system = ActorSystem("kmeans-system")
    
    implicit val dispatcher = system.dispatcher
    
    printf("K-Means: %d clusters, %d points, %d dimensions, %d workers\n", 
        NUM_CENTROIDS, NUM_POINTS, DIM, numWorkers)
    
    val master = system.actorOf(Props(new Master(numWorkers)), name = "master")
    
    val t0 = System.nanoTime()
    val result = Await.result(master ? Init(50), 1 hour)
    result match {
      case Centroids(centroids) =>
        val t1 = System.nanoTime()
        Common.printCentroids(centroids)
        val time = (t1 - t0)
        printf("Time per iteration %.3f ms\n", time / 1e6 / 50.0)
        
      case _ =>
        Console.err.println("Unexpected result: " + result)
    }
    
    system.shutdown()
  }
  
  case class Init(maxIterations : Int)
  case object Run
  case class Centroids(centroids : Array[Array[Float]])
  case class Updated(centroids : Array[Array[Float]], counts : Array[Int])

  class Master(numWorkers : Int) extends Actor {
    val workers = Vector.tabulate(numWorkers) { i =>
      context.actorOf(Props(new Worker(i, numWorkers)), name = s"worker-$i")
    }
    
    val centroids    = Array.ofDim[Float](NUM_CENTROIDS, DIM)
    val newCentroids = Array.ofDim[Float](NUM_CENTROIDS, DIM)
    val newCounts    = Array.ofDim[Int](NUM_CENTROIDS)
    
    // Arbitrarily initialize centroids to first few points
    val first = pointsForWorker(0, numWorkers).take(NUM_CENTROIDS)
    copy2DArray(first.toArray, centroids)
    
    var requester : Option[ActorRef] = None
    var itersLeft = 0
    var updates = 0
    
    override def receive = {
      case Init(maxIterations) =>
        requester = Some(sender)
        itersLeft = maxIterations
        self ! Run
        
      case Run =>
        if(itersLeft <= 0) {
          requester.foreach(_ ! Centroids(centroids))
        } else {
          itersLeft -= 1
          print(".")
          
          reset2DArray(newCentroids)
          resetArray(newCounts)
        
          updates = 0

          workers.foreach(_ ! Centroids(centroids))
        }
        
      case Updated(workerCentroids, workerCounts) =>
        for(i <- 0 until NUM_CENTROIDS; j <- 0 until DIM) {
          newCentroids(i)(j) += workerCentroids(i)(j)
        }
        for(i <- 0 until NUM_CENTROIDS) {
          newCounts(i) += workerCounts(i)
        }
        updates += 1
        
        if(updates == numWorkers) {
          for(i <- 0 until NUM_CENTROIDS; j <- 0 until DIM) {
            newCentroids(i)(j) /= newCounts(i)
          }
          
          if(Common.withinEpsilon(centroids, newCentroids)) {
            itersLeft = 0
          }
          Common.copy2DArray(newCentroids, this.centroids)
                    
          self ! Run
        }
    }
  }
  
  class Worker(id : Int, numWorkers : Int) extends Actor {
    val points = Common.pointsForWorker(id, numWorkers).toArray
    
    val localCentroids = Array.ofDim[Float](NUM_CENTROIDS, DIM)
    val localCounts    = Array.ofDim[Int](NUM_CENTROIDS)
    
    override def receive = {
      case Centroids(centroids) =>
        copy2DArray(centroids, localCentroids)
        resetArray(localCounts)
        
        for (p <- 0 until points.length) {
          val c = Common.closestCentroid(points(p), centroids)
          for (d <- 0 until DIM) {
            localCentroids(c)(d) += points(p)(d)
          }
          localCounts(c) += 1
        }
        sender ! Updated(localCentroids, localCounts)
    }
  }
}