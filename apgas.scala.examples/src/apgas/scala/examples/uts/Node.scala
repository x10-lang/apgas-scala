package apgas.scala.examples
package uts

class Node private(
    private val bytes0 : Long,
    private val bytes1 : Long,
    private val bytes2 : Int,
    val depth : Int, val childrenCount : Int) {
  
  def copy : Node = {
    new Node(bytes0, bytes1, bytes2, depth, childrenCount)
  }
  
  def nthChild(n : Int, hasher : NodeHasher) : Node = {
    val (b0, b1, b2) = hasher.childHash(bytes0, bytes1, bytes2, n)
    new Node(b0, b1, b2, depth - 1, Node.childCountForHash(b0,b1,b2))
  }
  
  override def toString() : String = {
    val sb = new StringBuilder();
    sb.append("Node(")
    
    /*
    for(i <- 0 until 20) {
      val b = hash(i)
      sb.append(String.format("%x", new java.lang.Byte(b)))
    }
    *
    */
    sb.append(String.format("%x%x%x", new java.lang.Long(bytes0), new java.lang.Long(bytes1), new java.lang.Integer(bytes2)))
    sb.append(s", $depth, $childrenCount)")
    
    sb.toString()
  }
}

object Node {
  // Problem constants:
  private val DEN : Double = Math.log(4.0 / (1.0 + 4.0)) // branching factor: 4.0
  
  def initial(seed : Int, maxDepth : Int) : Node = { 
    val (b0,b1,b2) = NodeHasher.initial(seed)
    new Node(b0,b1,b2, maxDepth, childCountForHash(b0,b1,b2))
  }
  
  /*
  private def childCountForHash(hash : Array[Byte]) : Int = {
    val v : Int = (((0x7f & hash(16)) << 24)
        | ((0xFF & hash(17)) << 16)
        | ((0xFF & hash(18)) << 8) | (0xFF & hash(19)))
    val childCount : Int = (Math.log(1.0 - v / 2147483648.0) / DEN).toInt
    childCount
  }
  */
  
  private def childCountForHash(b0 : Long, b1 : Long, b2 : Int) : Int = {
    val v : Int = b2 & 0x7FFFFFFF
    val childCount : Int = (Math.log(1.0 - v / 2147483648.0) / DEN).toInt
    childCount
  }
}
