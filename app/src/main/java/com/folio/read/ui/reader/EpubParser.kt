package com.folio.read.ui.reader

/*
 * .epub 解析:ZipFile 读 META-INF/container.xml → OPF(manifest+spine),OPF/NCX 用 DOM 解析(不引 epublib)。
 * 章节生成的标题**优先用 NCX 目录(toc.ncx 的 <navLabel><text>)**——epub 每个分章 xhtml 的 <title>
 * 常是整本书名(如「罗杰疑案」),拿它当章标题会每章同名(用户反馈);legado 也是用 NCX 标题。
 * NCX 不可用时退 spine(标题取 xhtml <title> 否则「第N章」)。正文=对应资源 xhtml→Jsoup 解析→HtmlToText 纯文本。
 */

import android.content.Context
import android.net.Uri
import org.w3c.dom.Document
import org.w3c.dom.Element
import java.io.File
import java.util.zip.ZipFile
import javax.xml.parsers.DocumentBuilderFactory

object EpubParser {

    private data class NcxItem(val title: String, val href: String)

    fun parse(context: Context, filePath: String): List<Chapter> {
        val temp = File.createTempFile("folio_epub_", ".epub", context.cacheDir)
        try {
            context.contentResolver.openInputStream(Uri.parse(filePath)).use { ins ->
                requireNotNull(ins) { "无法打开 epub 文件" }
                temp.outputStream().use { out -> ins.copyTo(out) }
            }
            ZipFile(temp).use { zip ->
                val opfPath = readContainer(zip)
                val opfDoc = readXml(zip, opfPath)
                val manifest = parseManifest(opfDoc)
                val spineHrefs = parseSpine(opfDoc, manifest, opfPath)
                val chapters = mutableListOf<Chapter>()

                // 优先用 NCX 目录生成章节(标题=navLabel 真实章标题),标题独立、正文剥离重复标题段
                val ncxItems = findNcx(zip)?.let { readNcx(zip, it) } ?: emptyList()
                if (ncxItems.isNotEmpty()) {
                    var no = 0
                    ncxItems.forEach { item ->
                        val entry = zip.getEntry(item.href) ?: return@forEach
                        val text = HtmlToText.convert(
                            zip.getInputStream(entry).reader(Charsets.UTF_8).readText(),
                        ).trim()
                        if (text.isEmpty()) return@forEach
                        val title = item.title.ifBlank { "第${++no}章" }
                        chapters.add(Chapter(title, indentContent(stripLeadingTitle(text, title))))
                    }
                    return chapters
                }

                // spine 退:每资源一章(标题取 xhtml <title>,否则「第N章」)
                var chapterNo = 0
                for (href in spineHrefs) {
                    val entry = zip.getEntry(href) ?: continue
                    val html = zip.getInputStream(entry).reader(Charsets.UTF_8).readText()
                    val title = extractTitle(html) ?: "第${++chapterNo}章"
                    val text = HtmlToText.convert(html).trim()
                    if (text.isEmpty()) continue
                    chapters.add(Chapter(title, indentContent(stripLeadingTitle(text, title))))
                }
                return chapters
            }
        } finally {
            temp.delete()
        }
    }

    private fun readContainer(zip: ZipFile): String {
        val entry = zip.getEntry("META-INF/container.xml")
            ?: zip.getEntry("mimetype")?.let { entry ->
                zip.entries().asSequence().firstOrNull { it.name.endsWith("container.xml") }?.let {
                    return readXml(zip, it.name).let { doc -> opfPath(doc) }
                }
                entry
            } ?: throw IllegalStateException("epub 缺少 container.xml")
        return opfPath(readXml(zip, entry.name))
    }

    private fun opfPath(containerDoc: Document): String {
        val rootfile = containerDoc.getElementsByTagName("rootfile").item(0) as? Element
            ?: throw IllegalStateException("epub container 无 rootfile")
        return rootfile.getAttribute("full-path")
    }

