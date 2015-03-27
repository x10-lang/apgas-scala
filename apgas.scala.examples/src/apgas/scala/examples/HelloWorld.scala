package apgas.scala.examples

import apgas.Configuration
import apgas.scala._

object HelloWorld {
  def main(args : Array[String]) : Unit = {
    if (System.getProperty(Configuration.APGAS_PLACES) == null) {
      System.setProperty(Configuration.APGAS_PLACES, "4")
    }

    // PS: I need this because java is not the default on my system..
    System.setProperty(Configuration.APGAS_JAVA, "java8")
    
    // PS: An alternate way would be to invoke 'scala' instead of Java.
    System.setProperty("java.class.path", System.getProperty("java.class.path", "") + ":" +
        "/home/psuter/software/scala/current/lib/scala-library.jar")
      
    apgas {
      finish {
        for(place <- places) {
          asyncAt(place) {
            println("Hello from there " + here)
          }
        }
      }
    }
  
    println(s"Running main at ${here} of ${places.size} places")
  }   
}