package com.gh3recomp.launcher.iso

/** Uma entrada (arquivo ou pasta) da ISO. [absoluteOffset] já inclui o offset da partição de jogo. */
data class XisoEntry(
    val path: String,            // "DATA/MUSIC/x.fsb", sempre com '/'
    val isDirectory: Boolean,
    val absoluteOffset: Long,    // posição do dado dentro do arquivo .iso
    val size: Long,
)

/**
 * Leitor de XDVDFS ("XISO"), o sistema de arquivos dos discos de Xbox 360.
 *
 * Aceita ISO crua/reduzida (partição em 0x0) e ISOs completas de XGD1/XGD2/XGD3,
 * achando a partição pelo descritor de volume (setor 32, assinatura MICROSOFT*XBOX*MEDIA).
 */
class XisoReader private constructor(
    private val src: IsoSource,
    val partitionOffset: Long,
    val layoutName: String,
    private val rootSector: Long,
    private val rootSize: Long,
) {

    /** Percorre toda a árvore e devolve pastas e arquivos. */
    fun listEntries(): List<XisoEntry> {
        val out = ArrayList<XisoEntry>()
        walkDir(rootSector, rootSize, "", out, 0)
        return out
    }

    private fun walkDir(sector: Long, size: Long, prefix: String, out: MutableList<XisoEntry>, depth: Int) {
        if (depth > MAX_DEPTH) throw IsoFormatException("Hierarquia de pastas profunda demais (ISO corrompida?)")
        if (size < 14) return
        val table = ByteArray(size.toInt())
        src.readFully(partitionOffset + sector * SECTOR, table)
        if (u16(table, 0) == 0xFFFF) return // pasta vazia: tabela toda 0xFF

        // A tabela é uma árvore binária; os ponteiros esquerdo/direito contam em dwords, 0 = sem filho.
        val visited = HashSet<Int>()
        val stack = ArrayDeque<Int>()
        stack.addLast(0)
        while (stack.isNotEmpty()) {
            val dw = stack.removeLast()
            if (!visited.add(dw)) throw IsoFormatException("Ciclo na tabela de diretório (ISO corrompida?)")
            val o = dw * 4
            if (o + 14 > table.size) throw IsoFormatException("Entrada de diretório fora da tabela")

            val left = u16(table, o)
            val right = u16(table, o + 2)
            val start = u32(table, o + 4)
            val len = u32(table, o + 8)
            val attr = table[o + 12].toInt() and 0xFF
            val nameLen = table[o + 13].toInt() and 0xFF
            if (nameLen == 0 || o + 14 + nameLen > table.size) throw IsoFormatException("Nome de arquivo inválido na ISO")

            val name = String(table, o + 14, nameLen, Charsets.ISO_8859_1)
            validateName(name)
            val path = if (prefix.isEmpty()) name else "$prefix/$name"

            if (attr and ATTR_DIRECTORY != 0) {
                out.add(XisoEntry(path, true, 0, 0))
                if (len > 0) walkDir(start, len, path, out, depth + 1)
            } else {
                out.add(XisoEntry(path, false, partitionOffset + start * SECTOR, len))
            }

            if (left != 0 && left != 0xFFFF) stack.addLast(left)
            if (right != 0 && right != 0xFFFF) stack.addLast(right)
        }
    }

    private fun validateName(name: String) {
        // Impede que uma ISO maliciosa escreva fora da pasta de destino.
        if (name == "." || name == ".." || name.any { it == '/' || it == '\\' || it == ':' || it == '\u0000' }) {
            throw IsoFormatException("Nome de arquivo suspeito na ISO: \"$name\"")
        }
    }

    companion object {
        const val SECTOR = 2048L
        private const val ATTR_DIRECTORY = 0x10
        private const val MAX_DEPTH = 32
        private const val MAX_DIR_TABLE = 16L * 1024 * 1024
        private val MAGIC = "MICROSOFT*XBOX*MEDIA".toByteArray(Charsets.US_ASCII)

        /** Onde a partição de jogo pode começar dentro do arquivo .iso. */
        private val LAYOUTS = listOf(
            "ISO crua/reduzida" to 0L,
            "XGD3" to 0x2080000L,
            "XGD2" to 0xFD90000L,
            "XGD1" to 0x18300000L,
        )

        fun open(src: IsoSource): XisoReader {
            if (src.size <= 0) {
                throw IsoFormatException("Não consegui ler o tamanho do arquivo. Use um arquivo local do aparelho, não da nuvem.")
            }
            val head = ByteArray(28)
            val tail = ByteArray(MAGIC.size)
            for ((name, base) in LAYOUTS) {
                val vd = base + 32 * SECTOR
                if (vd + SECTOR > src.size) continue
                src.readFully(vd, head)
                if (!head.copyOfRange(0, MAGIC.size).contentEquals(MAGIC)) continue
                src.readFully(vd + 0x7EC, tail)
                if (!tail.contentEquals(MAGIC)) continue

                val rootSector = u32(head, 20)
                val rootSize = u32(head, 24)
                val rootEnd = base + rootSector * SECTOR + rootSize
                if (rootSize <= 0 || rootSize > MAX_DIR_TABLE || rootEnd > src.size) {
                    throw IsoFormatException("Diretório raiz fora da ISO (arquivo truncado?)")
                }
                return XisoReader(src, base, name, rootSector, rootSize)
            }
            throw IsoFormatException("Isso não parece uma ISO de Xbox 360 (assinatura XISO não encontrada).")
        }
    }
}

private fun u16(b: ByteArray, o: Int): Int = (b[o].toInt() and 0xFF) or ((b[o + 1].toInt() and 0xFF) shl 8)
private fun u32(b: ByteArray, o: Int): Long = u16(b, o).toLong() or (u16(b, o + 2).toLong() shl 16)
