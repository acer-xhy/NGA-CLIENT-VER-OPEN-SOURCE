package com.client.androidnga.core.parse

import com.alibaba.fastjson2.JSON
import com.alibaba.fastjson2.JSONObject
import com.client.androidnga.core.data.bean.ThreadBean
import com.client.androidnga.core.data.bean.ThreadPageBean
import com.client.androidnga.core.data.bean.ThreadPostBean
import com.client.androidnga.core.data.bean.ThreadUserBean
import com.client.androidnga.core.data.model.ThreadInfo
import gov.anzong.androidnga.core.IHtmlConfigService

/**
 * 服务端 __output=8 的 JSON 直出会间歇性被截断（同一帖、同一身份稳定卡在同一位置），
 * 但同一时刻 read.php 的网页（HTML）是完整的。
 *
 * 这里把网页数据还原成与 JSON 输出完全一致的数据结构（[ThreadBean]），
 * 再交给 [ThreadInfoParse.parseThreadInfo] 走原有的原生解析 / 渲染流程，
 * 不截断、不补齐、不丢楼层，用户看到的仍是原生帖子界面。
 */
class ThreadInfoHtmlParse {

    companion object {

        private const val DEFAULT_ATTACH_BASE_VIEW = "img.nga.cn/attachments"

        /**
         * 每楼在网页里的起始标记：<tr id='post1strow0' ...>
         */
        private const val ROW_MARK = "id='post1strow"

        private const val DEFAULT_PAGE_SIZE = 20

        @JvmStatic
        @JvmOverloads
        fun parse(
            html: String,
            config: IHtmlConfigService?,
            page: Int = 1,
            tid: Int = 0
        ): ThreadInfo? {
            return ThreadInfoHtmlParse().parseHtml(html, config, page, tid)
        }
    }

    fun parseHtml(html: String, config: IHtmlConfigService?, page: Int, tid: Int): ThreadInfo? {
        if (html.isEmpty() || !html.contains(ROW_MARK)) {
            // 不是帖子页（错误页 / 登录页 / 权限不足），交给原有失败逻辑
            return null
        }
        val rowSegments = splitRows(html)
        if (rowSegments.isEmpty()) {
            return null
        }

        val meta = parsePageMeta(html)
        val currentPage = if (meta.currentPage > 0) meta.currentPage else maxOf(page, 1)
        val pageSize = if (meta.pageSize > 0) meta.pageSize else DEFAULT_PAGE_SIZE
        val realTid = if (meta.tid > 0) meta.tid else tid

        val userMap = parseUsers(html)
        val subject = parseSubject(html)
        val posts = parsePosts(rowSegments, meta.fid, realTid, currentPage, pageSize, userMap)
        if (posts.isEmpty()) {
            return null
        }

        val threadBean = ThreadBean(
            __GLOBAL = buildGlobal(html),
            __T = ThreadPageBean(
                author = null,
                authorid = meta.authorid,
                digest = 0,
                fid = meta.fid,
                jdata = null,
                lastmodify = 0,
                lastpost = 0,
                lastposter = null,
                locked = 0,
                post_misc_var = null,
                postdate = 0,
                quote_from = 0,
                quote_to = null,
                recommend = 0,
                replies = meta.replies,
                subject = subject,
                this_visit_rows = posts.size,
                tid = realTid,
                topic_misc = null,
                tpid = 0,
                type = meta.type
            ),
            __U = userMap,
            __R = posts,
            __R__ROWS = posts.size,
            __R__ROWS_PAGE = posts.size,
            __ROWS = posts.size,
            __PAGE = currentPage
        )

        val threadInfo = ThreadInfoParse().parseThreadInfo(html, threadBean, config) ?: return null
        // 网页只给出当前页的楼层；总楼层数优先取 postArg.setDefault 的 replies（与 JSON 的 __ROWS 完全一致），
        // 取不到时按 __PAGE 里的总页数估算，保证分页不会少。
        threadInfo.totalRows = when {
            meta.replies > 0 -> meta.replies + 1
            meta.totalPages > 0 && currentPage >= meta.totalPages ->
                (meta.totalPages - 1) * meta.pageSize + posts.size
            meta.totalPages > 0 -> meta.totalPages * meta.pageSize
            else -> posts.size
        }
        return threadInfo
    }

    // ------------------------------------------------------------------
    // 楼层
    // ------------------------------------------------------------------

