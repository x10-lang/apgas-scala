package apgas.scala.examples.kmeans

import akka.actor._
import scala.collection.mutable.ArrayBuffer

object KMeansAkka {
  import Common.{ DIM, NUM_CENTROIDS }
  
  def main(args : Array[String]) : Unit = {
    println("OK")
  }
  
  case class Run(maxIterations : Int)
  case class Dimensions(numClusters : Int, dimensions : Int)
  case object InitializePoints
  
  case class Update(centroids : Array[Array[Float]])
  case class Updated(centroids : Array[Array[Float]], counts : Array[Int])

  class Master(numWorkers : Int) extends Actor {
    override def receive = {
      case Run(iter) =>
        
    }
  }
  
  class Worker(id : Int) extends Actor {
    var clusters      = Array.ofDim[Float](0, 0)
    var clusterCounts = Array.ofDim[Int](0)
    
    val points         = ArrayBuffer(Common.pointsForWorker(id) : _*)
    val localCentroids = Array.ofDim[Float](NUM_CENTROIDS, DIM)
    val localCounts    = Array.ofDim[Int](NUM_CENTROIDS)
    
    override def receive = {
      case Update(centroids) =>
        // Copy updated centroids.
        for (i <- 0 until NUM_CENTROIDS; j <- 0 until DIM) {
          localCentroids(i)(j) = centroids(i)(j)
        }
        
        // Reset counts
        for (i <- 0 until NUM_CENTROIDS) {
          localCounts(i) = 0
        }
        
        for (p <- 0 until points.length) {
          val closest = Common.closestCentroid(points(p), clusters)
          for (d <- 0 until DIM) {
            localCentroids(closest)(d) += points(p)(d)
          }
          localCounts(closest) += 1
        }
        
        sender ! Updated(localCentroids, localCounts)
    }
  }
}