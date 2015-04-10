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
    
    println(v)
    
  }
}