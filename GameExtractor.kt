package com.gh3recomp.launcher.iso

import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/** O que copiar da ISO (a lista vem do ANDROID.md do port). Todo o resto é ignorado. */
class ExtractionRules(
    val rootFiles: List<String> = listOf("default.xex"),
    val dataDirs: List<String> = listOf(
        "COMPRESSED", "MUSIC", "MOVIES", "STREAMS", "FXFILES", "ANIMS", "MEMCARD", "SCRIPTS",
    ),
) {
    private val rootByLower = rootFiles.associateBy { it.lowercase() }
    private val dirByLower = dataDirs.associateBy { it.lowercase() }

    /**
     * Devolve o caminho de destino (com a capitalização que o jogo espera) ou null se o arquivo não interessa.
     * O Xbox ignora maiúsculas/minúsculas e o Android não, então normalizamos os dois primeiros níveis.
     */
    fun destinationFor(isoPath: String): String? {
        val parts = isoPath.split('/')
        val first = parts[0].lowercase()
        if (parts.size == 1) return rootByLower[first]
        if (first != "data" || parts.size < 3) return null
        val dir = dirByLower[parts[1].lowercase()] ?: return null
        return (listOf("DATA", dir) + parts.drop(2)).joinToString("/")
    }
}

data class PlannedFile(val isoPath: String, val destPath: String, val offset: Long, val size: Long)

data class ExtractionPlan(val files: List<PlannedFile>) {
    val totalBytes: Long = files.sumOf { it.size }
}

data class ExtractionProgress(
    val filesDone: Int,
    val filesTotal: Int,
    val bytesDone: Long,
    val bytesTotal: Long,
    val currentFile: String,
)

data class ExtractionResult(val copied: Int, val skipped: Int, val cancelled: Boolean)

data class VerifyReport(val missing: List<String>, val wrongSize: List<String>) {
    val ok: Boolean get() = missing.isEmpty() && wrongSize.isEmpty()
}

class InsufficientSpaceException(val neededBytes: Long, val availableBytes: Long) :
    IOException("Espaço insuficiente: preciso de ${neededBytes / MB} MB livres e só há ${availableBytes / MB} MB.")

private const val MB = 1024L * 1024L

class GameExtractor(
    private val src: IsoSource,
    private val reader: XisoReader = XisoReader.open(src),
    private val rules: ExtractionRules = ExtractionRules(),
) {

    /** Lê o índice da ISO e monta a lista do que será copiado. Não escreve nada em disco. */
    fun plan(): ExtractionPlan {
        val files = ArrayList<PlannedFile>()
        for (e in reader.listEntries()) {
            if (e.isDirectory) continue
            val dest = rules.destinationFor(e.path) ?: continue
            if (e.absoluteOffset + e.size > src.size) {
                throw IsoFormatException("ISO truncada: \"${e.path}\" termina depois do fim do arquivo.")
            }
            files.add(PlannedFile(e.path, dest, e.absoluteOffset, e.size))
        }
        if (files.none { it.destPath.equals("default.xex", ignoreCase = true) }) {
            throw IsoFormatException("default.xex não está na raiz da ISO. Esse disco não é o Guitar Hero III?")
        }
        // Ordem do disco = leitura sequencial, bem mais rápida no armazenamento do celular.
        return ExtractionPlan(files.sortedBy { it.offset })
    }

    /**
     * Copia os arquivos do plano para [destRoot] (ex.: /sdcard/gh3/game).
     * Retomável: arquivo que já existe com o tamanho certo é pulado; cópias incompletas ficam em *.part.
     */
    fun extract(
        plan: ExtractionPlan,
        destRoot: File,
        onProgress: (ExtractionProgress) -> Unit = {},
        isCancelled: () -> Boolean = { false },
    ): ExtractionResult {
        destRoot.mkdirs()
        val rootCanon = destRoot.canonicalFile

        fun target(f: PlannedFile): File {
            val t = File(rootCanon, f.destPath).canonicalFile
            if (!t.path.startsWith(rootCanon.path + File.separator)) throw IsoFormatException("Caminho inválido: ${f.destPath}")
            return t
        }

        val pending = plan.files.filter { f -> target(f).let { !(it.isFile && it.length() == f.size) } }
        val needed = pending.sumOf { it.size } + SPACE_MARGIN
        val free = rootCanon.usableSpace
        if (needed > free) throw InsufficientSpaceException(needed, free)

        var bytesDone = plan.totalBytes - pending.sumOf { it.size }
        var filesDone = plan.files.size - pending.size
        var copied = 0
        var lastEmit = 0L
        val buf = ByteArray(1 shl 20)

        fun emit(name: String, force: Boolean) {
            val now = System.nanoTime()
            if (force || now - lastEmit > 200_000_000L) {
                lastEmit = now
                onProgress(ExtractionProgress(filesDone, plan.files.size, bytesDone, plan.totalBytes, name))
            }
        }

        emit("", force = true)
        for (f in pending) {
            val dest = target(f)
            dest.parentFile?.mkdirs()
            val part = File(dest.path + ".part")
            FileOutputStream(part).use { out ->
                var done = 0L
                while (done < f.size) {
                    if (isCancelled()) return ExtractionResult(copied, plan.files.size - pending.size, cancelled = true)
                    val n = minOf(buf.size.toLong(), f.size - done).toInt()
                    src.readFully(f.offset + done, buf, 0, n)
                    out.write(buf, 0, n)
                    done += n
                    bytesDone += n
                    emit(f.destPath, force = false)
                }
            }
            if (dest.exists() && !dest.delete()) throw IOException("Não consegui substituir ${dest.path}")
            if (!part.renameTo(dest)) throw IOException("Não consegui finalizar ${dest.path}")
            copied++
            filesDone++
            emit(f.destPath, force = true)
        }
        return ExtractionResult(copied, plan.files.size - pending.size, cancelled = false)
    }

    /** Confere se tudo do plano está em [destRoot] com o tamanho certo (pega o caso "faltou um vídeo em MOVIES"). */
    fun verify(plan: ExtractionPlan, destRoot: File): VerifyReport {
        val missing = ArrayList<String>()
        val wrong = ArrayList<String>()
        for (f in plan.files) {
            val t = File(destRoot, f.destPath)
            when {
                !t.isFile -> missing.add(f.destPath)
                t.length() != f.size -> wrong.add(f.destPath)
            }
        }
        return VerifyReport(missing, wrong)
    }

    private companion object {
        const val SPACE_MARGIN = 256L * 1024 * 1024
    }
}
