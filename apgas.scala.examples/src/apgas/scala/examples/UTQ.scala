package apgas.scala.examples;

import apgas.scala._

import java.security.DigestException
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import java.util.Random
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean

object UTQ {
  class Bag(val hash : Array[Byte], val depth : Array[Int], val lower : Array[Int], val upper : Array[Int], var size : Int) {
    def this(n : Int) = this(
        hash = new Array[Byte](n * 20 + 4),
        depth = new Array[Int](n),
        lower = new Array[Int](n),
        upper = new Array[Int](n),
        size = 0)

    def split() : Option[Bag] = {
      var s : Int = 0
      for (i <- 0 until size) {
        if ((upper(i) - lower(i)) >= 2) {
          s += 1
        }
      }
      if (s == 0) {
        None
      } else {
        val b = new Bag(s)
        for (i <- 0 until size) {
          val p = upper(i) - lower(i)
          
          if (p >= 2) {
            System.arraycopy(hash, i * 20, b.hash, b.size * 20, 20)
            b.depth(b.size) = depth(i)
            b.upper(b.size) = upper(i)
            upper(i) -= p / 2
            b.lower(b.size) = upper(i)
            b.size += 1
          }
        }
        Some(b)
      }
    }

    def merge(b : Bag) : Unit = {
      System.arraycopy(b.hash, 0, hash, size * 20, b.size * 20)
      System.arraycopy(b.depth, 0, depth, size, b.size)
      System.arraycopy(b.lower, 0, lower, size, b.size)
      System.arraycopy(b.upper, 0, upper, size, b.size)
      size += b.size
    }
  }
  
  object Worker {
    val power : Int = 1
    
    val uts : Array[Worker] = Array.tabulate[Worker](1 << power) { id =>
      new Worker(id + (here.id << power))
    }
     
    def encoder : MessageDigest = MessageDigest.getInstance("SHA-1")
  }

  class Worker(val location : Int) {
    val locations : Int = places.size << Worker.power;

    val lifeline = new AtomicBoolean(location != locations - 1)
    
    val random = new Random()
    val md = Worker.encoder
    
    val den : Double = Math.log(4.0 / (1.0 + 4.0)); // branching factor: 4.0
    
    val bag = new Bag(64)
    
    var count : Long = 0L

    val thieves = new ConcurrentLinkedQueue[java.lang.Integer]()
    
    var state : Int = -2; // -2: inactive, -1: running, p: stealing from p

    def digest() : Int = {
      val offset = bag.size * 20
      
      md.digest(bag.hash, offset, 20)
      
      count += 1
      
      val v : Int = (((0x7f & bag.hash(offset + 16)) << 24)
          | ((0xff & bag.hash(offset + 17)) << 16)
          | ((0xff & bag.hash(offset + 18)) << 8)
          | (0xff & bag.hash(offset + 19)))
          
      return (Math.log(1.0 - v / 2147483648.0) / den).toInt
    }

    def init(seed : Int, depth : Int) : Unit = {
      for (i <- 0 until 16) {
        bag.hash(i) = 0;
      }
      bag.lower(0) = 0;
      bag.hash(16) = (seed >> 24).toByte
      bag.hash(17) = (seed >> 16).toByte
      bag.hash(18) = (seed >> 8).toByte
      bag.hash(19) = seed.toByte
      
      md.update(bag.hash, 0, 20)
      
      val v = digest()
      
      if (v > 0) {
        bag.depth(0) = depth
        bag.upper(0) = v
        bag.size = 1
      }
    }

    def expand() : Unit = {
      val top : Int = bag.size - 1
      val d : Int = bag.depth(top)
      val l : Int = bag.lower(top)
      val u : Int = bag.upper(top) - 1
      
      if (d > 1) {
        if (u == l) {
          bag.size -= 1
        } else {
          bag.upper(top) = u
        }
        
        val offset : Int = top * 20
        bag.hash(offset + 20) = (u >> 24).toByte
        bag.hash(offset + 21) = (u >> 16).toByte
        bag.hash(offset + 22) = (u >> 8).toByte
        bag.hash(offset + 23) = u.toByte
        
        md.update(bag.hash, offset, 24)
        
        val v : Int = digest()
        
        if (v > 0) {
          bag.depth(bag.size) = d - 1
          bag.lower(bag.size) = 0
          bag.upper(bag.size) = v
          bag.size += 1
        }
      } else {
        bag.size -= 1
        count += 1 + u - l
      }
    }

