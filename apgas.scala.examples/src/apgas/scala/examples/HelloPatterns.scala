package apgas.scala.examples

import apgas.Configuration
import apgas.scala._

object HelloPatterns {
  
  def evalThere(x : Int, y : Int) : Int = {
    println(s"Evaluating $x+$y at $here")
    x + y
  }
  
  def main(args : Array[String]) : Unit = {
    // PS: I need this because java is not the default on my system..
    System.setProperty(Configuration.APGAS_JAVA, "java8")

    if (System.getProperty(Configuration.APGAS_PLACES) == null) {
      System.setProperty(Configuration.APGAS_PLACES, "4")
    }
    
    val arg1 = 38
    val arg2 = 42
    val p = (places.toList)(1)
    
    val v = at(p) { evalThere(arg1, arg2) }
    
    import _root_.apgas.scala.util._
    
    val r = GlobalRef.forPlaces(places) { 42 + here.id }
    
    val total = places.foldLeft(0) { (i,p) =>
      i + at(p) { r() }
    }
    
    println(total)
    
    println(v)
    
    def fib(i : Int) : Long = if(i <= 1) i else {
      var a, b : Long = 0L
      
      finish {
        async {
          a = fib(i - 2)
        }
        b = fib(i - 1)
      }
      a + b
    }
    
    println(fib(10))
    
    
  }
}