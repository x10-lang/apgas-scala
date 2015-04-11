package apgas.scala.examples
package kmeans

import apgas.Configuration
import apgas.MultipleException

import apgas.scala.util._
import apgas.scala._

import java.util.Random

import scala.collection.mutable.ListBuffer

/**
 * A resilient distributed KMeans using coarse-grained asyncs to implement
 * an allreduce pattern for cluster centers and counts.
 * If a place dies, its points are redistributed to the surviving places.
 * TODO don't regenerate all points on place failure
 *
 * For a highly optimized and scalable, version of this benchmark see KMeans.x10
 * in the X10 Benchmarks (separate download from x10-lang.org)
 */
object ResilientKMeans {
  import Common.{ DIM, NUM_CENTROIDS, NUM_PLACES }

  class ClusterState extends Serializable {
    val clusters = Array.ofDim[Float](NUM_CENTROIDS, DIM)
    val clusterCounts = Array.ofDim[Int](NUM_CENTROIDS)
  }

  def main(args: Array[String]): Unit = {
    Common.setup(numPlaces = NUM_PLACES)

    val numPoints: Int = try {
      args(0).toInt
    } catch {
      case _: Throwable => Common.NUM_POINTS
    }
    val iterations: Int = try {
      args(1).toInt
    } catch {
      case _: Throwable => 20
    }

    printf("Resilient K-Means: %d clusters, %d points, %d dimensions, %d places\n",
      NUM_CENTROIDS, numPoints, DIM, NUM_PLACES)

    val clusterState = PlaceLocalRef.forPlaces(places) {
      new ClusterState()
    }

    def pointsForPlace(i: Int): ListBuffer[Array[Float]] = {
      val rand = new Random(i)
      ListBuffer.fill[Array[Float]](numPoints / NUM_PLACES) {
        Array.fill[Float](DIM) { rand.nextFloat() }
      }
    }

    val points = PlaceLocalRef.forPlaces(places) {
      pointsForPlace(here.id)
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

    var currentPlaces = places
    while (iter <= iterations && !converged) {
      println("Iteration: " + iter)
      try {
        finish {
          for (place <- currentPlaces) {
            async {
              val placeClusters = at(place) {
                val state = clusterState()
                val newClusters = state.clusters
                for (i <- 0 until NUM_CENTROIDS) {
                  for (j <- 0 until DIM) {
                    newClusters(i)(j) = 0.0f
                  }
                }
                val clusterCounts = state.clusterCounts
                for (i <- 0 until NUM_CENTROIDS) {
                  clusterCounts(i) = 0
                }

                /* compute new clusters and counters */
                val ps = points()

                for (p <- 0 until ps.length) {
                  val closest = Common.closestCentroid(ps(p), centralCurrentClusters)

                  for (d <- 0 until DIM) {
                    newClusters(closest)(d) += ps(p)(d)
                  }
                  clusterCounts(closest) = clusterCounts(closest) + 1
                }

                // kill a place
                if (iter == 9) {
                  if (here.id == 2) System.exit(1)
                }

                state
              }

              // Combine place clusters to central
              synchronized {
                for (k <- 0 until NUM_CENTROIDS; d <- 0 until DIM) {
                  centralNewClusters(k)(d) += placeClusters.clusters(k)(d)
                }
              }

              synchronized {
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

      } catch {
        case e: MultipleException => {
          if (!e.isDeadPlaceException()) {
            e.printStackTrace()
          } else {
            val oldPlaces = currentPlaces
            currentPlaces = places
            val survivorList = currentPlaces.toList
            // clear existing points
            finish {
              for (survivorPlace <- survivorList) asyncAt(survivorPlace) {
                points().clear()
              }
            }
            // regenerate or redistribute points
            for (place <- oldPlaces) {
              if (survivorList.contains(place)) {
                //println(place + " survived. Regenerating points.")
                asyncAt(place) {
                  val pointsHere = points()
                  synchronized { pointsHere.++=(pointsForPlace(here.id)) }
                }
              } else {
                println(place + " has died. Redistributing points.")
                var pointsToRedistribute = pointsForPlace(place.id)
                val chunkSize = pointsToRedistribute.size / survivorList.size
                val leftOver = pointsToRedistribute.size % survivorList.size
                var i = 0

                finish {
                  for (survivorPlace <- survivorList) {
                    val numToRedistribute = {
                      if (i < leftOver) chunkSize + 1
                      else chunkSize
                    }
                    i = i + 1
                    val pointsToSend = pointsToRedistribute.take(numToRedistribute)
                    pointsToRedistribute = pointsToRedistribute.drop(numToRedistribute)
                    asyncAt(survivorPlace) {
                      val pointsHere = points()
                      synchronized { pointsHere.++=(pointsToSend) }
                    }
                  }
                }
              }
            }
          }
        }
      }

      for (i <- 0 until NUM_CENTROIDS; j <- 0 until DIM) {
        centralNewClusters(i)(j) = 0.0f
      }

      for (i <- 0 until NUM_CENTROIDS) {
        centralClusterCounts(i) = 0
      }
    }
    time = System.nanoTime() - time

    for (d <- 0 until DIM) {
      for (k <- 0 until NUM_CENTROIDS) {
        if (k > 0) {
          print(" ")
        }
        print(centralCurrentClusters(k)(d))
      }
      println()
    }
    printf("Time per iteration %.3f ms\n", time / 1e6 / iter)
  }
}