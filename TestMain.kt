import com.gh3recomp.launcher.iso.*
import java.io.File
import java.io.RandomAccessFile
import java.security.MessageDigest
import kotlin.system.exitProcess

fun sha(f: File) = MessageDigest.getInstance("SHA-256").digest(f.readBytes()).joinToString("") { "%02x".format(it) }

fun manifest(root: File): List<String> =
    root.walkTopDown().filter { it.isFile }.map { "${it.relativeTo(root).path.replace('\\', '/')} ${it.length()} ${sha(it)}" }.sorted().toList()

fun check(cond: Boolean, msg: String) { if (!cond) { println("FALHOU: $msg"); exitProcess(1) } else println("ok: $msg") }

fun main(args: Array<String>) {
    val iso = File(args[0]); val dest = File(args[1])
    RandomAccessFile(iso, "r").use { raf ->
        val src = ChannelIsoSource(raf.channel)
        val ex = GameExtractor(src)
        val plan = ex.plan()
        println("plano: ${plan.files.size} arquivos, ${plan.totalBytes} bytes")

        // 1) cancelamento no meio do arquivo grande
        var calls = 0
        val r0 = ex.extract(plan, dest, isCancelled = { ++calls > 2 })
        check(r0.cancelled, "cancelamento é respeitado")
        check(!ex.verify(plan, dest).ok, "verify detecta cópia incompleta")

        // 2) retomada
        val r1 = ex.extract(plan, dest)
        check(!r1.cancelled && r1.copied > 0, "retomada termina a cópia (copiou ${r1.copied}, pulou ${r1.skipped})")
        check(ex.verify(plan, dest).ok, "verify ok após extração")
        check(dest.walkTopDown().none { it.name.endsWith(".part") }, "nenhum .part sobrando")

        // 3) segunda execução não copia nada
        val r2 = ex.extract(plan, dest)
        check(r2.copied == 0 && r2.skipped == plan.files.size, "segunda execução pula tudo")

        // 4) arquivo faltando / tamanho errado é detectado e consertado
        File(dest, "DATA/MUSIC/song2.fsb").delete()
        File(dest, "DATA/COMPRESSED/a.pak").writeBytes(ByteArray(5))
        val rep = ex.verify(plan, dest)
        check(rep.missing == listOf("DATA/MUSIC/song2.fsb") && rep.wrongSize == listOf("DATA/COMPRESSED/a.pak"), "verify acha faltando + tamanho errado")
        val r3 = ex.extract(plan, dest)
        check(r3.copied == 2 && ex.verify(plan, dest).ok, "re-extração conserta só os 2 arquivos")

        // 5) progresso
        var last: ExtractionProgress? = null
        File(dest, "DATA/MUSIC/song1.fsb").delete()
        ex.extract(plan, dest, onProgress = { last = it })
        check(last!!.bytesDone == last!!.bytesTotal && last!!.filesDone == last!!.filesTotal, "progresso chega a 100%")
    }
    manifest(dest).forEach { println("M $it") }
}
