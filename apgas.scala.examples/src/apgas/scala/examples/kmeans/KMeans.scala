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

  val DEFAULT_THREADS = 2

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
      case _: Throwable => DEFAULT_PLACES
    }
    System.setProperty(Configuration.APGAS_PLACES,
      String.valueOf(numPlaces))

    if (System.getProperty(Configuration.APGAS_THREADS) == null)
      System.setProperty(Configuration.APGAS_THREADS,
        String.valueOf(DEFAULT_THREADS))

    val numPoints = try {
      args(0).toInt
    } catch {
      case _: Throwable =>
        println("Defaulting to 2M points")
        2000000
    }

    val iterations = 50

    println("Warmup...")

    run(numPoints, iterations, warmup = true)
    run(numPoints, iterations)
  }

  def run(numPoints: Int, iterations0: Int, warmup: Boolean = false): Unit = {
    val numPlaces = System.getProperty(Configuration.APGAS_PLACES).toInt
    val iterations = if (warmup) { iterations0 / 10 } else iterations0

    if (!warmup) {
      printf("K-Means: %d clusters, %d points, %d dimensions, %d places, %d threads\n",
        NUM_CENTROIDS, numPoints, DIM, numPlaces, System.getProperty(Configuration.APGAS_THREADS).toInt)
    }

    val localState = GlobalRef.forPlaces(places) {
      val d = new LocalData(numPoints / numPlaces)
      copy2DArray(Common.pointsForWorker(here.id, numPlaces, numPoints).toArray, d.points)
      d
    }

    val centralClusterState = new ClusterState();
    val centralClusterStateGr = SharedRef.make(centralClusterState)
    val currentCentroids = Array.ofDim[Float](NUM_CENTROIDS, DIM)

    // arbitrarily initialize central clusters to first few points
    for (i <- 0 until NUM_CENTROIDS; j <- 0 until DIM) {
      currentCentroids(i)(j) = localState().points(i)(j)
    }

    var time = System.nanoTime()

    var iter = 1
    var converged = false

    while (iter <= iterations && !converged) {
      if (!warmup) print(".")
      finish {
        for (place <- places) {
          asyncAt(place) {
            val placeState = localState().clusterState
            reset2DArray(placeState.centroids)
            resetArray(placeState.counts)

            val lps = localState().points
            var p = 0
            while (p < lps.length) {
              val c = closestCentroid(lps(p), currentCentroids)
              var d = 0
              while (d < DIM) {
                placeState.centroids(c)(d) += lps(p)(d)
                d += 1
              }
              placeState.counts(c) += 1
              p += 1
            }

            asyncAt(centralClusterStateGr.home()) {
              // Combine place clusters to central
              val newCentroids = centralClusterStateGr().centroids
              newCentroids.synchronized {
                var k: Int = 0
                while (k < NUM_CENTROIDS) {
                  var d: Int = 0
                  while (d < DIM) {
                    newCentroids(k)(d) += placeState.centroids(k)(d)
                    d += 1
                  }
                  k += 1
                }
              }
              val newCounts = centralClusterStateGr().counts
              newCounts.synchronized {
                var j = 0
                while (j < NUM_CENTROIDS) {
                  newCounts(j) += placeState.counts(j)
                  j += 1
                }
              }
            }
          }
        }
      }

      val newCentroids = centralClusterState.centroids
      val newCounts = centralClusterState.counts
      var k: Int = 0
      while (k < NUM_CENTROIDS) {
        var d: Int = 0
        while (d < DIM) {
          newCentroids(k)(d) /= newCounts(k)
          d += 1
        }
        k += 1
      }

      iter += 1
      converged = withinEpsilon(currentCentroids, newCentroids)

      copy2DArray(newCentroids, currentCentroids)
      reset2DArray(newCentroids)
      resetArray(newCounts)
    }
    time = System.nanoTime() - time

    if (!warmup) {
      printCentroids(currentCentroids)
      println("")
      printf("[kmeans-apgas-%d] Time per iteration %.3f ms\n", numPlaces, time / 1e6 / iter)
      printf("[kmeans-apgas-%d] Iterations per sec: %.3f\n", numPlaces, iter / (time / 1e9))
    }

    localState.free()
  }
}
