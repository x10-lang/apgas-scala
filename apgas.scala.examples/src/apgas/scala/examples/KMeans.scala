package apgas.scala.examples

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
  val DIM = 2
  val CLUSTERS = 4
  val NUM_PLACES = 5

  class ClusterState extends Serializable {
    val clusters = Array.ofDim[Float](CLUSTERS, DIM)
    val clusterCounts = Array.ofDim[Int](CLUSTERS)
  }

  def main(args: Array[String]): Unit = {
    val numPoints: Int = try {
      args(0).toInt
    } catch {
      case _: Throwable => 2000
    }
    val iterations: Int = try {
      args(0).toInt
    } catch {
      case _: Throwable => 20
    }

    // PS: I need this because java is not the default on my system..
    System.setProperty(Configuration.APGAS_JAVA, "java8")

    if (System.getProperty(Configuration.APGAS_PLACES) == null) {
      System.setProperty(Configuration.APGAS_PLACES, NUM_PLACES.toString())
    }

    val globalClusterState = PlaceLocalRef.forPlaces[ClusterState](places) {
      new ClusterState()
    }

    val globalCurrentClusters = PlaceLocalRef.forPlaces[Array[Array[Float]]](places) {
      Array.ofDim[Float](CLUSTERS, DIM)
    }

    val globalPoints = PlaceLocalRef.forPlaces(places) {
      val rand = new Random(here.id)
      Array.fill[Float](numPoints / places.size, DIM) {
        rand.nextFloat()
      }
    }

    val centralCurrentClusters = Array.ofDim[Float](CLUSTERS, DIM)
    val centralNewClusters = Array.ofDim[Float](CLUSTERS, DIM)
    val centralClusterCounts = Array.ofDim[Int](CLUSTERS)

    // arbitrarily initialize central clusters to first few points
    for (i <- 0 until CLUSTERS; j <- 0 until DIM) {
      centralCurrentClusters(i)(j) = globalPoints()(i)(j)
    }

    var iter = 1
    var converged = false

    while (iter <= iterations && !converged) {
      println("Iteration: " + iter)

      finish {
        for (place <- places) {
          async {
            val placeClusters = at(place) {
              val currentClusters = globalCurrentClusters()

              for (i <- 0 until CLUSTERS; j <- 0 until DIM) {
                currentClusters(i)(j) = centralCurrentClusters(i)(j)
              }

              val clusterState = globalClusterState()
              val newClusters = clusterState.clusters
              for (i <- 0 until CLUSTERS) {
                for (j <- 0 until DIM) {
                  newClusters(i)(j) = 0.0f
                }
              }
              val clusterCounts = clusterState.clusterCounts
              for (i <- 0 until CLUSTERS) {
                clusterCounts(i) = 0
              }

              /* compute new clusters and counters */
              val points = globalPoints()

              for (p <- 0 until points.length) {
                var closest = -1
                var closestDist = Float.MaxValue
                for (k <- 0 until CLUSTERS) {
                  var dist = 0.0f
                  for (d <- 0 until DIM) {
                    val tmp = points(p)(d) - currentClusters(k)(d)
                    dist += tmp * tmp
                  }
                  if (dist < closestDist) {
                    closestDist = dist
                    closest = k
                  }
                }

                for (d <- 0 until DIM) {
                  newClusters(closest)(d) += points(p)(d)
                }
                clusterCounts(closest) = clusterCounts(closest) + 1
              }

              clusterState
            }

            // Combine place clusters to central
            synchronized {
              for (i <- 0 until CLUSTERS) {
                for (j <- 0 until DIM) {
                  centralNewClusters(i)(j) += placeClusters.clusters(i)(j)
                }
              }

              for (j <- 0 until CLUSTERS) {
                centralClusterCounts(j) += placeClusters.clusterCounts(j)
              }
            }
          }
        }
      }

      for (k <- 0 until CLUSTERS; d <- 0 until DIM) {
        centralNewClusters(k)(d) /= centralClusterCounts(k)
      }

      def testConvergence(): Boolean = {
        import scala.math.abs

        for (i <- 0 until CLUSTERS; j <- 0 until DIM) {
          if (abs(centralCurrentClusters(i)(j) - centralNewClusters(i)(j)) > 0.0001) {
            return false
          }
        }
        return true
      }

      iter += 1
      converged = testConvergence()

      for (i <- 0 until CLUSTERS; j <- 0 until DIM) {
        centralCurrentClusters(i)(j) = centralNewClusters(i)(j)
      }

      for (i <- 0 until CLUSTERS; j <- 0 until DIM) {
        centralNewClusters(i)(j) = 0.0f
      }

      for (i <- 0 until CLUSTERS) {
        centralClusterCounts(i) = 0
      }
    }

    for (d <- 0 until DIM) {
      for (k <- 0 until CLUSTERS) {
        if (k > 0) {
          print(" ")
        }
        print(centralCurrentClusters(k)(d))
      }
      println()
    }
  }
}