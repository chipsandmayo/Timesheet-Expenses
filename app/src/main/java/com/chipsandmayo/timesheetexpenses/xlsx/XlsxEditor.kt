package com.chipsandmayo.timesheetexpenses.xlsx

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Minimal .xlsx editor that edits the sheet XML in place, so all of the
 * template's formatting, logos, merged cells and print setup are preserved.
 */
class XlsxEditor(templateBytes: ByteArray) {
    private val order = mutableListOf<String>()
    private val parts = mutableMapOf<String, ByteArray>()
    private val text = mutableMapOf<String, String>()

    init {
        ZipInputStream(ByteArrayInputStream(templateBytes)).use { zin ->
            var e: ZipEntry? = zin.nextEntry
            while (e != null) {
                if (!e.isDirectory) {
                    order += e.name
                    parts[e.name] = zin.readBytes()
                }
                e = zin.nextEntry
            }
        }
    }

    private fun xml(path: String): String = text.getOrPut(path) {
        String(parts[path] ?: error("Missing part $path"), Charsets.UTF_8)
    }

    private fun putXml(path: String, value: String) {
        if (path !in order) order += path
        text[path] = value
    }

    fun setString(sheet: String, ref: String, value: String, style: Int? = null) {
        if (value.isEmpty()) { clear(sheet, ref, style); return }
        val space = if (value != value.trim() || value.contains('\n')) " xml:space=\"preserve\"" else ""
        writeCell(sheet, ref, style, " t=\"inlineStr\"", "<is><t$space>${esc(value)}</t></is>")
    }

    fun setNumber(sheet: String, ref: String, value: Double, style: Int? = null) =
        writeCell(sheet, ref, style, "", "<v>${num(value)}</v>")

    fun setFormula(sheet: String, ref: String, formula: String, cached: Double, style: Int? = null) =
        writeCell(sheet, ref, style, "", "<f>${esc(formula)}</f><v>${num(cached)}</v>")

    fun clear(sheet: String, ref: String, style: Int? = null) = writeCell(sheet, ref, style, "", null)

    /**
     * Clones cell style [baseStyle] with a custom number format and returns the
     * new style index (e.g. to show exchange rates with 4 decimals).
     */
    fun styleWithFormat(baseStyle: Int, formatCode: String): Int {
        val path = "xl/styles.xml"
        var s = xml(path)
        val ids = Regex("numFmtId=\"(\\d+)\"").findAll(s).map { it.groupValues[1].toInt() }.toList()
        val fmtId = maxOf(200, (ids.maxOrNull() ?: 0) + 1)
        val fmt = "<numFmt numFmtId=\"$fmtId\" formatCode=\"${esc(formatCode)}\"/>"
        s = if (s.contains("<numFmts")) {
            s.replace(Regex("<numFmts count=\"(\\d+)\">")) { m ->
                "<numFmts count=\"${m.groupValues[1].toInt() + 1}\">"
            }.replace("</numFmts>", "$fmt</numFmts>")
        } else s.replaceFirst(Regex("(<styleSheet[^>]*>)"), "$1<numFmts count=\"1\">$fmt</numFmts>")
        val xfsMatch = Regex("<cellXfs count=\"(\\d+)\">(.*?)</cellXfs>", RegexOption.DOT_MATCHES_ALL).find(s)
            ?: error("No cellXfs")
        val body = xfsMatch.groupValues[2]
        val xfs = Regex("<xf [^>]*?(/>|>.*?</xf>)", RegexOption.DOT_MATCHES_ALL).findAll(body).map { it.value }.toList()
        val base = xfs[baseStyle]
        var clone = base.replace(Regex("numFmtId=\"\\d+\""), "numFmtId=\"$fmtId\"")
        if (!clone.contains("applyNumberFormat")) clone = clone.replaceFirst("<xf ", "<xf applyNumberFormat=\"1\" ")
        val newIndex = xfs.size
        val replaced = "<cellXfs count=\"${newIndex + 1}\">$body$clone</cellXfs>"
        s = s.substring(0, xfsMatch.range.first) + replaced + s.substring(xfsMatch.range.last + 1)
        putXml(path, s)
        return newIndex
    }

    /** Forces Excel to recalculate formulas when the file is opened. */
    fun forceRecalc() {
        val wb = "xl/workbook.xml"
        var s = xml(wb)
        s = if (Regex("<calcPr[^>]*fullCalcOnLoad").containsMatchIn(s)) s
        else if (s.contains("<calcPr")) s.replaceFirst("<calcPr", "<calcPr fullCalcOnLoad=\"1\"")
        else s.replaceFirst("</workbook>", "<calcPr fullCalcOnLoad=\"1\"/></workbook>")
        putXml(wb, s)
    }

