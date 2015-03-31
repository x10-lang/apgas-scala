package apgas

import _root_.scala.collection.JavaConverters._

package object scala {
  trait APGASSerialization[T] {
    type S <: java.io.Serializable
    def toSerializable(x : T) : S
    def fromSerializable(x : S) : T
  }
  
  implicit def javaToAPGASSerialization[T <: java.io.Serializable] = new APGASSerialization[T]() {
    override type S = T
    override def toSerializable(x : T) = x
    override def fromSerializable(x : S) = x
  }
  
  implicit val longHasAPGASSerialization = new APGASSerialization[Long]() {
    override type S = java.lang.Long
    override def toSerializable(x : Long) = new java.lang.Long(x)
    override def fromSerializable(x : java.lang.Long) : Long = x
  }
  
  def apgas(block : =>Unit) : Unit = {
    val rt = GlobalRuntime.getRuntime()
    block
    rt.shutdown()
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
      override def run() : Unit = {
        body
      }
    })
  }
  
  def uncountedAsyncAt(place : Place)(body : =>Unit) : Unit = {
    Constructs.uncountedasyncat(place, new SerializableJob() {
      override def run() : Unit = {
        body
      }
    })
  }

  def finish(body : =>Unit) : Unit = {
    Constructs.finish(new Job {
      override def run() : Unit = {
        body
      }
    })
  }
  
  def at[T : APGASSerialization](place : Place)(body : =>T) : T = {
    val converter = implicitly[APGASSerialization[T]]
    
    val fromWire = Constructs.at(place, new SerializableCallable[converter.S]() {
      override def call() : converter.S = {
        converter.toSerializable(body)
      }
    })
    
    converter.fromSerializable(fromWire)
  }

  def here : Place = Constructs.here()
  
  def place(id : Int) : Place = Constructs.place(id)

  def places : Iterable[Place] = Constructs.places().asScala
}