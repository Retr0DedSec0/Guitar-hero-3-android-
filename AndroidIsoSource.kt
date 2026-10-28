package com.gh3recomp.launcher.iso

import android.content.Context
import android.net.Uri
import java.io.Closeable
import java.io.FileInputStream
import java.io.IOException

/** Abre a ISO escolhida no seletor de arquivos (ACTION_OPEN_DOCUMENT). Só compila dentro do projeto Android. */
fun openIsoSource(context: Context, uri: Uri): IsoSource {
    val pfd = context.contentResolver.openFileDescriptor(uri, "r")
        ?: throw IOException("Não consegui abrir o arquivo escolhido.")
    val stream = FileInputStream(pfd.fileDescriptor)
    return ChannelIsoSource(stream.channel, Closeable { stream.close(); pfd.close() })
}
