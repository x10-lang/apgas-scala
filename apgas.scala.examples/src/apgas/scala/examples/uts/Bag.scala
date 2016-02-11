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
package uts

import scala.math.log

import java.security.MessageDigest

object Bag {
  def encoder() = MessageDigest.getInstance("SHA-1")
  private val den : Double = Math.log(4.0 / (1.0 + 4.0))
}

final class Bag(n : Int = 10) extends Serializable {
  private var hash : Array[Byte] = new Array[Byte](n * 20 + 4)
  private var depth : Array[Int] = new Array[Int](n)
  private var lower : Array[Int] = new Array[Int](n)
  private var upper : Array[Int] = new Array[Int](n)

  // Number of nodes in the bag
  private var sz : Int = 0
  
  // Number of nodes processed so far
  private var count : Long = 0L
  
  def size : Int = sz
  
  def isEmpty : Boolean = (sz == 0)
  
  def counted : Long = count
  
  private def digest(md : MessageDigest, d : Int) : Unit = {
    if(sz >= depth.length) {
      grow()
    }
    count += 1L
    val offset = sz * 20
    
    md.digest(hash, offset, 20)
    
    val v = (((0x7f & hash(offset + 16)) << 24)
        | ((0xff & hash(offset + 17)) << 16)
        | ((0xff & hash(offset + 18)) << 8)
        | (0xff & hash(offset + 19)))
        
    val n = (log(1.0 - v / 2147483648.0) / Bag.den).toInt
    
    if(n > 0) {
      if(d > 1) {
        depth(sz) = d - 1
        lower(sz) = 0
        upper(sz) = n
        sz += 1
      } else {
        count += n
      }
    }
  }
  
  def initialize(md : MessageDigest, s : Int, d : Int) : Unit = {
    hash(16) = (s >> 24).toByte
    hash(17) = (s >> 16).toByte
    hash(18) = (s >> 8).toByte
    hash(19) = s.toByte
    md.update(hash, 0, 20)
    digest(md, d)
  }

  def expand(md : MessageDigest) : Unit = {
    val top = sz - 1
    val d = depth(top)
    val l = lower(top)
    val u = upper(top) - 1
    
    if (u == l) {
      sz = top
    } else {
      upper(top) = u
    }
    
    val offset = top * 20
    hash(offset + 20) = (u >> 24).toByte
    hash(offset + 21) = (u >> 16).toByte
    hash(offset + 22) = (u >> 8).toByte
    hash(offset + 23) = u.toByte
    md.update(hash, offset, 24)
    digest(md, d)
  }

  def run(md : MessageDigest) : Unit = {
    while (sz > 0) {
      expand(md)
    }
  }

  def trimmed : Bag = {
    if (sz == 0) {
      val b = new Bag(0)
      b.count = count
      b
    } else {
      val b = new Bag(sz)
      System.arraycopy(hash, 0, b.hash, 0, sz * 20)
      System.arraycopy(depth, 0, b.depth, 0, sz)
      System.arraycopy(lower, 0, b.lower, 0, sz)
      System.arraycopy(upper, 0, b.upper, 0, sz)
      b.sz = sz
      b.count = count
      b
    }
  }

  def split() : Option[Bag] = {
    var s = 0
    
    var i = 0
    while(i < sz) {
      if ((upper(i) - lower(i)) >= 2) {
        s += 1
      }
      i += 1
    }
    if (s == 0) {
      None
    } else {
      val b = new Bag(s)
      i = 0
      while(i < sz) {
        val p = upper(i) - lower(i)
        if (p >= 2) {
          System.arraycopy(hash, i * 20, b.hash, b.sz * 20, 20)
          b.depth(b.sz) = depth(i)
          b.upper(b.sz) = upper(i)
          upper(i) -= p / 2
          b.lower(b.sz) = upper(i) 
          b.sz += 1
        }
        i += 1
      }
      Some(b)
    }
  }

  def merge(b : Bag) : Unit = {
    val s = sz + b.sz
    
    while (s > depth.length) {
      grow()
    }
    
    System.arraycopy(b.hash, 0, hash, sz * 20, b.sz * 20)
    System.arraycopy(b.depth, 0, depth, sz, b.sz)
    System.arraycopy(b.lower, 0, lower, sz, b.sz)
    System.arraycopy(b.upper, 0, upper, sz, b.sz)
    sz = s
  }

  private def grow() : Unit = {
    val n = depth.length * 2
    
    val h = new Array[Byte](n * 20 + 4)
    val d = new Array[Int](n)
    val l = new Array[Int](n)
    val u = new Array[Int](n)
    
    System.arraycopy(hash, 0, h, 0, sz * 20)
    System.arraycopy(depth, 0, d, 0, sz)
    System.arraycopy(lower, 0, l, 0, sz)
    System.arraycopy(upper, 0, u, 0, sz)
    hash = h
    depth = d
    lower = l
    upper = u
  }
  
  def clear() : Unit = {
    sz = 0
    count = 0L
  }
}
