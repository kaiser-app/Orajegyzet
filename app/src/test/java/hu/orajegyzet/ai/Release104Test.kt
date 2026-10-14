package hu.orajegyzet.ai

import hu.orajegyzet.data.GanttTaskEntity
import hu.orajegyzet.data.ProjectEntity
import hu.orajegyzet.data.ProjectEventEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** Az 1.0.4 új, tiszta logikájának unit tesztjei (P1, P3, P4). */
class Release104Test {

    // ---------- P1 / 1.1: modellformátum felismerése (LiteRT-LM) ----------

    private fun bytes(vararg b: Int) = ByteArray(b.size) { b[it].toByte() }

    @Test fun litertlmHeaderIsAccepted() {
        val f = GemmaModel.detectFormat("LITERTLM".toByteArray(Charsets.ISO_8859_1), "gemma-4-E2B-it.litertlm")
        assertEquals(GemmaModel.Format.LITERTLM, f)
        assertNull(GemmaModel.rejectionMessage(f))
    }

    @Test fun litertlmHeaderWinsOverWrongFileName() {
        // pl. a régi verziók „gemma.task" néven mentették a Gemma 4 fájlt
        assertEquals(GemmaModel.Format.LITERTLM,
            GemmaModel.detectFormat("LITERTLM".toByteArray(Charsets.ISO_8859_1), "gemma.task"))
    }

    @Test fun mediapipeTaskIsRejectedWithLitertlmHint() {
        val f = GemmaModel.detectFormat(bytes(0x50, 0x4B, 0x03, 0x04, 0, 0, 0, 0))
        assertEquals(GemmaModel.Format.TASK, f)
        assertTrue(GemmaModel.rejectionMessage(f)!!.contains(".litertlm"))
    }

    @Test fun mediapipeTfliteIsRejected() {
        val f = GemmaModel.detectFormat(bytes(0x18, 0, 0, 0, 'T'.code, 'F'.code, 'L'.code, '3'.code))
        assertEquals(GemmaModel.Format.TFLITE, f)
        assertNotNull(GemmaModel.rejectionMessage(f))
    }

    @Test fun whisperGgmlIsRejected() {
        val f = GemmaModel.detectFormat("lmggxxxx".toByteArray(Charsets.ISO_8859_1))
        assertEquals(GemmaModel.Format.GGML, f)
        assertTrue(GemmaModel.rejectionMessage(f)!!.contains("Whisper"))
    }

    @Test fun truncatedLitertlmGetsDownloadHint() {
        val f = GemmaModel.detectFormat(bytes(0, 0, 0, 0, 0, 0, 0, 0), "gemma-4-E2B-it.litertlm")
        assertEquals(GemmaModel.Format.UNKNOWN, f)
        assertTrue(GemmaModel.rejectionMessage(f, "gemma-4-E2B-it.litertlm")!!.contains("letöltés"))
    }

    @Test fun unknownIsRejected() {
        val f = GemmaModel.detectFormat(bytes(1, 2, 3, 4, 5, 6, 7, 8), "valami.bin")
        assertEquals(GemmaModel.Format.UNKNOWN, f)
        assertNotNull(GemmaModel.rejectionMessage(f, "valami.bin"))
    }

    // ---------- P4: blokkolás és tokenbecslés ----------

    @Test fun splitBlocksRespectsLimitAndKeepsAllText() {
        val sentence = "Ez egy tesztmondat a blokkolás ellenőrzéséhez."
        val text = List(200) { sentence }.joinToString(" ")
        val blocks = OfflinePipeline.splitBlocks(text, 1500)
        assertTrue(blocks.size > 1)
        assertTrue(blocks.all { it.length <= 1500 })
        assertEquals(text.replace(" ", ""), blocks.joinToString("").replace(" ", ""))
    }

    @Test fun splitBlocksCutsOverlongSentence() {
        val longSentence = "a".repeat(5000)
        val blocks = OfflinePipeline.splitBlocks(longSentence, 2000)
        assertEquals(3, blocks.size)
        assertTrue(blocks.all { it.length <= 2000 })
    }

    @Test fun tokenEstimateIsAboutFourCharsPerToken() {
        assertEquals(250, GemmaSession.estimateTokens("x".repeat(1000)))
        assertEquals(4000, GemmaSession.MAX_TOKENS)
        assertTrue(GemmaSession.estimateTokens("x".repeat(16000)) + 500 > GemmaSession.MAX_TOKENS)
    }

    // ---------- P3: tényblokk és sablonváz ----------

    private val today = LocalDate.of(2026, 10, 14)
    private val project = ProjectEntity(id = "p1", code = "PRJ-01", name = "Teszt projekt", ragStatus = "amber",
        budget = "10 M Ft", spent = "4 M Ft")
    private val events = listOf(
        ProjectEventEntity(projectId = "p1", date = "2026-10-01", type = "decision", title = "Új beszállító kiválasztása"),
        ProjectEventEntity(projectId = "p1", date = "2026-10-05", type = "risk", title = "Csúszik a licencbeszerzés", impact = "high"),
        ProjectEventEntity(projectId = "p1", date = "2026-10-06", type = "risk", title = "Lezárt kockázat", status = "resolved")
    )
    private val tasks = listOf(
        GanttTaskEntity(projectId = "p1", title = "Lejárt feladat", dueDate = "2026-10-10", assignee = "Kovács Anna"),
        GanttTaskEntity(projectId = "p1", title = "Kész feladat", dueDate = "2026-10-01", progress = 100, status = "completed"),
        GanttTaskEntity(projectId = "p1", title = "Jövő heti feladat", dueDate = "2026-10-20", progress = 30, status = "in_progress"),
        GanttTaskEntity(projectId = "p1", title = "Távoli feladat", dueDate = "2026-12-31")
    )

