package apgas.scala.examples

import apgas.Configuration
import apgas.MultipleException
import apgas.scala._

/** 
 *  This code prints a greeting from each place, repeating forever.
 *  Kill one of the places (except for place 0) and watch it recover!
 */
object ResilientHelloWorld {
  def main(args: Array[String]): Unit = {
    System.setProperty(Configuration.APGAS_RESILIENT, "true")
    System.setProperty(Configuration.APGAS_SERIALIZATION_EXCEPTION, "true")
    if (System.getProperty(Configuration.APGAS_PLACES) == null) {
      System.setProperty(Configuration.APGAS_PLACES, "4")
    }

    // PS: I need this because java is not the default on my system..
    System.setProperty(Configuration.APGAS_JAVA, "java8")

    // PS: An alternate way would be to invoke 'scala' instead of Java.
    //System.setProperty("java.class.path", System.getProperty("java.class.path", "") + ":" +
    //    "/home/psuter/software/scala/current/lib/scala-library.jar")

    apgas {
      var i: Int = 0
      while (true) {
        try {
          finish {
            val world = places
            println(i + ": There are " + world.size + " places")
            for (place <- places) {
              asyncAt(place) {
                println("Hello from there " + here)
              }
            }
          }
        } catch {
          case e: MultipleException => {
            if (!e.isDeadPlaceException()) {
              e.printStackTrace()
            } else {
              Console.err.println("Ignoring DeadPlaceException")
            }
            try {
              Thread.sleep(2000)
            } catch {
              case e: InterruptedException => ;
            }
          }
        }
      }
    }

    println(s"Running main at ${here} of ${places.size} places")
  }
}