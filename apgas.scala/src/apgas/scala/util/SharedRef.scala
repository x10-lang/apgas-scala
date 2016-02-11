/*
 *  This file is part of the X10 project (http://x10-lang.org).
 *
 *  This file is licensed to You under the Eclipse Public License (EPL);
 *  You may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *      http://www.opensource.org/licenses/eclipse-1.0.php
 *
 *  (C) Copyright IBM Corporation 2015-2016.
 */

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