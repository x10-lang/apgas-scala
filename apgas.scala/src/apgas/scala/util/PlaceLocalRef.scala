package apgas.scala
package util

import _root_.apgas.{ Place, SerializableCallable }
import _root_.apgas.util.{ GlobalRef => JGlobalRef }
import _root_.scala.collection.JavaConverters._

sealed trait PlaceLocalRef[T] {
  def apply() : T
}
  
object PlaceLocalRef {
  def forPlaces[T : APGASSerialization](places : Iterable[Place])(initializer : =>T) : PlaceLocalRef[T] = {
    val converter = implicitly[APGASSerialization[T]]
    
    new JGlobalRef(places.asJavaCollection, new SerializableCallable[converter.S]() {
      override def call() : converter.S = {
        converter.toSerializable(initializer)
      }
    }) with PlaceLocalRef[T] {
      override def apply() : T = converter.fromSerializable(this.get())
    }
  }
}