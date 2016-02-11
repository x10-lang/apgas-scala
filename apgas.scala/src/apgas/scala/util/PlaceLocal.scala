/*
 *  This file is part of the X10 project (http://x10-lang.org).
 *
 *  This file is licensed to You under the Eclipse Public License (EPL);
 *  You may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *      http://www.opensource.org/licenses/eclipse-1.0.php
 *
 *  (C) Copyright IBM Corporation 2015-2016.2016.
 */

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