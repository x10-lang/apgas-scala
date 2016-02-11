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

package apgas.scala.examples.uts

object Common {
  def setup(numPlaces : Int = 1) : Unit = {
    import apgas.Configuration
    // PS: I put my machine-specific config here...
    // I need this because java is not the default on my system..
    System.setProperty(Configuration.APGAS_JAVA, "java8")
    if (System.getProperty(Configuration.APGAS_PLACES) == null) {
      System.setProperty(Configuration.APGAS_PLACES, "" + numPlaces)
    }
  }
  
  def printResult(method : String, depth : Int, size : Long, t0 : Long, t1 : Long) : Unit = {
    val time = t1 - t0
    println(s"[$method] depth: $depth, performance: $size/${time / 1e9} = ${size / (time / 1e3)}M nodes/s")
  }
  
  def checkResult(depth : Int, size : Long) : Unit = {
    val knownSizes = Map[Int,Long](
       1 -> 6L,
       2 -> 65L,
       3 -> 254L,
       4 -> 944L,
       5 -> 3987L,
       6 -> 16000L,
       7 -> 63914L,
       8 -> 257042L,
       9 -> 1031269L,
      10 -> 4130071L,
      11 -> 16526523L,
      12 -> 66106929L,
      13 -> 264459392L
    )
     
    for(s <- knownSizes.get(depth)) {
      if(size != s) {
        Console.err.println(s"Expected $s, got $size. Difference: ${size - s}")
      } else {
        println("Result appears correct.")
      }
    }
  }
}