    private fun splitRows(html: String): List<String> {
        val starts = mutableListOf<Int>()
        var index = html.indexOf(ROW_MARK)
        while (index >= 0) {
            starts.add(index)
            index = html.indexOf(ROW_MARK, index + ROW_MARK.length)
        }
        if (starts.isEmpty()) {
            return emptyList()
        }
        val segments = mutableListOf<String>()
        for (i in starts.indices) {
            val end = if (i + 1 < starts.size) starts[i + 1] else minOf(html.length, starts[i] + 40000)
            segments.add(html.substring(starts[i], end))
        }
        return segments
    }

    private fun parsePosts(
        segments: List<String>,
        fid: Int,
        tid: Int,
        currentPage: Int,
        pageSize: Int,
        userMap: MutableMap<String, String>
    ): Map<String, ThreadPostBean> {
        val posts = LinkedHashMap<String, ThreadPostBean>()
        segments.forEachIndexed { index, segment ->
            val rowPart = segment.substringBefore("</tr>")
            // 即使取不到作者（楼层被屏蔽 / 结构异常）也要保留这一楼，不能丢数据
            val authorId = firstGroup(rowPart, "uid=(\\d+)' id='postauthor")?.toIntOrNull() ?: 0
            val pid = firstGroup(rowPart, "id='pid(\\d+)Anchor'")?.toIntOrNull() ?: 0
            val anchorLou = firstGroup(rowPart, "name='l(\\d+)'")?.toIntOrNull()
            val postDate = firstGroup(rowPart, "id='postdate\\d+'[^>]*>([^<]*)<")?.let { unescape(it).trim() }
            val subject = firstGroup(rowPart, "<h3 id='postsubject\\d+'>([\\s\\S]*?)</h3>")
                ?.let { normalize(it) }
                ?.trim()
                ?: ""
            val content = parseContent(rowPart)
            val fromClient = firstGroup(segment, "postArg\\.proc[\\s\\S]*?'(\\d+ [^'\\n]{0,24})'")

            val postBean = ThreadPostBean().apply {
                this.tid = tid
                this.fid = fid
                this.authorid = authorId
                this.pid = pid
                this.subject = subject
                this.content = content
                this.postdate = postDate
                this.from_client = fromClient
                this.lou = resolveLou(anchorLou, currentPage, pageSize, index)
                this.level = null
                this.alterinfo = null
                this.vote = null
            }
            posts[posts.size.toString()] = postBean
            ensureUser(userMap, authorId)
        }
        return posts
    }

    private fun parseContent(rowPart: String): String {
        val raw = firstGroup(rowPart, "<p id='postcontent\\d+'[^>]*>([\\s\\S]*?)</p>")
            ?: firstGroup(rowPart, "<span id='postcontent\\d+'[^>]*>([\\s\\S]*?)</span>")
            ?: ""
        return normalize(raw)
    }

    /**
     * 网页正文是与 JSON 完全相同的 UBB 文本，只是用 <br/> 表示换行、并对少量字符做了 HTML 转义。
     */
    private fun normalize(raw: String): String {
        var content = raw.replace(Regex("(?i)<br\\s*/?>"), "\n")
        content = unescape(content)
        while (content.endsWith("\n")) {
            content = content.substring(0, content.length - 1)
        }
        return content
    }

    private fun unescape(raw: String): String {
        if (!raw.contains('&')) {
            return raw
        }
        var text = raw
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&apos;", "'")
            .replace("&#39;", "'")
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")
        text = Regex("&#x([0-9a-fA-F]+);").replace(text) { match ->
            val code = match.groupValues[1].toIntOrNull(16)
            if (code != null) String(Character.toChars(code)) else match.value
        }
        text = Regex("&#(\\d+);").replace(text) { match ->
            val code = match.groupValues[1].toIntOrNull()
            if (code != null) String(Character.toChars(code)) else match.value
        }
        return text
    }

    /**
     * 楼层号：网页里的 <a name='lN'> 是页内序号，除首页外需要按当前页换算成全局楼层号。
     * 若服务端直接给出全局楼层号（大于等于换算值），则以服务端为准。
     */
    private fun resolveLou(anchor: Int?, currentPage: Int, pageSize: Int, index: Int): Int {
        val base = (maxOf(currentPage, 1) - 1) * pageSize
        val computed = base + index
        return if (anchor == null || anchor >= computed) anchor ?: computed else computed
    }

