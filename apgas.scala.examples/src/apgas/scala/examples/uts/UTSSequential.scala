package apgas.scala.examples
package uts

import java.security.MessageDigest

object UTSSequential { 
  def main(args : Array[String]) : Unit = {
    val seed = 19
    val depth = try {
      args(0).toInt
    } catch {
      case _ : Throwable => 13
    }
    
    println("Warmup...")
    treeSize(seed, depth - 2)
    println("Starting...")
    
    val t0 = System.nanoTime()
    val s = treeSize(seed, depth)
    val t1 = System.nanoTime()
    
    Common.printResult("serial", depth, s, t0, t1)
    Common.checkResult(depth, s)
  }
  
  def treeSize(seed : Int, maxDepth : Int) : Long = {
    val b = new Bag()
    val md = java.security.MessageDigest.getInstance("SHA-1")
    b.initialize(md, seed, maxDepth)
    while(b.size != 0) {
      b.expand(md)
    }
    b.counted
  }
}