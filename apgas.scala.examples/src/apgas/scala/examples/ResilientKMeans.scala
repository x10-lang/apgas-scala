package apgas.scala.examples

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
      case _: Throwable => 20000
    }
    val iterations: Int = try {
      args(1).toInt
    } catch {
      case _: Throwable => 20
    }

    System.setProperty(Configuration.APGAS_RESILIENT, "true")
    
    System.setProperty(Configuration.APGAS_SERIALIZATION_EXCEPTION, "true")
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

    def pointsForPlace(i: Int) : ListBuffer[Array[Float]] = {
      val rand = new Random(i)
      ListBuffer.fill[Array[Float]](numPoints / NUM_PLACES) {
        Array.fill[Float](DIM) { rand.nextFloat() }
      }
    }

    val globalPoints = PlaceLocalRef.forPlaces(places) {
      pointsForPlace(here.id)
    }

    val centralCurrentClusters = Array.ofDim[Float](CLUSTERS, DIM)
    val centralNewClusters = Array.ofDim[Float](CLUSTERS, DIM)
    val centralClusterCounts = Array.ofDim[Int](CLUSTERS)

    // Arbitrarily initialize central clusters to first few points
    for (i <- 0 until CLUSTERS; j <- 0 until DIM) {
      centralCurrentClusters(i)(j) = globalPoints()(i)(j)
    }

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

                // kill a place
                if (iter == 9) {
                  if (here.id == 2) System.exit(1)
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
              }

              synchronized {
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
                globalPoints().clear()
              }
            }
            // regenerate or redistribute points
            for (place <- oldPlaces) {
              if (survivorList.contains(place)) {
                //println(place + " survived. Regenerating points.")
                asyncAt(place) {
                  val pointsHere = globalPoints()
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
                      val pointsHere = globalPoints()
                      synchronized { pointsHere.++=(pointsToSend) }
                    }
                  }
                }
              }
            }
          }
        }
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