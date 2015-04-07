package apgas.scala.examples
package uts

class WorkList(val list: List[(Node,Int,Int)]) extends AnyVal {
  def split : (WorkList, WorkList) = {
    var l1 : List[(Node,Int,Int)] = Nil
    var l2 : List[(Node,Int,Int)] = Nil
    
    var ceil : Boolean = false
    
    for((n,l,u) <- list) {
      if(l == u) {
        if(ceil) {
          l1 = (n,l,u) :: l1
        } else {
          l2 = (n,l,u) :: l2
        }
        ceil = !ceil
      } else {
        val r = 1 + u - l
        var h = r / 2
        if(2 * h < r) {
          if(ceil) {
            h += 1
          }
          ceil = !ceil
        }
        
        if(l + h > l)
          l1 = (n, l, l + h) :: l1
        
        if(u > l + h)
          l2 = (n.copy, l + h, u) :: l2
      }
    }    
    (new WorkList(l1.reverse), new WorkList(l2.reverse))
  }
  
  def isEmpty : Boolean = list.isEmpty
  
  def head = list.head
  
  def tail = new WorkList(list.tail)
  
  def push(node : Node, lower : Int, upper : Int) : WorkList = new WorkList(((node, lower, upper) :: list))
  
  override def toString : String = {
    list.map {
      case (node, lower, upper) =>
        s"[${node.toString.substring(5, 20)}... $lower-$upper]"
    }.toString
  }
}

object WorkList {
  def init(node : Node) : WorkList = new WorkList((node, 0, node.childrenCount) :: Nil)
  
  def empty : WorkList = new WorkList(Nil)
}