    private fun readXml(zip: ZipFile, path: String): Document {
        val input = zip.getInputStream(zip.getEntry(path))
            ?: throw IllegalStateException("epub 缺少 $path")
        val factory = DocumentBuilderFactory.newInstance()
        factory.isNamespaceAware = true
        return input.use { factory.newDocumentBuilder().parse(it) }
    }

    private fun parseManifest(opf: Document): Map<String, Pair<String, String>> {
        val map = mutableMapOf<String, Pair<String, String>>()
        val items = opf.getElementsByTagName("item")
        for (i in 0 until items.length) {
            val item = items.item(i) as Element
            val id = item.getAttribute("id")
            val href = item.getAttribute("href")
            val type = item.getAttribute("media-type")
            if (id.isNotEmpty()) {
                map[id] = href to type
            }
        }
        return map
    }

    private fun parseSpine(opf: Document, manifest: Map<String, Pair<String, String>>, opfPath: String): List<String> {
        val hrefs = mutableListOf<String>()
        val opfDir = opfPath.substringBeforeLast('/', "")
        val itemrefs = opf.getElementsByTagName("itemref")
        for (i in 0 until itemrefs.length) {
            val itemref = itemrefs.item(i) as Element
            val idref = itemref.getAttribute("idref")
            val info = manifest[idref] ?: continue
            val (href, mediaType) = info
            if (!mediaType.contains("html") && !href.endsWith(".xhtml", true) && !href.endsWith(".html", true)) continue
            val full = if (opfDir.isEmpty()) href else "$opfDir/$href"
            hrefs.add(normalizePath(full))
        }
        return hrefs
    }

    /** 在 zip 里找 .ncx 文件(zip 条目名) */
    private fun findNcx(zip: ZipFile): String? =
        zip.entries().asSequence().firstOrNull { it.name.endsWith(".ncx", true) }?.name

    /** OPF <spine toc="ncx_id"> → manifest 里 ncx_id 的 href(相对 OPF) */
    private fun parseNcxHref(opf: Document, manifest: Map<String, Pair<String, String>>, opfPath: String): String? {
        val spine = opf.getElementsByTagName("spine").item(0) as? Element ?: return null
        val tocId = spine.getAttribute("toc").ifBlank { return null }
        val href = manifest[tocId]?.first ?: return null
        val opfDir = opfPath.substringBeforeLast('/', "")
        return normalizePath(if (opfDir.isEmpty()) href else "$opfDir/$href")
    }

    /** NCX navPoint → (title = navLabel text, href 相对 NCX 目录) */
    private fun readNcx(zip: ZipFile, ncxPath: String): List<NcxItem> {
        val input = zip.getInputStream(zip.getEntry(ncxPath)) ?: return emptyList()
        val content = input.reader(Charsets.UTF_8).readText()
        val ncxDir = ncxPath.substringBeforeLast('/', "")
        val items = mutableListOf<NcxItem>()
        val navPointRegex =
            Regex("""(?is)<navPoint.*?<navLabel>.*?<text>(.*?)</text>.*?<content[^>]*src=["']([^"']+)["']""")
        navPointRegex.findAll(content).forEach { m ->
            val title = m.groupValues[1].replace(Regex("<[^>]+>"), "").trim()
            val raw = m.groupValues[2].substringBefore("#")
            val href = normalizePath(if (ncxDir.isEmpty()) raw else "$ncxDir/$raw")
            items.add(NcxItem(title, href))
        }
        return items
    }

    private fun normalizePath(path: String): String {
        val parts = mutableListOf<String>()
        path.split('/').forEach { seg ->
            when (seg) {
                "", "." -> {}
                ".." -> if (parts.isNotEmpty()) parts.removeAt(parts.size - 1)
                else -> parts.add(seg)
            }
        }
        return parts.joinToString("/")
    }

    private fun extractTitle(html: String): String? {
        val m = Regex("(?is)<title>\\s*(.*?)\\s*</title>").find(html) ?: return null
        return m.groupValues[1].ifBlank { null }
    }
}
