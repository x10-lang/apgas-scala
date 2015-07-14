/*
 *  This file is part of the X10 project (http://x10-lang.org).
 *
 *  This file is licensed to You under the Eclipse Public License (EPL);
 *  You may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *      http://www.opensource.org/licenses/eclipse-1.0.php
 *
 *  (C) Copyright IBM Corporation 2015.
 */

package apgas.scala
package util

import _root_.apgas.{ Place, SerializableCallable }
import _root_.apgas.util.{ GlobalRef => JGlobalRef }
import _root_.scala.collection.JavaConverters._

sealed trait GlobalRef[T] {
  def apply() : T
  def free() : Unit
}
  
object GlobalRef {
  def forPlaces[T : APGASSerialization](places : Iterable[Place])(initializer : =>T) : GlobalRef[T] = {
    val converter = implicitly[APGASSerialization[T]]
    
    new JGlobalRef(places.asJavaCollection, new SerializableCallable[converter.S]() {
      override def call() : converter.S = {
        converter.toSerializable(initializer)
      }
    }) with GlobalRef[T] {
      override def apply() : T = converter.fromSerializable(this.get())
    }
  }
}