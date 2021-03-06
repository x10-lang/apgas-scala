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
import apgas.scala._

object HelloWorld {
  def main(args : Array[String]) : Unit = {
    if (System.getProperty(Configuration.APGAS_PLACES) == null) {
      System.setProperty(Configuration.APGAS_PLACES, "4")
    }

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
