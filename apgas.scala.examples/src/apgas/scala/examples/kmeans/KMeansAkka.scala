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
    val numPoints = try {
      args(0).toInt
    } catch {
      case _ : Throwable =>
        println("Defaulting to 2M points.")
        2000000
    }
    
    val numWorkers = try {
      args(1).toInt
    } catch {
      case _ : Throwable => NUM_PLACES
    }
    
    val iterations = 50
    
    val system = ActorSystem("kmeans-system")
    
    implicit val dispatcher = system.dispatcher
    
    val master = system.actorOf(Props(new Master(numWorkers, numPoints)), name = "master")
    
    println("Warmup...")
    run(master, numWorkers, numPoints, iterations, warmup=true)
    
    printf("K-Means: %d clusters, %d points, %d dimensions, %d workers\n", 
        NUM_CENTROIDS, numPoints, DIM, numWorkers)
    
    run(master, numWorkers, numPoints, iterations)
    
    system.shutdown()
  }
  
  def run(master: ActorRef, numWorkers: Int, numPoints: Int, iterations0: Int, warmup: Boolean=false) : Unit = {
    val iterations = if(warmup) { iterations0 / 10 } else { iterations0 }
    
    implicit val timeout : Timeout = Timeout(1 hour)
    
    val t0 = System.nanoTime()
    
    val result = Await.result(master ? Init(iterations, warmup), 1 hour)
    result match {
      case FinalCentroids(centroids, iters) =>
        val t1 = System.nanoTime()
        if(!warmup) Common.printCentroids(centroids)
        val time = (t1 - t0)
        
        if(!warmup) {
          printf("[kmeans-akka-s-%d] Time per iteration %.3f ms\n", numWorkers, time / 1e6 / 50.0)
          printf("[kmeans-akka-s-%d] Iterations per sec: %.3f\n", numWorkers, iters / (time / 1e9))
        }
        
      case _ =>
        Console.err.println("Unexpected result: " + result)
    }
  }
  
  case class Init(maxIterations : Int, isWarmup: Boolean)
  case object Run
  case class Centroids(centroids : Array[Array[Float]])
  case class FinalCentroids(centroids : Array[Array[Float]], iterations: Int)
  case class Updated(centroids : Array[Array[Float]], counts : Array[Int])

  class Master(numWorkers : Int, numPoints : Int) extends Actor {
    val workers = Vector.tabulate(numWorkers) { i =>
      context.actorOf(Props(new Worker(i, numWorkers, numPoints)), name = s"worker-$i")
    }
    
    val centroids    = Array.ofDim[Float](NUM_CENTROIDS, DIM)
    val newCentroids = Array.ofDim[Float](NUM_CENTROIDS, DIM)
    val newCounts    = Array.ofDim[Int](NUM_CENTROIDS)
    
    // Arbitrarily initialize centroids to first few points
    val first = pointsForWorker(0, numWorkers, numPoints).take(NUM_CENTROIDS)
    copy2DArray(first.toArray, centroids)
    
    var requester : Option[ActorRef] = None
    var isWarmup = false
    var maxIterations = 0
    var itersLeft = 0
    var updates = 0
    
    override def receive = {
      case Init(maxIter, warmup) =>
        requester = Some(sender)
        isWarmup = warmup
        maxIterations = maxIter
        itersLeft = maxIter
        self ! Run
        
      case Run =>
        if(itersLeft <= 0) {
          requester.foreach(_ ! FinalCentroids(centroids, maxIterations - itersLeft))
        } else {
          itersLeft -= 1
          if(!isWarmup) print(".")
          
          reset2DArray(newCentroids)
          resetArray(newCounts)
        
          updates = 0

          workers.foreach(_ ! Centroids(centroids))
        }
        
      case Updated(workerCentroids, workerCounts) =>
        var k = 0
        while (k < NUM_CENTROIDS) {
          var d = 0
          while (d < DIM) {
            newCentroids(k)(d) += workerCentroids(k)(d)
            d += 1
          }
          k += 1
        }

        k = 0
        while (k < NUM_CENTROIDS) {
          newCounts(k) += workerCounts(k)
          k += 1
        }
        updates += 1
        
        if(updates == numWorkers) {
          var k = 0
          while (k < NUM_CENTROIDS) {
            var d = 0
            while (d < DIM) {
              newCentroids(k)(d) /= newCounts(k)
              d += 1
            }
            k += 1
          }
                      
          if(Common.withinEpsilon(centroids, newCentroids)) {
            itersLeft = 0
          }
          Common.copy2DArray(newCentroids, this.centroids)
                    
          self ! Run
        }
    }
  }
  
  class Worker(id : Int, numWorkers : Int, numPoints : Int) extends Actor {
    val points = Common.pointsForWorker(id, numWorkers, numPoints).toArray
    
    val localCentroids = Array.ofDim[Float](NUM_CENTROIDS, DIM)
    val localCounts    = Array.ofDim[Int](NUM_CENTROIDS)
    
    override def receive = {
      case Centroids(centroids) =>
        copy2DArray(centroids, localCentroids)
        resetArray(localCounts)
        
        var p = 0
        while (p < points.length) {
          val c = Common.closestCentroid(points(p), centroids)
          var d = 0
          while (d < DIM) {
            localCentroids(c)(d) += points(p)(d)
            d += 1
          }
          localCounts(c) += 1
          p += 1
        }
        sender ! Updated(localCentroids, localCounts)
    }
  }
}