    @Test fun overdueAndDueSoon() {
        assertEquals(listOf("Lejárt feladat"), ReportBuilder.overdueTasks(tasks, today).map { it.title })
        assertEquals(listOf("Jövő heti feladat"), ReportBuilder.dueSoonTasks(tasks, today, 14).map { it.title })
    }

    @Test fun factBlockContainsFactsAndKeepsRag() {
        val f = ReportBuilder.factBlock(ReportBuilder.STATUS, project, events, tasks, today)
        assertTrue(f.contains("PRJ-01"))
        assertTrue(f.contains("SÁRGA"))
        assertTrue(f.contains("lejárt feladat: 1"))
        assertTrue(f.contains("nyitott kockázat: 1"))
        assertTrue(f.contains("4 M Ft / 10 M Ft"))
    }

    @Test fun shortenedFactBlockKeepsOnlyOverdueAndSoon() {
        val f = ReportBuilder.factBlock(ReportBuilder.EXECUTIVE, project, events, tasks, today, shortenTasks = true)
        assertTrue(f.contains("Lejárt feladat"))
        assertTrue(f.contains("Jövő heti feladat"))
        assertFalse(f.contains("Távoli feladat"))
    }

    @Test fun templateNeverContainsPromptText() {
        for (type in listOf(ReportBuilder.EXECUTIVE, ReportBuilder.STATUS, ReportBuilder.QA)) {
            val t = ReportBuilder.templateDraft(type, project, events, tasks, today)
            assertFalse(type, t.contains("Készíts"))
            assertFalse(type, t.contains("Végezz"))
            assertFalse(type, t.contains("Kizárólag a megadott"))
            assertTrue(type, t.contains("Offline vázlat"))
            assertTrue(type, t.contains("SÁRGA"))
        }
    }

    @Test fun executiveTemplateListsDecisionRiskAndOverdue() {
        val t = ReportBuilder.templateDraft(ReportBuilder.EXECUTIVE, project, events, tasks, today)
        assertTrue(t.contains("Új beszállító kiválasztása"))
        assertTrue(t.contains("Csúszik a licencbeszerzés"))
        assertFalse(t.contains("Lezárt kockázat"))
        assertTrue(t.contains("Lejárt feladat – Kovács Anna"))
        assertTrue(t.contains("Következő határidő:** Jövő heti feladat – 2026-10-20"))
    }

    @Test fun promptContainsRulesAndFacts() {
        val facts = ReportBuilder.factBlock(ReportBuilder.EXECUTIVE, project, events, tasks, today)
        val p = ReportBuilder.prompt(ReportBuilder.EXECUTIVE, facts)
        assertTrue(p.contains("Kizárólag a megadott tényeket"))
        assertTrue(p.contains("PRJ-01"))
    }
}

/** 1.1.1: a stílusos összefoglalók ténylegesen különböznek, és a szerkezet szavai nem „hallucinációk”. */
class SummaryStylesTest {

    @Test fun everyStyleHasItsOwnStructure() {
        val all = SummaryStyles.Style.values().map { SummaryStyles.instruction(it) }
        assertEquals(all.size, all.toSet().size)
        assertTrue(SummaryStyles.instruction(SummaryStyles.Style.EXECUTIVE).contains("**Lényeg:**"))
        assertTrue(SummaryStyles.instruction(SummaryStyles.Style.MINUTES).contains("**Határozatok:**"))
        assertTrue(SummaryStyles.instruction(SummaryStyles.Style.ACTIONS).contains("| Feladat | Felelős | Határidő |"))
        assertTrue(SummaryStyles.instruction(SummaryStyles.Style.DETAILED).contains("**Kulcsfogalmak:**"))
    }

    @Test fun labelsMapToStyles() {
        assertEquals(SummaryStyles.Style.EXECUTIVE, SummaryStyles.fromLabel("Vezetői"))
        assertEquals(SummaryStyles.Style.MINUTES, SummaryStyles.fromLabel("Jegyzőkönyv"))
        assertEquals(SummaryStyles.Style.ACTIONS, SummaryStyles.fromLabel("Teendők"))
        assertEquals(SummaryStyles.Style.DETAILED, SummaryStyles.fromLabel("Részletes jegyzet"))
    }

    @Test fun templateWordsDoNotTriggerHallucinationFilter() {
        val source = "Kovács Anna vállalta a költségvetés elkészítését péntekig. A tesztelést Péter végzi a jövő héten."
        val minutes = "**Tárgy:** költségvetés\n**Résztvevők:** nem rögzített\n**Napirendi pontok:** 1. költségvetés\n" +
            "**Határozatok:** nem született határozat\n**Felelősök és határidők:** költségvetés – Kovács Anna – péntek"
        val instr = SummaryStyles.instruction(SummaryStyles.Style.MINUTES)
        assertFalse(OfflinePipeline.verifySourceCoherence(minutes, source))
        assertTrue(OfflinePipeline.verifySourceCoherence(minutes, source, allowedText = instr))
    }
}
