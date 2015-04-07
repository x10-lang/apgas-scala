package apgas.scala.examples
package uts

import java.security.MessageDigest
import java.nio.ByteBuffer
import java.nio.ByteOrder

final class NodeHasher {
  private val encoder   = MessageDigest.getInstance("SHA-1")
  private val inBuffer  = ByteBuffer.allocateDirect(24).order(NodeHasher.endianness)
  private val hashOut   = new Array[Byte](20)
  private val outBuffer = ByteBuffer.wrap(hashOut).order(NodeHasher.endianness)
  
  /*
  def childHash(base : Array[Byte], index : Int) : Array[Byte] = {
    val n = index
    val h2 = Array[Byte]((n >> 24).toByte, (n >> 16).toByte, (n >> 8).toByte, n.toByte)
    encoder.update(base, 0, 20)
    encoder.update(h2, 0, 4)
    val result = new Array[Byte](20)
    encoder.digest(result, 0, 20)
    result
  }
  */
  
  def childHash(b0 : Long, b1 : Long, b2 : Int, n : Int) : (Long,Long,Int) = {
    inBuffer.clear()
    inBuffer.putLong(b0).putLong(b1).putInt(b2).putInt(n).rewind()
    encoder.update(inBuffer)
    encoder.digest(hashOut, 0, 20)
    outBuffer.rewind()
    (outBuffer.getLong(0), outBuffer.getLong(8), outBuffer.getInt(16))
  }
}

object NodeHasher {
  val endianness = ByteOrder.BIG_ENDIAN
  
  def initial(seed : Int) : (Long,Long,Int) = {
    val encoder : MessageDigest = MessageDigest.getInstance("SHA-1")
    val buffer = ByteBuffer.allocate(20).order(endianness)
    buffer.putLong(0).putLong(0).putInt(seed).rewind()
    encoder.update(buffer)
    val out = ByteBuffer.wrap(encoder.digest()).order(endianness)
    (out.getLong(0), out.getLong(8), out.getInt(16))
  }
}