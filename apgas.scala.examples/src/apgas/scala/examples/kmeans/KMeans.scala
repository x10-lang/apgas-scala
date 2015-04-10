package apgas.scala.examples
package kmeans

import apgas.Configuration

import apgas.scala.util._
import apgas.scala._

import java.util.Random

/**
 * A formulation of distributed KMeans using coarse-grained asyncs to implement
 * an allreduce pattern for cluster centers and counts.
 *
 * For a highly optimized and scalable, version of this benchmark see KMeans.x10
 * in the X10 Benchmarks (separate download from x10-lang.org)
 */
object KMeans {
  import Common.{ DIM, NUM_CENTROIDS, NUM_PLACES }

  class ClusterState extends Serializable {
    val clusters      = Array.ofDim[Float](NUM_CENTROIDS, DIM)
    val clusterCounts = Array.ofDim[Int](NUM_CENTROIDS)
  }
  
  def main(args: Array[String]): Unit = {
    Common.setup(numPlaces = NUM_PLACES)
    
    val numPoints : Int = try {
      args(0).toInt
    } catch {
      case _ : Throwable => Common.NUM_POINTS
    }
    
    val iterations : Int = 20

    val clusterState = PlaceLocalRef.forPlaces(places) {
      new ClusterState()
    }

    val currentClusters = PlaceLocalRef.forPlaces(places) {
      Array.ofDim[Float](NUM_CENTROIDS, DIM)
    }

    val points = PlaceLocalRef.forPlaces(places) {
      val rand = new Random(here.id)
      Array.fill[Float](numPoints / places.size, DIM) {
        rand.nextFloat()
      }
    }

    val centralCurrentClusters = Array.ofDim[Float](NUM_CENTROIDS, DIM)
    val centralNewClusters = Array.ofDim[Float](NUM_CENTROIDS, DIM)
    val centralClusterCounts = Array.ofDim[Int](NUM_CENTROIDS)

    // arbitrarily initialize central clusters to first few points
    for (i <- 0 until NUM_CENTROIDS; j <- 0 until DIM) {
      centralCurrentClusters(i)(j) = points()(i)(j)
    }

    var iter = 1
    var converged = false

    while (iter <= iterations && !converged) {
      println("Iteration: " + iter)

      finish {
        for (place <- places) {
          async {
            val placeClusters = at(place) {
              val clusters = currentClusters()

              for (i <- 0 until NUM_CENTROIDS; j <- 0 until DIM) {
                clusters(i)(j) = centralCurrentClusters(i)(j)
              }

              val state = clusterState()
              val newClusters = state.clusters
              for (i <- 0 until NUM_CENTROIDS) {
                for (j <- 0 until DIM) {
                  newClusters(i)(j) = 0.0f
                }
              }
              val counts = state.clusterCounts
              for (i <- 0 until NUM_CENTROIDS) {
                counts(i) = 0
              }

              /* compute new clusters and counters */
              val ps = points()

              for (p <- 0 until ps.length) {
                val closest = Common.closestCentroid(ps(p), clusters)

                for (d <- 0 until DIM) {
                  newClusters(closest)(d) += ps(p)(d)
                }
                counts(closest) += 1
              }

              state
            }

            // Combine place clusters to central
            synchronized {
              for (i <- 0 until NUM_CENTROIDS) {
                for (j <- 0 until DIM) {
                  centralNewClusters(i)(j) += placeClusters.clusters(i)(j)
                }
              }

              for (j <- 0 until NUM_CENTROIDS) {
                centralClusterCounts(j) += placeClusters.clusterCounts(j)
              }
            }
          }
        }
      }

      for (k <- 0 until NUM_CENTROIDS; d <- 0 until DIM) {
        centralNewClusters(k)(d) /= centralClusterCounts(k)
      }

      def testConvergence(): Boolean = {
        import scala.math.abs

        for (i <- 0 until NUM_CENTROIDS; j <- 0 until DIM) {
          if (abs(centralCurrentClusters(i)(j) - centralNewClusters(i)(j)) > 0.0001) {
            return false
          }
        }
        return true
      }

      iter += 1
      converged = testConvergence()

      for (i <- 0 until NUM_CENTROIDS; j <- 0 until DIM) {
        centralCurrentClusters(i)(j) = centralNewClusters(i)(j)
      }

      for (i <- 0 until NUM_CENTROIDS; j <- 0 until DIM) {
        centralNewClusters(i)(j) = 0.0f
      }

      for (i <- 0 until NUM_CENTROIDS) {
        centralClusterCounts(i) = 0
      }
    }

    for (d <- 0 until DIM) {
      for (k <- 0 until NUM_CENTROIDS) {
        if (k > 0) {
          print(" ")
        }
        print(centralCurrentClusters(k)(d))
      }
      println()
    }
  }
}