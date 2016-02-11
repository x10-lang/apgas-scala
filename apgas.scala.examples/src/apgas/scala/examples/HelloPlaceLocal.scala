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
import apgas.scala.util._
import apgas.scala._

import scala.util.Random

object HelloPlaceLocal {
  def main(args : Array[String]) : Unit = {
    case class Box(i : Int) extends PlaceLocal
    
    // PS: I need this because java is not the default on my system..
    System.setProperty(Configuration.APGAS_JAVA, "java8")

    if (System.getProperty(Configuration.APGAS_PLACES) == null) {
      System.setProperty(Configuration.APGAS_PLACES, "4")
    }
    
    val box = PlaceLocal.forPlaces(places) {
      val r = Random.nextInt()
      println(s"At place $here, putting $r in the box.")
      new Box(r)
    }
    
    for(p <- places) {
      asyncAt(p) {
        println(s"At place $here, reading ${box.i} from the box.")
      }
    }
  }
}