    /**
     * Places a PNG on the sheet's existing drawing, anchored at the 0-based
     * [col]/[row] with the given size in EMUs.
     */
    fun addPicture(drawing: String, png: ByteArray, col: Int, row: Int, colOff: Long, rowOff: Long, cx: Long, cy: Long, name: String) {
        val media = "xl/media/${name.lowercase()}.png"
        parts[media] = png
        if (media !in order) order += media
        val relsPath = drawing.substringBeforeLast('/') + "/_rels/" + drawing.substringAfterLast('/') + ".rels"
        val relId = "rId$name"
        var rels = xml(relsPath)
        rels = rels.replace("</Relationships>",
            "<Relationship Id=\"$relId\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/image\" Target=\"/$media\"/></Relationships>")
        putXml(relsPath, rels)
        var d = xml(drawing)
        val anchor = "<oneCellAnchor><from><col>$col</col><colOff>$colOff</colOff><row>$row</row><rowOff>$rowOff</rowOff></from>" +
            "<ext cx=\"$cx\" cy=\"$cy\"/><pic><nvPicPr><cNvPr id=\"${900 + name.length}\" name=\"$name\"/>" +
            "<cNvPicPr><a:picLocks noChangeAspect=\"1\"/></cNvPicPr></nvPicPr><blipFill><a:blip r:embed=\"$relId\"/>" +
            "<a:stretch><a:fillRect/></a:stretch></blipFill><spPr><a:xfrm><a:off x=\"0\" y=\"0\"/><a:ext cx=\"$cx\" cy=\"$cy\"/></a:xfrm>" +
            "<a:prstGeom prst=\"rect\"><a:avLst/></a:prstGeom></spPr></pic><clientData/></oneCellAnchor>"
        d = d.replace(Regex("</(\\w+:)?wsDr>")) { m -> anchor + m.value }
        putXml(drawing, d)
        // make sure png has a content type
        val ct = "[Content_Types].xml"
        val types = xml(ct)
        if (!types.contains("Extension=\"png\"")) {
            putXml(ct, types.replace("</Types>", "<Default Extension=\"png\" ContentType=\"image/png\"/></Types>"))
        }
    }

    fun toByteArray(): ByteArray {
        val bos = ByteArrayOutputStream()
        ZipOutputStream(bos).use { zout ->
            for (name in order) {
                zout.putNextEntry(ZipEntry(name))
                zout.write(text[name]?.toByteArray(Charsets.UTF_8) ?: parts[name]!!)
                zout.closeEntry()
            }
        }
        return bos.toByteArray()
    }

    // ---- internals ----

    private fun writeCell(sheet: String, ref: String, style: Int?, typeAttr: String, inner: String?) {
        var s = xml(sheet)
        val cellRe = Regex("<c r=\"$ref\"(?=[\\s/>])([^>]*?)(/>|>(.*?)</c>)", RegexOption.DOT_MATCHES_ALL)
        val m = cellRe.find(s)
        val existingStyle = m?.groupValues?.get(1)?.let { Regex("\\ss=\"(\\d+)\"").find(it)?.groupValues?.get(1) }
        val st = style?.toString() ?: existingStyle
        val styleAttr = if (st != null) " s=\"$st\"" else ""
        val cell = if (inner == null) "<c r=\"$ref\"$styleAttr/>" else "<c r=\"$ref\"$styleAttr$typeAttr>$inner</c>"
        s = if (m != null) {
            s.substring(0, m.range.first) + cell + s.substring(m.range.last + 1)
        } else insertCell(s, ref, cell)
        putXml(sheet, s)
    }

    private fun insertCell(s: String, ref: String, cell: String): String {
        val (col, row) = splitRef(ref)
        val rowRe = Regex("<row r=\"$row\"(?=[\\s/>])([^>]*?)(/>|>(.*?)</row>)", RegexOption.DOT_MATCHES_ALL)
        val rm = rowRe.find(s)
        if (rm == null) {
            // insert a new row in order
            val newRow = "<row r=\"$row\">$cell</row>"
            val rows = Regex("<row r=\"(\\d+)\"").findAll(s).toList()
            val after = rows.firstOrNull { it.groupValues[1].toInt() > row }
            return if (after != null) s.substring(0, after.range.first) + newRow + s.substring(after.range.first)
            else s.replace(Regex("<sheetData\\s*/>"), "<sheetData></sheetData>").replace("</sheetData>", "$newRow</sheetData>")
        }
        val open = "<row r=\"$row\"${rm.groupValues[1]}>"
        val body = if (rm.groupValues[2] == "/>") "" else rm.groupValues[3]
        val cells = Regex("<c r=\"([A-Z]+)\\d+\"").findAll(body).toList()
        val target = colIndex(col)
        val next = cells.firstOrNull { colIndex(it.groupValues[1]) > target }
        val newBody = if (next != null) body.substring(0, next.range.first) + cell + body.substring(next.range.first) else body + cell
        return s.substring(0, rm.range.first) + open + newBody + "</row>" + s.substring(rm.range.last + 1)
    }

    companion object {
        fun esc(v: String) = v.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
            .filter { it == '\n' || it == '\t' || it >= ' ' }

        fun num(v: Double): String =
            if (v == Math.floor(v) && !v.isInfinite() && Math.abs(v) < 1e15) v.toLong().toString() else v.toString()

        fun colIndex(col: String): Int = col.fold(0) { acc, c -> acc * 26 + (c - 'A' + 1) }

        fun colName(index: Int): String {
            var i = index; val sb = StringBuilder()
            while (i > 0) { val r = (i - 1) % 26; sb.insert(0, ('A' + r)); i = (i - 1) / 26 }
            return sb.toString()
        }

        fun splitRef(ref: String): Pair<String, Int> {
            val m = Regex("([A-Z]+)(\\d+)").matchEntire(ref) ?: error("Bad ref $ref")
            return m.groupValues[1] to m.groupValues[2].toInt()
        }
    }
}
