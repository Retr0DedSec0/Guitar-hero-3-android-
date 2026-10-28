package com.gh3recomp.launcher.iso

import java.io.Closeable
import java.io.EOFException
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.channels.FileChannel

/** Erro de formato: o arquivo não é uma ISO de Xbox 360 válida (ou está truncado). */
class IsoFormatException(message: String) : IOException(message)

/** Fonte de leitura com acesso aleatório. Sem dependências do Android, para poder ser testada no PC. */
interface IsoSource : Closeable {
    val size: Long

    /** Lê exatamente [length] bytes a partir de [position]; lança [EOFException] se faltar dado. */
    fun readFully(position: Long, dst: ByteArray, offset: Int = 0, length: Int = dst.size)
}

class ChannelIsoSource(
    private val channel: FileChannel,
    private val onClose: Closeable? = null,
) : IsoSource {

    override val size: Long get() = channel.size()

    override fun readFully(position: Long, dst: ByteArray, offset: Int, length: Int) {
        val buf = ByteBuffer.wrap(dst, offset, length)
        var pos = position
        while (buf.hasRemaining()) {
            val n = channel.read(buf, pos)
            if (n < 0) throw EOFException("Fim inesperado da ISO em $pos")
            pos += n
        }
    }

    override fun close() {
        try { channel.close() } finally { onClose?.close() }
    }
}
