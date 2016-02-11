/*
 *  This file is part of the X10 project (http://x10-lang.org).
 *
 *  This file is licensed to You under the Eclipse Public License (EPL);
 *  You may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *      http://www.opensource.org/licenses/eclipse-1.0.php
 *
 *  (C) Copyright IBM Corporation 2015-2016.
 */

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