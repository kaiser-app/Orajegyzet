package hu.orajegyzet.ui

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import hu.orajegyzet.data.Settings
import kotlinx.coroutines.runBlocking
import java.io.File
import java.util.zip.ZipInputStream

/**
 * Offline modellek telepítése a rendszer fájlválasztójával (Storage Access
 * Framework) — adb nélkül. A felhasználó a telefon böngészőjével letölti a
 * fájlt a Letöltések mappába, majd itt importálja.
 *
 *  - Gemma: a kiválasztott .task fájl bemásolása files/models/gemma.task néven.
 *  - Vosk:  a kiválasztott .zip kicsomagolása files/models/vosk-hu mappába
 *           (a zip-en belüli legfelső könyvtárnév levágásával).
 */
object ModelImporter {

    data class Status(val voskInstalled: Boolean, val gemmaInstalled: Boolean, val whisperInstalled: Boolean)

    private fun modelsDir(ctx: Context) = File(ctx.filesDir, "models").apply { mkdirs() }

    fun status(ctx: Context) = Status(
        voskInstalled = File(modelsDir(ctx), "vosk-hu").let { it.isDirectory && (it.list()?.isNotEmpty() == true) },
        gemmaInstalled = File(modelsDir(ctx), "gemma.task").let { it.isFile && it.length() > 0 },
        whisperInstalled = File(modelsDir(ctx), "whisper.bin").let { it.isFile && it.length() > 0 }
    )

    fun importWhisper(ctx: Context, uri: Uri) {
        val target = File(modelsDir(ctx), "whisper.bin")
        val tmp = File(modelsDir(ctx), "whisper.bin.part")
        ctx.contentResolver.openInputStream(uri).use { input ->
            requireNotNull(input) { "Nem olvasható a kiválasztott fájl" }
            tmp.outputStream().use { out -> input.copyTo(out, 1 shl 20) }
        }
        check(tmp.length() > 10_000_000) { "A fájl gyanúsan kicsi — biztosan Whisper .bin modell?" }
        target.delete(); check(tmp.renameTo(target)) { "Nem sikerült véglegesíteni" }
    }

    fun importGemma(ctx: Context, uri: Uri) {
        val bytes = ByteArray(8)
        ctx.contentResolver.openInputStream(uri)?.use { it.read(bytes) }
        
        // 50 4B 03 04 -> PK -> .task
        val isTask = bytes[0] == 0x50.toByte() && bytes[1] == 0x4B.toByte() && bytes[2] == 0x03.toByte() && bytes[3] == 0x04.toByte()
        // TFL3 -> TFLite -> .bin
        val isTflite = bytes[4] == 'T'.code.toByte() && bytes[5] == 'F'.code.toByte() && bytes[6] == 'L'.code.toByte() && bytes[7] == '3'.code.toByte()
        
        val isGgml = String(bytes, 0, 4) in listOf("lmgg", "ggml", "ggjt")
        if (isGgml) {
            error("Ez Whisper beszédfelismerő modell, nem Gemma. A Whisper importálásánál töltsd be.")
        }
        if (!isTask && !isTflite) {
            error("Ez nem MediaPipe-formátumú Gemma (a Gemma 4 .litertlm a Fülelő 2-be való). Ide .task vagy MediaPipe .bin kell.")
        }

        val ext = if (isTask) ".task" else ".bin"
        
        // Read file name from URI
        var originalName = "gemma$ext"
        ctx.contentResolver.query(uri, null, null, null, null)?.use {
            if (it.moveToFirst()) {
                val idx = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0) originalName = it.getString(idx)
            }
        }
        
        val target = File(modelsDir(ctx), originalName)
        val tmp = File(modelsDir(ctx), "$originalName.part")
        
        ctx.contentResolver.openInputStream(uri).use { input ->
            requireNotNull(input) { "Nem olvasható a kiválasztott fájl" }
            tmp.outputStream().use { out -> input.copyTo(out, 1 shl 20) }
        }
        check(tmp.length() > 1_000_000) { "A fájl gyanúsan kicsi — biztosan MediaPipe modell?" }
        
        // Eltávolítjuk a korábbi gemma modelleket
        modelsDir(ctx).listFiles()?.filter { it.name.startsWith("gemma") }?.forEach { it.delete() }
        
        check(tmp.renameTo(target)) { "Nem sikerült véglegesíteni" }
        
        // Save name to settings
        runBlocking<Unit> {
            val settings = Settings.get(ctx)
            val updated = settings.copy(gemmaModelName = target.name)
            ctx.getSharedPreferences("settings_migration", Context.MODE_PRIVATE).edit().putString("gemma_model_name", target.name).apply()
        }
    }

    fun importVoskZip(ctx: Context, uri: Uri) {
        val target = File(modelsDir(ctx), "vosk-hu")
        val tmp = File(modelsDir(ctx), "vosk-hu.part").apply { deleteRecursively(); mkdirs() }
        ctx.contentResolver.openInputStream(uri).use { input ->
            requireNotNull(input) { "Nem olvasható a kiválasztott fájl" }
            ZipInputStream(input.buffered()).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    val name = entry.name
                    if (!name.contains("__MACOSX") && !name.startsWith(".")) {
                        // Ha a zip tetején van egy csomagoló mappa (pl. vosk-model-small-hu-0.22/), levágjuk
                        val hasTopFolder = name.contains('/') && !name.startsWith("am/") && !name.startsWith("conf/") && !name.startsWith("graph/") && !name.startsWith("ivector/")
                        val rel = if (hasTopFolder) name.substringAfter('/') else name
                        if (rel.isNotEmpty()) {
                            val out = File(tmp, rel)
                            val base = tmp.canonicalPath + File.separator
                            check(out.canonicalPath.startsWith(base) || out.canonicalPath == tmp.canonicalPath) { "Érvénytelen zip-bejegyzés" }
                            if (entry.isDirectory) out.mkdirs()
                            else {
                                out.parentFile?.mkdirs()
                                out.outputStream().use { o -> zip.copyTo(o, 1 shl 16) }
                            }
                        }
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }
        }
        check(File(tmp, "am").exists() || File(tmp, "conf").exists() || (tmp.list()?.isNotEmpty() == true)) {
            "A zip nem tűnik Vosk-modellnek"
        }
        target.deleteRecursively()
        check(tmp.renameTo(target)) { "Nem sikerült véglegesíteni" }
    }
}
