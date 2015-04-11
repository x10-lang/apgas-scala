package apgas
package scala.examples
package kmeans

object Common {
  val DIM           : Int = 4
  val NUM_CENTROIDS : Int = 5
  val NUM_PLACES    : Int = 2
  val NUM_POINTS    : Int = 2000000
  
  def setup(numPlaces : Int = 1) : Unit = {
    // PS: I need this because java is not the default on my system..
    System.setProperty(Configuration.APGAS_JAVA, "java8")

    if (System.getProperty(Configuration.APGAS_PLACES) == null) {
      System.setProperty(Configuration.APGAS_PLACES, s"$numPlaces")
    }
  }
  
  def pointsForWorker(workerID : Int, numPlaces : Int) : Seq[Array[Float]] = {
    val rand = new java.util.Random(workerID)
    
    Array.fill[Array[Float]](NUM_POINTS / numPlaces) {
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
  
  def withinEpsilon(centroids1: Array[Array[Float]], centroids2: Array[Array[Float]]) : Boolean = {
    import _root_.scala.math.abs
    
    for (i <- 0 until NUM_CENTROIDS; j <- 0 until DIM) {
      if (abs(centroids1(i)(j) - centroids2(i)(j)) > 0.0001) {
        return false
      }
    }
    return true
  }
  
  def printCentroids(centroids : Array[Array[Float]]) : Unit = {
    println()
    for (d <- 0 until DIM) {
      for (k <- 0 until NUM_CENTROIDS) {
        if (k > 0) {
          print(" ")
        }
        print(centroids(k)(d))
      }
      println()
    }
  }
  
  def copy2DArray(a1 : Array[Array[Float]], a2 : Array[Array[Float]]) : Unit = {
    for(i <- 0 until a1.length; j <- 0 until a1(i).length) {
      a2(i)(j) = a1(i)(j)
    }
  }
  
  trait Zero[T] { val zero : T }
  implicit object IntZero extends Zero[Int] { val zero = 0 }
  implicit object FloatZero extends Zero[Float] { val zero = 0.0f }
  
  def resetArray[T : Zero](a : Array[T]) : Unit = {
    val zero = implicitly[Zero[T]].zero
    for(i <- 0 until a.length) {
      a(i) = zero
    }
  }
  
  def reset2DArray[T : Zero](a : Array[Array[T]]) : Unit = {
    val zero = implicitly[Zero[T]].zero
    for(i <- 0 until a.length; j <- 0 until a(i).length) {
      a(i)(j) = zero
    }
  }
}