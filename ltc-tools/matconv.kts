import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

val tokens = File("mat.txt").readText().split(",")
        .map {it.trim() }
        .map { it.toFloat() }

val binaryOut = File("ltc_mat.bin")
val backing = ByteArray(tokens.size * 4)
val buffer = ByteBuffer.wrap(backing)
buffer.order(ByteOrder.nativeOrder())
buffer.rewind()
tokens.forEach { buffer.putFloat(it) }
buffer.rewind()

binaryOut.writeBytes(backing)
