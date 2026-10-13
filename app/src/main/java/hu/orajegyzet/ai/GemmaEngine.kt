package hu.orajegyzet.ai

import android.content.Context
import android.util.Log
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.google.mediapipe.tasks.genai.llminference.LlmInferenceSession
import java.io.Closeable
import java.io.File
import java.io.FileInputStream
import java.util.concurrent.Callable
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/**
 * A Gemma-modellfájl egyetlen, közös kezelése (1.0.4).
 *
 * A modell MINDIG rögzített néven van a files/models mappában:
 *  - models/gemma.task  (MediaPipe .task, ZIP-fejléc „PK\u0003\u0004")
 *  - models/gemma.bin   (MediaPipe .bin, TFLite-fejléc „TFL3" a 4–7. bájton)
 * Az eredeti fájlnév csak a beállításokban (gemmaModelName) tárolódik.
 */
object GemmaModel {

    enum class Format { TASK, TFLITE, GGML, LITERTLM, UNKNOWN }

    const val MIN_SIZE_BYTES = 1_000_000L
    const val TASK_NAME = "gemma.task"
    const val BIN_NAME = "gemma.bin"

    fun modelsDir(ctx: Context): File = File(ctx.filesDir, "models").apply { mkdirs() }

    /** A telepített Gemma-modell, vagy null, ha nincs (vagy gyanúsan kicsi). */
    fun file(ctx: Context): File? {
        val dir = modelsDir(ctx)
        return listOf(File(dir, TASK_NAME), File(dir, BIN_NAME))
            .firstOrNull { it.isFile && it.length() > MIN_SIZE_BYTES }
    }

    /** Fejléc (első 8 bájt) + opcionálisan a fájlnév alapján felismert formátum. Tiszta függvény (unit teszt). */
    fun detectFormat(header: ByteArray, fileName: String? = null): Format {
        if (header.size >= 4) {
            if (header[0] == 0x50.toByte() && header[1] == 0x4B.toByte() &&
                header[2] == 0x03.toByte() && header[3] == 0x04.toByte()) return Format.TASK
            val magic = String(header, 0, 4, Charsets.ISO_8859_1)
            if (magic in setOf("lmgg", "ggml", "ggjt", "ggmf")) return Format.GGML
            if (magic.startsWith("LITE")) return Format.LITERTLM
        }
        if (header.size >= 8 && header[4] == 'T'.code.toByte() && header[5] == 'F'.code.toByte() &&
            header[6] == 'L'.code.toByte() && header[7] == '3'.code.toByte()) return Format.TFLITE
        if (fileName?.lowercase()?.endsWith(".litertlm") == true) return Format.LITERTLM
        return Format.UNKNOWN
    }

    fun readHeader(f: File): ByteArray {
        val b = ByteArray(8)
        FileInputStream(f).use { it.read(b) }
        return b
    }

    /** Felhasználónak szóló hibaüzenet a nem elfogadható formátumokra (null = elfogadható). */
    fun rejectionMessage(format: Format): String? = when (format) {
        Format.TASK, Format.TFLITE -> null
        Format.GGML -> "Ez Whisper beszédfelismerő modell, nem Gemma. A Whisper importálásánál töltsd be."
        Format.LITERTLM -> "Ez Gemma 4 (.litertlm) modell. Az AssiSten-s 1.0.4 még csak a .task / MediaPipe .bin " +
            "Gemmát tudja futtatni; a Gemma 4 támogatása a következő verzióban (1.1) jön."
        Format.UNKNOWN -> "Ez nem MediaPipe-formátumú Gemma-modell. Ide .task vagy MediaPipe .bin fájl kell " +
            "(pl. gemma3-1b-it-int4.task)."
    }

    /**
     * Az 1.0.3 az importált Gemmát az EREDETI fájlnéven mentette, ezért az összefoglaló nem találta.
     * Itt átnevezzük a rögzített névre. Visszaadja az eredeti fájlnevet, ha volt mit átnevezni.
     */
    fun migrateLegacyName(ctx: Context): String? {
        if (file(ctx) != null) return null
        val dir = modelsDir(ctx)
        val candidates = dir.listFiles()?.filter {
            it.isFile && it.length() > MIN_SIZE_BYTES &&
                !it.name.endsWith(".part") &&
                it.name != "whisper.bin" && it.name != TASK_NAME && it.name != BIN_NAME
        } ?: return null
        for (f in candidates) {
            val fmt = runCatching { detectFormat(readHeader(f), f.name) }.getOrDefault(Format.UNKNOWN)
            val target = when (fmt) {
                Format.TASK -> File(dir, TASK_NAME)
                Format.TFLITE -> File(dir, BIN_NAME)
                else -> null
            } ?: continue
            if (f.renameTo(target)) {
                Log.i("FuleloAI", "Gemma-modell átnevezve: ${f.name} → ${target.name}")
                return f.name
            }
        }
        return null
    }
}

