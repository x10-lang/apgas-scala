package apgas.scala
package util

import _root_.apgas.{ Place }
import _root_.apgas.util.{ GlobalRef => JGlobalRef }

sealed trait SharedRef[T] {
  def apply(): T
  def free(): Unit
  def home(): Place
}

object SharedRef {
  def make[T](init: T): SharedRef[T] = {
    new JGlobalRef(init) with SharedRef[T] {
      override def apply(): T = this.get()
    }
  }
}