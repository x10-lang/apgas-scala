package apgas
package scala.examples
package kmeans

object Common {
  val DIM           : Int = 2
  val NUM_CENTROIDS : Int = 4
  val NUM_PLACES    : Int = 2
  val NUM_POINTS    : Int = 2000
  
  def setup(numPlaces : Int = 1) : Unit = {
    // PS: I need this because java is not the default on my system..
    System.setProperty(Configuration.APGAS_JAVA, "java8")

    if (System.getProperty(Configuration.APGAS_PLACES) == null) {
      System.setProperty(Configuration.APGAS_PLACES, s"$NUM_PLACES")
    }
  }
  
  def pointsForWorker(workerID : Int) : Seq[Array[Float]] = {
    val rand = new java.util.Random(workerID)
    
    Array.fill[Array[Float]](NUM_POINTS / NUM_PLACES) {
      Array.fill[Float](DIM) { rand.nextFloat() }
    }
  }
  
  def closestCentroid(point : Array[Float], centroids : Array[Array[Float]]) : Int = {
    var closest = -1
    var closestDist = Float.MaxValue
    
    for (k <- 0 until NUM_CENTROIDS) {
      var dist = 0.0f
      for (d <- 0 until DIM) {
        val tmp = point(d) - centroids(k)(d)
        dist += tmp * tmp
      }
      if (dist < closestDist) {
        closestDist = dist
        closest = k
      }
    }
    
    closest
  }
}