class GemmaTimeoutException(msg: String) : RuntimeException(msg)

/**
 * Egy betöltött Gemma-példány. Egyszer töltődik be, és több generálásra is használható
 * (pl. blokkos összefoglalónál). A natív hívások egy saját szálon futnak, így időkorlát
 * alkalmazható rájuk; időtúllépés után a példány „elhagyott", és a lezárás csak a futó
 * generálás végén történik meg (nem zárjuk le alóla a natív erőforrást).
 */
class GemmaSession private constructor(
    private val llm: LlmInference,
    private val executor: java.util.concurrent.ExecutorService,
    val loadMillis: Long
) : Closeable {

    @Volatile private var abandoned = false

    fun generate(instruction: String, timeoutMs: Long = 120_000L): String {
        if (abandoned) throw GemmaTimeoutException("A Gemma egy korábbi időtúllépés miatt nem használható, próbáld újra.")
        val future = executor.submit(Callable { generateBlocking(instruction) })
        return try {
            future.get(timeoutMs, TimeUnit.MILLISECONDS)
        } catch (e: TimeoutException) {
            abandoned = true
            throw GemmaTimeoutException("A Gemma ${timeoutMs / 1000} mp alatt nem válaszolt (időtúllépés).")
        } catch (e: ExecutionException) {
            throw (e.cause as? Exception) ?: RuntimeException(e.cause)
        }
    }

    private fun generateBlocking(instruction: String): String {
        val sessionOptions = LlmInferenceSession.LlmInferenceSessionOptions.builder()
            .setTopK(40)
            .setTopP(0.95f)
            .setTemperature(0.3f)
            .build()
        val session = LlmInferenceSession.createFromOptions(llm, sessionOptions)
        return try {
            session.addQueryChunk("<start_of_turn>user\n$instruction<end_of_turn>\n<start_of_turn>model\n")
            session.generateResponse()
                .replace("<end_of_turn>", "").replace("<start_of_turn>", "").trim()
        } finally {
            runCatching { session.close() }
        }
    }

    override fun close() {
        // A sorba állított lezárás csak a (esetleg még futó) generálás után fut le.
        runCatching { executor.execute { runCatching { llm.close() } } }
        executor.shutdown()
    }

    companion object {
        /** Becsült tokenkeret (bemenet + válasz együtt), a MediaPipe setMaxTokens értéke. */
        const val MAX_TOKENS = 2048

        /** Durva tokenbecslés magyar szövegre: kb. 4 karakter / token. */
        fun estimateTokens(text: String): Int = (text.length + 3) / 4

        fun open(ctx: Context, file: File, timeoutMs: Long = 120_000L): GemmaSession {
            val executor = Executors.newSingleThreadExecutor { r -> Thread(r, "gemma").apply { isDaemon = true } }
            val started = System.currentTimeMillis()
            val future = executor.submit(Callable {
                val options = LlmInference.LlmInferenceOptions.builder()
                    .setModelPath(file.absolutePath)
                    .setMaxTokens(MAX_TOKENS)
                    .build()
                LlmInference.createFromOptions(ctx.applicationContext, options)
            })
            val llm = try {
                future.get(timeoutMs, TimeUnit.MILLISECONDS)
            } catch (e: TimeoutException) {
                executor.shutdown()
                throw GemmaTimeoutException("A Gemma-modell betöltése ${timeoutMs / 1000} mp alatt nem fejeződött be.")
            } catch (e: ExecutionException) {
                executor.shutdown()
                throw RuntimeException("A Gemma-modell nem tölthető be: ${e.cause?.message ?: e.message}", e.cause ?: e)
            }
            return GemmaSession(llm, executor, System.currentTimeMillis() - started)
        }
    }
}
