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

  class ClusterState extends Serializable {
    val centroids = Array.ofDim[Float](NUM_CENTROIDS, DIM)
    val counts = Array.ofDim[Int](NUM_CENTROIDS)
  }

  class LocalData(numPoints: Int) extends Serializable {
    val points = Array.ofDim[Float](numPoints, DIM)
    val clusterState = new ClusterState
  }

  def main(args: Array[String]): Unit = {
    val numPlaces = try {
      args(1).toInt
    } catch {
      case _: Throwable => NUM_PLACES
    }

    val numPoints = try {
      args(0).toInt
    } catch {
      case _: Throwable =>
        println("Defaulting to 2M points")
        2000000
    }

    Common.setup(numPlaces = numPlaces)
    val numThreads = System.getProperty(Configuration.APGAS_THREADS).toInt

    val iterations = 50

    println("Warmup...")
    run(numPlaces, numPoints, iterations, warmup = true)
    run(numPlaces, numPoints, iterations)
  }

  def run(numPlaces: Int, numPoints: Int, iterations0: Int, warmup: Boolean = false): Unit = {
    val iterations = if (warmup) { iterations0 / 10 } else iterations0

    if (!warmup) {
      printf("K-Means: %d clusters, %d points, %d dimensions, %d places\n",
        NUM_CENTROIDS, numPoints, DIM, numPlaces)
    }

    val localState = GlobalRef.forPlaces(places) {
      val d = new LocalData(numPoints / numPlaces)
      copy2DArray(Common.pointsForWorker(here.id, numPlaces, numPoints).toArray, d.points)
      d
    }

    val centroids = Array.ofDim[Float](NUM_CENTROIDS, DIM)
    val newCentroids = Array.ofDim[Float](NUM_CENTROIDS, DIM)
    val newCounts = Array.ofDim[Int](NUM_CENTROIDS)

    // arbitrarily initialize central clusters to first few points
    for (i <- 0 until NUM_CENTROIDS; j <- 0 until DIM) {
      centroids(i)(j) = localState().points(i)(j)
    }

    var time = System.nanoTime()

    var iter = 1
    var converged = false

    while (iter <= iterations && !converged) {
      if (!warmup) print(".")
      finish {
        for (place <- places) {
          async {
            val placeState = at(place) {
              val clusterState = localState().clusterState
              reset2DArray(clusterState.centroids)
              resetArray(clusterState.counts)

              val lps = localState().points
              for (p <- 0 until lps.length) {
                val c = closestCentroid(lps(p), centroids)
                for (d <- 0 until DIM) {
                  clusterState.centroids(c)(d) += lps(p)(d)
                }
                clusterState.counts(c) += 1
              }
              clusterState
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

    // printCentroids(centroids)

    if (!warmup) {
      println("")
      printf("[kmeans-apgas-%d] Time per iteration %.3f ms\n", numPlaces, time / 1e6 / iter)
      printf("[kmeans-apgas-%d] Iterations per sec: %.3f\n", numPlaces, iter / (time / 1e9))
    }
  }
}