    def run() : Unit = {
      // System.err.println(location + " starting");
      this.synchronized {
        state = -1
      }
      
      while (bag.size > 0) {
        while (bag.size > 0) {
          var n = 500
          while((n > 0) && (bag.size > 0)) {
            expand()
            n -= 1
          }
          distribute()
        }
        steal()
      }
      
      this.synchronized {
        state = -2
      }
      
      // System.err.println(location + " stopping");
      distribute()
      lifelinesteal()
      // System.err.println(location + " stopped");
    }

    def lifelinesteal() : Unit = {
      if (locations == 1) {
        return
      }
      
      val victim : Int = (location + locations - 1) % locations
      val id = victim & ((1 << Worker.power) - 1)
      asyncAt(place(victim >> Worker.power)) {
        Worker.uts(id).lifeline.set(true)
      }
    }

    def steal() : Unit = {
      if (locations == 1) {
        return
      }
      
      val thief : Int = location
      val victim : Int = {
        val v = random.nextInt(locations - 1)
        if(v >= thief) v + 1 else v
      }
      
      this.synchronized {
        state = victim
      }
      
      val id : Int = victim & ((1 << Worker.power) - 1)
      uncountedAsyncAt(place(victim >> Worker.power)) {
        Worker.uts(id).request(thief)
      }
      
      this.synchronized {
        while (state >= 0) {
          try {
            wait()
          } catch {
            case _ : InterruptedException => ; 
          }
        }
      }
    }

    def request(thief : Int) : Unit = {
      this.synchronized {
        if (state == -1) {
          thieves.add(thief)
          return
        }
      }
      
      val victim : Int = location
      val id : Int = thief & ((1 << Worker.power) - 1)
      
      uncountedAsyncAt(place(thief >> Worker.power)) {
        Worker.uts(id).deal(victim, null)
      }
    }

    def lifelinedeal(b : Bag) : Unit = {
      bag.merge(b)
      run()
    }

    def deal(victim : Int, b : Option[Bag]) : Unit = synchronized {
      if (state != victim) {
        return
      }
      
      b.foreach(b0 => bag.merge(b0))
      
      state = -1;
      notifyAll()
    }

    def distribute() : Unit = {
      var thief : java.lang.Integer = null
      
      if (lifeline.get()) {
        for(b <- bag.split()) {
          thief = (location + 1) % locations
          lifeline.set(false)
          val id : Int = thief & ((1 << Worker.power) - 1)
          
          asyncAt(place(thief >> Worker.power)) {
            Worker.uts(id).lifelinedeal(b)
          }
        }
      }
      
      while ({ thief = thieves.poll(); thief != null}) {
        val b = bag.split();
        
        val victim : Int = location
        val id : Int = thief & ((1 << Worker.power) - 1)
        
        uncountedAsyncAt(place(thief >> Worker.power)) {
          Worker.uts(id).deal(victim, b)
        }
      }
    }

    def reset() : Unit = {
      count = 0
      lifeline.set(location != locations - 1)
    }
  }

  def sub(str : String, start : Int, end : Int) : String = {
    return str.substring(start, Math.min(end, str.length()))
  }

  def main(args : Array[String]) : Unit = apgas {
    val d : Int = try {
      args(0).toInt
    } catch {
      case _ : Throwable => 13
    }

    val depth = d

    println("Warmup...")
    
    finish {
      Worker.uts(0).init(19, depth - 2)
      Worker.uts(0).run()
    }

    finish {
      for (p <- places) {
        asyncAt(p) {
          for (id <- 0 until (1 << Worker.power)) {
            Worker.uts(id).reset()
          }
        }
      }
    }

    println("Starting...");
    var time = System.nanoTime()

    finish {
      Worker.uts(0).init(19, depth)
      Worker.uts(0).run()
    }

    time = System.nanoTime() - time;
    System.out.println("Finished.");

    var count = 0L
    
    // collect all counts
    for (p <- places) {
      count += at(p) {
        var v : Long = 0L
        
        for (id <- 0 until (1 << Worker.power)) {
          v += Worker.uts(id).count;
        }
        v
      }
    }
    
    assert(depth != 13 || count == 264459392)

    println("Depth: " + depth + ", Places: " + places.size
        + ", Power: " + Worker.power + ", Performance: " + count + "/"
        + sub("" + time / 1e9, 0, 6) + " = "
        + sub("" + (count / (time / 1e3)), 0, 6) + "M nodes/s");
  }
}