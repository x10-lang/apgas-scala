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
  import Common._

  class LocalData(numPoints: Int) extends Serializable {
    val points    = Array.ofDim[Float](numPoints, DIM)
    val centroids = Array.ofDim[Float](NUM_CENTROIDS, DIM)
    val counts    = Array.ofDim[Int](NUM_CENTROIDS)
  }

  def main(args: Array[String]): Unit = {
    val numPlaces = try {
      args(0).toInt
    } catch {
      case _ : Throwable => NUM_PLACES
    }
    Common.setup(numPlaces = numPlaces)

    val iterations = 50

    printf("K-Means: %d clusters, %d points, %d dimensions, %d places\n",
      NUM_CENTROIDS, NUM_POINTS, DIM, numPlaces)

    val localState = GlobalRef.forPlaces(places) {
      val d = new LocalData(NUM_POINTS / numPlaces)
      copy2DArray(Common.pointsForWorker(here.id, numPlaces).toArray, d.points)
      d
    }

    val centroids    = Array.ofDim[Float](NUM_CENTROIDS, DIM)
    val newCentroids = Array.ofDim[Float](NUM_CENTROIDS, DIM)
    val newCounts    = Array.ofDim[Int](NUM_CENTROIDS)

    // arbitrarily initialize central clusters to first few points
    for (i <- 0 until NUM_CENTROIDS; j <- 0 until DIM) {
      centroids(i)(j) = localState().points(i)(j)
    }

    var time = System.nanoTime()

    var iter = 1
    var converged = false

    while (iter <= iterations && !converged) {
      print(".")
      finish {
        for (place <- places) {
          async {
            val placeState = at(place) {
              reset2DArray(localState().centroids)
              resetArray(localState().counts)

              val lps = localState().points
              for (p <- 0 until lps.length) {
                val c = closestCentroid(lps(p), centroids)
                for (d <- 0 until DIM) {
                  localState().centroids(c)(d) += lps(p)(d)
                }
                localState().counts(c) += 1
              }
              localState()
            }

            // Combine place clusters to central
            newCentroids.synchronized {
              for (k <- 0 until NUM_CENTROIDS; d <- 0 until DIM) {
                newCentroids(k)(d) += placeState.centroids(k)(d)
              }
            }
            newCounts.synchronized {
              for (j <- 0 until NUM_CENTROIDS) {
                newCounts(j) += placeState.counts(j)
              }
            }
          }
        }
      }

      for (k <- 0 until NUM_CENTROIDS; d <- 0 until DIM) {
        newCentroids(k)(d) /= newCounts(k)
      }

      iter += 1
      converged = withinEpsilon(centroids, newCentroids)

      copy2DArray(newCentroids, centroids)
      reset2DArray(newCentroids)
      resetArray(newCounts)
    }
    time = System.nanoTime() - time
    
    printCentroids(centroids)

    printf("Time per iteration %.3f ms\n", time / 1e6 / iter)
  }
}