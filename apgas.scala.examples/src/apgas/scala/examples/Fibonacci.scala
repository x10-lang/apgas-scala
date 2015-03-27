package apgas.scala.examples

import apgas.scala._
    
object Fibonacci {
  def main(args : Array[String]) : Unit = {
    val n : Int = if (args.length > 0) {
      try {
        args(0).toInt
      } catch {
        case e : NumberFormatException =>
          Console.err.println("usage: java Fibonacci [int]")
          sys.exit(1)
      }
    } else {
      42
    }
  
    Console.println("Initializing the global runtime...")
    apgas {
      println(s"Beginning to compute fib($n) sequentially...")
      for (i <- 0 until 5) {
        var time : Long = System.nanoTime()
        val f : Int = seqfib(n)
        time = System.nanoTime() - time
        println(s"fib($n)=$f in ${time / 1e9}s")
      }

      println(s"Beginning to compute fib($n) in parallel...")
      for (i <- 0 until 5) {
        var time : Long = System.nanoTime()
        val f : Int = parfib(n)
        time = System.nanoTime() - time
        println(s"fib($n)=$f in ${time / 1e9}s")
      }
    } 
  }
  
    def seqfib(n : Int) : Int = {
    if (n < 2) {
      n
    } else {
      seqfib(n - 1) + seqfib(n - 2)
    }
  }

  def parfib(n : Int) : Int = {
    if (n < 22) {
      seqfib(n)
    } else {
      val result : Array[Int] = new Array[Int](2)

      finish {
        async {
          result(0) = parfib(n - 2)
        }
        result(1) = parfib(n - 1)
      }

      result(0) + result(1)
    }
  }
}