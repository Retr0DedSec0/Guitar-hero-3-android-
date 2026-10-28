import com.gh3recomp.launcher.iso.*
import java.io.File
import java.io.RandomAccessFile
import kotlin.system.exitProcess

fun expectFormatError(label: String, f: File) {
    try {
        RandomAccessFile(f, "r").use { GameExtractor(ChannelIsoSource(it.channel)).plan() }
        println("FALHOU: $label não deu erro"); exitProcess(1)
    } catch (e: IsoFormatException) { println("ok: $label -> ${e.message}") }
}

fun main(args: Array<String>) {
    val junk = File("junk.bin").apply { writeBytes(ByteArray(300_000) { (it * 31).toByte() }) }
    expectFormatError("arquivo qualquer", junk)
    val full = File(args[0])
    val cut = File("cut.iso").apply { RandomAccessFile(this, "rw").use { it.setLength(full.length() - 3_000_000); } }
    full.inputStream().use { i -> cut.outputStream().use { o -> i.copyTo(o) } }
    RandomAccessFile(cut, "rw").use { it.setLength(full.length() - 1_000_000) }
    expectFormatError("ISO truncada", cut)
}
