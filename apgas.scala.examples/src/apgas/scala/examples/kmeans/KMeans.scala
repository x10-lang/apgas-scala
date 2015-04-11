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
  import Common.{ DIM, NUM_CENTROIDS }

  class ClusterState extends PlaceLocal {
    val clusters = Array.ofDim[Float](NUM_CENTROIDS, DIM)
    val clusterCounts = Array.ofDim[Int](NUM_CENTROIDS)
  }

  def main(args: Array[String]): Unit = {
    val numPlaces = try {
      args(0).toInt
    } catch {
      case _ : Throwable => Common.NUM_PLACES
    }
    Common.setup(numPlaces = numPlaces)

    val numPoints = Common.NUM_POINTS

    val iterations = 50

    printf("K-Means: %d clusters, %d points, %d dimensions, %d places\n",
      NUM_CENTROIDS, numPoints, DIM, numPlaces)

    val clusterState = PlaceLocal.forPlaces(places) {
      new ClusterState()
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

    var time = System.nanoTime()

    var iter = 1
    var converged = false

    while (iter <= iterations && !converged) {
      print(".")
      finish {
        for (place <- places) {
          async {
            val placeClusters = at(place) {
              val state = clusterState
              val newClusters = state.clusters
              
              for (k <- 0 until NUM_CENTROIDS; d <- 0 until DIM) {
                newClusters(k)(d) = 0.0f
              }
              val counts = state.clusterCounts
              for (i <- 0 until NUM_CENTROIDS) {
                counts(i) = 0
              }

              /* compute new clusters and counters */
              val ps = points()

              for (p <- 0 until ps.length) {
                val closest = Common.closestCentroid(ps(p), centralCurrentClusters)

                for (d <- 0 until DIM) {
                  newClusters(closest)(d) += ps(p)(d)
                }
                counts(closest) += 1
              }

              state
            }

            // Combine place clusters to central
            centralNewClusters.synchronized {
              for (k <- 0 until NUM_CENTROIDS; d <- 0 until DIM) {
                centralNewClusters(k)(d) += placeClusters.clusters(k)(d)
              }
            }
            centralClusterCounts.synchronized {
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

      iter += 1
      converged = Common.withinEpsilon(centralCurrentClusters, centralNewClusters)

      Common.copy2DArray(centralNewClusters, centralCurrentClusters)
      

      for (i <- 0 until NUM_CENTROIDS; j <- 0 until DIM) {
        centralNewClusters(i)(j) = 0.0f
      }

      for (i <- 0 until NUM_CENTROIDS) {
        centralClusterCounts(i) = 0
      }
    }
    time = System.nanoTime() - time
    
    Common.printCentroids(centralCurrentClusters)

    printf("Time per iteration %.3f ms\n", time / 1e6 / iter)
  }
}