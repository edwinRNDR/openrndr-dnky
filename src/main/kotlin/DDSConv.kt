//import gli_.gli
//import java.io.File
//import java.nio.ByteBuffer
//import java.nio.ByteOrder
//
//fun main(args: Array<String>) {
//
//    val t = gli.load("offline-data/ltc_mat.dds")
//
//    println(t.format)
//    println(t.format.isCompressed)
//    println(t.extent(0))
//    println(t.layers())
//
//    val e = t.extent(0)
//    val backing = ByteArray(e.x * e.y * 4 * 4)
//
//    val outBuffer = ByteBuffer.wrap(backing)
//    outBuffer.order(ByteOrder.nativeOrder())
//    val data = t.data()
//
//    data.rewind()
//
//    outBuffer.rewind()
//    for (j in 0 until e.y) {
//        for (i in 0 until e.x) {
//            val r = data.getFloat()
//            val g = data.getFloat()
//            val b = data.getFloat()
//            val a = data.getFloat()
//            outBuffer.putFloat(r)
//            outBuffer.putFloat(g)
//            outBuffer.putFloat(b)
//            outBuffer.putFloat(a)
//        }
//    }
//
//    outBuffer.rewind()
//
//    File("ltc_mat.bin").writeBytes(backing)
//}