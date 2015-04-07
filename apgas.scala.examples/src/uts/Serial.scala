package apgas.scala.examples
package uts

import java.security.MessageDigest

object Serial { 
  def main(args : Array[String]) : Unit = {
    val seed = 19
    val depth = try {
      args(0).toInt
    } catch {
      case _ : Throwable => 13
    }
    
    println("Warmup...")
    treeSizeSerialFast(seed, depth - 2)
    println("Starting...")
    
    val t0 = System.nanoTime()
    val s = treeSizeSerialFast(seed, depth)
    val t1 = System.nanoTime()
    
    printResult("serial", depth, s, t0, t1)
    checkResult(depth, s)
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
      }
      //assert(size == s)
    }
  }
  
  def treeSizeSerial(seed : Int, maxDepth : Int) : Int = {
    val i = Node.initial(seed, maxDepth)
    count(WorkList.init(i))
  }
  
  def treeSizeSerialFast(seed : Int, maxDepth : Int) : Long = {
    val b = new Bag()
    
    val md = java.security.MessageDigest.getInstance("SHA-1")
    
    b.initialize(md, seed, maxDepth)
    
    while(b.size != 0) {
      b.expand(md)
    }
    
    b.counted
  }
  
  def count(workList : WorkList) : Int = {
    val (c, l) = countMax(workList, Int.MaxValue)
    assert(l.isEmpty)
    c
  }
  
  def countMax(workList : WorkList, max : Int) : (Int, WorkList) = {
    val hasher = new NodeHasher
    
    var c : Int = 0
    var wl = workList
    
    while(!wl.isEmpty && c < max) {
      val (node, lower, upper) = wl.head
      wl = wl.tail
      
      if(node.depth > 0 && lower < upper) {
        val child = node.nthChild(lower, hasher)
        wl = wl.push(node, lower + 1, upper).push(child, 0, child.childrenCount)
      } else if(upper == node.childrenCount) {
        // The guard is necessary do not double-count nodes that have been split.
        c += 1
      }
    }
    
    (c, wl)
  }
}