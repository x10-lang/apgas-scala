package apgas.scala
package util

import _root_.apgas.Place
import _root_.apgas.util.GlobalID
import _root_.scala.collection.JavaConverters._

trait PlaceLocal extends Serializable {
  private[apgas] var id : GlobalID = _
  
  protected def writeReplace() : java.lang.Object = {
    return new PlaceLocal.ProxyObject(id)
  }
}

object PlaceLocal {
  private class ProxyObject(val id : GlobalID) extends Serializable {
    protected def readResolve() : java.lang.Object = {
      return id.getHere()
    }
  }

  def forPlaces[T <: PlaceLocal](places : Iterable[Place])(initializer : => T) : T = {
    val id = new GlobalID()
    
    finish {
      for(p <- places) {
        asyncAt(p) {
          val t = initializer
          t.id = id
          id.putHere(t)
        }
      }
    }
    
    id.getHere().asInstanceOf[T]
  }
}