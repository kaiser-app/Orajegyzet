package hu.orajegyzet.ui

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import hu.orajegyzet.ai.GemmaModel
import hu.orajegyzet.ai.GemmaSession
import hu.orajegyzet.data.Settings
import kotlinx.coroutines.runBlocking
import java.io.File

/**
 * Offline modellek telepítése a rendszer fájlválasztójával (Storage Access
 * Framework) — adb nélkül. A felhasználó a telefon böngészőjével letölti a
 * fájlt a Letöltések mappába, majd itt importálja.
 *
 *  - Whisper: a kiválasztott ggml .bin bemásolása files/models/whisper.bin néven.
 *  - Gemma:   a kiválasztott .task / MediaPipe .bin bemásolása RÖGZÍTETT néven
 *             (files/models/gemma.task vagy gemma.bin), az eredeti név a beállításokba kerül.
 */
object ModelImporter {

    data class Status(
        val gemmaInstalled: Boolean,
        val whisperInstalled: Boolean,
        val gemmaFileName: String? = null,
        val gemmaSizeBytes: Long = 0
    )

    data class GemmaImportResult(val originalName: String, val sizeBytes: Long, val loadSeconds: Double)

    private fun modelsDir(ctx: Context) = GemmaModel.modelsDir(ctx)

    fun status(ctx: Context): Status {
        val gemma = GemmaModel.file(ctx)
        return Status(
            gemmaInstalled = gemma != null,
            whisperInstalled = File(modelsDir(ctx), "whisper.bin").let { it.isFile && it.length() > 0 },
            gemmaFileName = gemma?.name,
            gemmaSizeBytes = gemma?.length() ?: 0
        )
    }

    fun importWhisper(ctx: Context, uri: Uri) {
        val target = File(modelsDir(ctx), "whisper.bin")
        val tmp = File(modelsDir(ctx), "whisper.bin.part")
        ctx.contentResolver.openInputStream(uri).use { input ->
            requireNotNull(input) { "Nem olvasható a kiválasztott fájl" }
            tmp.outputStream().use { out -> input.copyTo(out, 1 shl 20) }
        }
        check(tmp.length() > 10_000_000) { tmp.delete(); "A fájl gyanúsan kicsi — biztosan Whisper .bin modell?" }
        target.delete(); check(tmp.renameTo(target)) { "Nem sikerült véglegesíteni" }
    }

    /**
     * Gemma importálása. Hibánál kivételt dob, magyar, felhasználónak szóló üzenettel.
     * Siker esetén próbabetöltést is végez (rövid prompt, 120 mp időkorláttal).
     */
    fun importGemma(ctx: Context, uri: Uri): GemmaImportResult {
        var originalName = "gemma"
        ctx.contentResolver.query(uri, null, null, null, null)?.use {
            if (it.moveToFirst()) {
                val idx = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0) it.getString(idx)?.let { n -> originalName = n }
            }
        }

        val header = ByteArray(8)
        val read = ctx.contentResolver.openInputStream(uri)?.use { it.read(header) } ?: -1
        check(read > 0) { "Nem olvasható a kiválasztott fájl." }
        val format = GemmaModel.detectFormat(header, originalName)
        GemmaModel.rejectionMessage(format)?.let { error(it) }

        val fixedName = if (format == GemmaModel.Format.TASK) GemmaModel.TASK_NAME else GemmaModel.BIN_NAME
        val target = File(modelsDir(ctx), fixedName)
        val tmp = File(modelsDir(ctx), "$fixedName.part")
        tmp.delete()

        ctx.contentResolver.openInputStream(uri).use { input ->
            requireNotNull(input) { "Nem olvasható a kiválasztott fájl." }
            tmp.outputStream().use { out -> input.copyTo(out, 1 shl 20) }
        }
        if (tmp.length() <= GemmaModel.MIN_SIZE_BYTES) {
            tmp.delete()
            error("A fájl gyanúsan kicsi — biztosan MediaPipe Gemma-modell?")
        }

        // A korábbi Gemma-modellek (rögzített és régi, eredeti nevű) eltávolítása
        modelsDir(ctx).listFiles()?.filter {
            it.isFile && it != tmp && (it.name == GemmaModel.TASK_NAME || it.name == GemmaModel.BIN_NAME ||
                it.name.lowercase().startsWith("gemma"))
        }?.forEach { it.delete() }

        check(tmp.renameTo(target)) { tmp.delete(); "Nem sikerült véglegesíteni a modellfájlt." }

        // Próbabetöltés: ha nem sikerül, a fájl törlődik, és a hiba megjelenik.
        val loadSeconds = try {
            GemmaSession.open(ctx, target).use { s ->
                s.generate("Válaszolj egy szóval: OK", timeoutMs = 120_000L)
                s.loadMillis / 1000.0
            }
        } catch (e: Exception) {
            target.delete()
            throw IllegalStateException("A modell bemásolódott, de nem tölthető be, ezért töröltem. Ok: ${e.message}", e)
        }

        runBlocking { Settings.setGemmaModelName(ctx, originalName) }
        return GemmaImportResult(originalName, target.length(), loadSeconds)
    }

    /** Régi (1.0.3 előtti) Vosk-modell törlése: a Vosk 1.0.4-től nincs az appban. */
    fun deleteLegacyVosk(ctx: Context): Boolean {
        val dirs = listOf(File(modelsDir(ctx), "vosk-hu"), File(modelsDir(ctx), "vosk-hu.part"))
        var deleted = false
        dirs.filter { it.exists() }.forEach { deleted = it.deleteRecursively() || deleted }
        return deleted
    }
}
