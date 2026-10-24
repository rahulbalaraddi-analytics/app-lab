package com.applab.docsnap.scan

import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory
import java.io.File

/**
 * Joins page JPEGs into one PDF.
 * JPEGFactory.createFromStream embeds the original JPEG bytes as-is (DCTDecode),
 * so the photo inside the PDF is identical to the camera photo: no quality loss.
 */
object PdfMaker {

    private const val DPI = 300f

    fun make(pages: List<File>, out: File) {
        require(pages.isNotEmpty()) { "No pages" }
        out.parentFile?.mkdirs()
        val tmp = File(out.parentFile, out.name + ".part")
        PDDocument().use { doc ->
            for (jpg in pages) {
                val image = jpg.inputStream().use { JPEGFactory.createFromStream(doc, it) }
                // Page size from pixels at 300 DPI, so a full A4 photo maps to an A4 page.
                val w = image.width * 72f / DPI
                val h = image.height * 72f / DPI
                val page = PDPage(PDRectangle(w, h))
                doc.addPage(page)
                PDPageContentStream(doc, page).use { cs -> cs.drawImage(image, 0f, 0f, w, h) }
            }
            doc.save(tmp)
        }
        // Write-then-rename: a crash never leaves a half-written PDF under the real name.
        if (out.exists()) out.delete()
        check(tmp.renameTo(out)) { "Could not save PDF" }
    }
}
