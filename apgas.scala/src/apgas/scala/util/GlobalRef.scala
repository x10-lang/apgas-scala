package apgas.scala
package util

import _root_.apgas.{ Place, SerializableCallable }
import _root_.apgas.util.{ GlobalRef => JGlobalRef }
import _root_.scala.collection.JavaConverters._

sealed trait GlobalRef[T] {
  def apply() : T
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