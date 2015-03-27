package apgas

import _root_.scala.collection.JavaConverters._

package object scala {
  def apgas(block : =>Unit) : Unit = {
    val rt = GlobalRuntime.getRuntime()
    block
    // FIXME.. maybe do some reference counting and only shutdown if last.
    //rt.shutdown()
  }

  def async(body : =>Unit) : Unit = {
    Constructs.async(new Job {
      override def run() {
        body
      }
    })
  }

  def asyncAt(place : Place)(body : =>Unit) : Unit = {
    Constructs.asyncat(place, new SerializableJob() {
      override def run() {
        body
      }
    })
  }

  def finish(body : =>Unit) : Unit = {
    Constructs.finish(new Job {
      override def run() {
        body
      }
    })
  }

  def here : Place = Constructs.here()

  def places : Iterable[Place] = Constructs.places().asScala
}