    // ------------------------------------------------------------------
    // 用户信息（commonui.userInfo.setAll 里的 JSON 与 JSON 输出的 __U 结构一致）
    // ------------------------------------------------------------------

    private fun parseUsers(html: String): MutableMap<String, String> {
        val userMap = LinkedHashMap<String, String>()
        val json = extractJsonObject(html, "commonui.userInfo.setAll(") ?: return userMap
        val jsonObject = try {
            JSON.parseObject(json)
        } catch (e: Throwable) {
            null
        } ?: return userMap

        for ((key, value) in jsonObject) {
            if (value == null || (value is JSONObject && value.isEmpty())) {
                continue
            }
            if (value is JSONObject) {
                // fastjson 会把值为 null 的字段写进 Kotlin 的非空字段，导致解析用户信息时崩溃，
                // 这里去掉 null 字段，让 ThreadUserBean 使用默认值。
                val clean = JSONObject()
                for ((k, v) in value) {
                    if (v != null) {
                        clean[k] = v
                    }
                }
                userMap[key] = clean.toJSONString()
            } else {
                userMap[key] = JSON.toJSONString(value)
            }
        }

        val groups = try {
            JSON.parseObject(userMap["__GROUPS"] ?: "{}")
        } catch (e: Throwable) {
            null
        }
        if (groups != null) {
            var changed = false
            for ((uid, userJson) in userMap) {
                if (uid.startsWith("__")) {
                    continue
                }
                val memberId = try {
                    JSON.parseObject(userJson, ThreadUserBean::class.java).memberid
                } catch (e: Throwable) {
                    null
                }
                if (memberId == null) {
                    // 没有用户组信息时补一个空组，避免解析用户信息时取不到 group 而整帖失败
                    try {
                        val user = JSON.parseObject(userJson)
                        user?.put("memberid", "-1")
                        if (user != null) {
                            userMap[uid] = user.toJSONString()
                        }
                    } catch (e: Throwable) {
                        // ignore
                    }
                }
                val groupKey = memberId ?: "-1"
                if (!groups.containsKey(groupKey)) {
                    val group = JSONObject()
                    group["0"] = ""
                    groups[groupKey] = group
                    changed = true
                }
            }
            if (changed || !userMap.containsKey("__GROUPS")) {
                userMap["__GROUPS"] = groups.toJSONString()
            }
        }
        return userMap
    }

    /**
     * 用户在 __U 里缺失时（网页对被屏蔽账号不输出资料），补一份最小资料，保证不会因为 NPE 整帖解析失败。
     */
    private fun ensureUser(userMap: MutableMap<String, String>, authorId: Int) {
        val key = authorId.toString()
        if (userMap.containsKey(key)) {
            return
        }
        val user = JSONObject()
        user["uid"] = authorId
        user["username"] = "UID:$authorId"
        user["memberid"] = "-1"
        user["postnum"] = "0"
        user["rvrc"] = "0"
        user["yz"] = "0"
        user["buffs"] = JSONObject()
        userMap[key] = user.toJSONString()

        val groups = try {
            JSON.parseObject(userMap["__GROUPS"] ?: "{}")
        } catch (e: Throwable) {
            null
        } ?: return
        if (!groups.containsKey("-1")) {
            val group = JSONObject()
            group["0"] = ""
            groups["-1"] = group
            userMap["__GROUPS"] = groups.toJSONString()
        }
    }

    // ------------------------------------------------------------------
    // 帖子级信息
    // ------------------------------------------------------------------

    private class PageMeta {
        var fid = 0
        var tid = 0
        var authorid = 0
        var type = 0
        var replies = 0
        var currentPage = 0
        var totalPages = 0
        var pageSize = DEFAULT_PAGE_SIZE
    }

    private fun parsePageMeta(html: String): PageMeta {
        val meta = PageMeta()
        firstGroup(html, "__CURRENT_PAGE\\s*=\\s*(\\d+)")?.toIntOrNull()?.let { meta.currentPage = it }
        // __PAGE = {0:'/read.php?tid=xxx',1: 总页数, 2: 当前页, 3: 每页楼层数}
        Regex("__PAGE\\s*=\\s*\\{\\s*0:[^,]*,\\s*1:(\\d+),\\s*2:(\\d+),\\s*3:(\\d+)")
            .find(html)?.let { match ->
                meta.totalPages = match.groupValues[1].toIntOrNull() ?: 0
                if (meta.currentPage <= 0) {
                    meta.currentPage = match.groupValues[2].toIntOrNull() ?: 0
                }
                val rowsPerPage = match.groupValues[3].toIntOrNull() ?: 0
                if (rowsPerPage >= DEFAULT_PAGE_SIZE) {
                    meta.pageSize = rowsPerPage
                }
            }

        val args = firstGroup(html, "commonui\\.postArg\\.setDefault\\(([\\s\\S]*?)\\)")
            ?.let { splitArguments(it) }
        if (args != null && args.size >= 4) {
            meta.fid = args.getOrNull(0)?.toIntOrNull() ?: 0
            meta.tid = args.getOrNull(2)?.toIntOrNull() ?: 0
            meta.authorid = args.getOrNull(3)?.toIntOrNull() ?: 0
            meta.type = args.getOrNull(10)?.toIntOrNull() ?: 0
            meta.replies = args.getOrNull(11)?.toIntOrNull() ?: 0
        }
        // 最后一页的实参是"本页楼层数"（不是每页楼层数），每页固定按 20 楼换算即可
        if (meta.pageSize < DEFAULT_PAGE_SIZE) {
            meta.pageSize = DEFAULT_PAGE_SIZE
        }
        return meta
    }

    private fun buildGlobal(html: String): String {
        val attachBaseView = firstGroup(html, "__ATTACH_BASE_VIEW\\s*=\\s*'([^']+)'")
            ?.let { if (it.contains("/attachments")) it else "$it/attachments" }
            ?: DEFAULT_ATTACH_BASE_VIEW
        val global = JSONObject()
        global["_ATTACH_BASE_VIEW"] = attachBaseView
        return global.toJSONString()
    }

    private fun parseSubject(html: String): String {
        val fromFirstPost = firstGroup(html, "<h3 id='postsubject0'>([\\s\\S]*?)</h3>")
        if (!fromFirstPost.isNullOrBlank()) {
            return normalize(fromFirstPost).trim()
        }
        val title = firstGroup(html, "<title>([\\s\\S]*?)</title>") ?: return ""
        return unescape(title).replace(" NGA玩家社区", "").trim()
    }

    // ------------------------------------------------------------------
    // 基础工具
    // ------------------------------------------------------------------

    private fun firstGroup(source: String, pattern: String): String? {
        val match = Regex(pattern).find(source) ?: return null
        return if (match.groups.size > 1) match.groupValues[1] else match.value
    }

    /**
     * 从 "commonui.userInfo.setAll(" 之后取出配对的 JSON 对象（对象内部有嵌套与字符串，需要按括号层级匹配）。
     */
    private fun extractJsonObject(source: String, anchor: String): String? {
        val start = source.indexOf(anchor)
        if (start < 0) {
            return null
        }
        val begin = source.indexOf('{', start)
        if (begin < 0) {
            return null
        }
        var depth = 0
        var inString = false
        var escape = false
        for (i in begin until source.length) {
            val c = source[i]
            if (inString) {
                when {
                    escape -> escape = false
                    c == '\\' -> escape = true
                    c == '"' -> inString = false
                }
            } else {
                when (c) {
                    '"' -> inString = true
                    '{' -> depth++
                    '}' -> {
                        depth--
                        if (depth == 0) {
                            return source.substring(begin, i + 1)
                        }
                    }
                }
            }
        }
        return null
    }

    /**
     * 拆分 JS 函数实参：逗号分隔，但要跳过双引号字符串与嵌套括号里的逗号。
     */
    private fun splitArguments(source: String): List<String> {
        val result = mutableListOf<String>()
        val builder = StringBuilder()
        var inString = false
        var escape = false
        var depth = 0
        for (c in source) {
            when {
                inString -> {
                    if (escape) {
                        builder.append(c)
                        escape = false
                    } else if (c == '\\') {
                        builder.append(c)
                        escape = true
                    } else {
                        if (c == '"') {
                            inString = false
                        } else {
                            builder.append(c)
                        }
                    }
                }
                c == '"' -> inString = true
                c == '(' || c == '[' || c == '{' -> {
                    depth++
                    builder.append(c)
                }
                c == ')' || c == ']' || c == '}' -> {
                    depth--
                    builder.append(c)
                }
                (c == ',' && depth == 0) -> {
                    result.add(builder.toString().trim())
                    builder.setLength(0)
                }
                else -> builder.append(c)
            }
        }
        val tail = builder.toString().trim()
        if (tail.isNotEmpty()) {
            result.add(tail)
        }
        return result
    }
}
