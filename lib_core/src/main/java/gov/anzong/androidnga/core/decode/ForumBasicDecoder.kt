package gov.anzong.androidnga.core.decode

import com.client.androidnga.core.data.html.HtmlData
import gov.anzong.androidnga.base.util.StringUtils

/**
 * Created by Justwen on 2018/8/25.
 */
class ForumBasicDecoder : IForumDecoder {

    override fun decode(content: String, htmlData: HtmlData?): String {
        var content = content
        if (StringUtils.isEmpty(content)) {
            return ""
        }

        val imageHost = htmlData?.attachmentHost

        // s = StringUtils.unEscapeHtml(s);
        val quoteStyle: String = STYLE_QUOTE

        val styleLeft = "<div style='float:left' >"
        val styleRight = "<div style='float:right' >"
        content = StringUtils.replaceAll(content, IForumDecoder.ignoreCaseTag + "&amp;", "&")
        content =
            StringUtils.replaceAll(content, IForumDecoder.ignoreCaseTag + "\\[l\\]", styleLeft)
        content = StringUtils.replaceAll(content, IForumDecoder.ignoreCaseTag + "\\[/l\\]", endDiv)

        // content = StringUtils.replaceAll(content, "\\[L\\]", styleLeft);
        // content = StringUtils.replaceAll(content, "\\[/L\\]", endDiv);
        content =
            StringUtils.replaceAll(content, IForumDecoder.ignoreCaseTag + "\\[r\\]", styleRight)
        content = StringUtils.replaceAll(content, IForumDecoder.ignoreCaseTag + "\\[/r\\]", endDiv)

        // content = StringUtils.replaceAll(content, "\\[R\\]", styleRight);
        // content = StringUtils.replaceAll(content, "\\[/R\\]", endDiv);
        content = StringUtils.replaceAll(
            content,
            IForumDecoder.ignoreCaseTag + "\\[align=right\\]",
            styleAlignRight
        )
        content = StringUtils.replaceAll(
            content,
            IForumDecoder.ignoreCaseTag + "\\[align=left\\]",
            styleAlignLeft
        )
        content = StringUtils.replaceAll(
            content,
            IForumDecoder.ignoreCaseTag + "\\[align=center\\]",
            styleAlignCenter
        )
        content =
            StringUtils.replaceAll(content, IForumDecoder.ignoreCaseTag + "\\[/align\\]", endDiv)

        content = StringUtils.replaceAll(
            content,
            IForumDecoder.ignoreCaseTag
                    + "\\[b\\]Reply to \\[pid=(.+?),(.+?),(.+?)\\]Reply\\[/pid\\] (.+?)\\[/b\\]",
            "[quote]Reply to [b]<a href='" + htmlData?.nGAHost + "read.php?searchpost=1&pid=$1&tid=$2' style='font-weight: bold;color:#3181f4'>[Reply]</a> $4[/b][/quote]"
        )

        content = StringUtils.replaceAll(
            content,
            IForumDecoder.ignoreCaseTag
                    + "\\<b\\>Reply to \\[pid=(.+?),(.+?),(.+?)\\]Reply\\[/pid\\] (.+?)\\</b\\>",
            "[quote]Reply to <b><a href='" + htmlData?.nGAHost + "read.php?searchpost=1&pid=$1&tid=$2' style='font-weight: bold;color:#3181f4'>[Reply]</a> $4</b>[/quote]"
        )

        content = StringUtils.replaceAll(
            content,
            IForumDecoder.ignoreCaseTag + "\\[pid=(.+?),(.+?),(.+?)\\]Reply\\[/pid\\]",
            "<a href='" + htmlData?.nGAHost + "read.php?searchpost=1&pid=$1&tid=$2' style='font-weight: bold;color:#3181f4'>[Reply]</a>"
        )

        // 某些帖子会导致这个方法卡住, 暂时不清楚原因, 和这个方法的作用.... by elrond
        /*content = StringUtils.replaceAll(content, 
                ignoreCaseTag + "={3,}((^=){0,}(.*?){0,}(^=){0,})={3,}",
                "<h4 style='font-weight: bold;border-bottom: 1px solid #AAA;clear: both;margin-bottom: 0px;'>$1</h4>");*/
        content =
            StringUtils.replaceAll(content, IForumDecoder.ignoreCaseTag + "\\[quote\\]", quoteStyle)
        content =
            StringUtils.replaceAll(content, IForumDecoder.ignoreCaseTag + "\\[/quote\\]", endDiv)

        content = StringUtils.replaceAll(
            content,
            IForumDecoder.ignoreCaseTag + "\\[code\\]",
            quoteStyle + "Code:"
        )
        content = StringUtils.replaceAll(
            content,
            IForumDecoder.ignoreCaseTag + "\\[code(.+?)\\]",
            quoteStyle
        )
        content =
            StringUtils.replaceAll(content, IForumDecoder.ignoreCaseTag + "\\[/code\\]", endDiv)

        // reply
        // content = StringUtils.replaceAll(content, 
        // ignoreCaseTag +"\\[pid=\\d+\\]Reply\\[/pid\\]", "Reply");
        // content = StringUtils.replaceAll(content, 
        // ignoreCaseTag +"\\[pid=\\d+,\\d+,\\d\\]Reply\\[/pid\\]", "Reply");

        // topic
        content = StringUtils.replaceAll(
            content, IForumDecoder.ignoreCaseTag + "\\[tid=\\d+\\]Topic\\[/pid\\]",
            "Topic"
        )
        content = StringUtils.replaceAll(
            content, IForumDecoder.ignoreCaseTag + "\\[tid=?(\\d{0,50})\\]Topic\\[/tid\\]",
            "<a href='" + htmlData?.nGAHost + "read.php?tid=$1' style='font-weight: bold;color:#3181f4'>[Topic]</a>"
        )
        // reply
        // s =
        // s.replaceAll("\\[b\\]Reply to \\[pid=\\d+\\]Reply\\[/pid\\] (Post by .+ \\(\\d{4,4}-\\d\\d-\\d\\d \\d\\d:\\d\\d\\))\\[/b\\]"
        // , "Reply to Reply <b>$1</b>");
        // 转换 tag
        // [b]
        content = StringUtils.replaceAll(content, IForumDecoder.ignoreCaseTag + "\\[b\\]", "<b>")
        content = StringUtils.replaceAll(
            content,
            IForumDecoder.ignoreCaseTag + "\\[/b\\]",
            "</b>" /* "</font>" */
        )

        // item
        content = StringUtils.replaceAll(content, IForumDecoder.ignoreCaseTag + "\\[item\\]", "<b>")
        content =
            StringUtils.replaceAll(content, IForumDecoder.ignoreCaseTag + "\\[/item\\]", "</b>")

        content = StringUtils.replaceAll(content, IForumDecoder.ignoreCaseTag + "\\[u\\]", "<u>")
        content = StringUtils.replaceAll(content, IForumDecoder.ignoreCaseTag + "\\[/u\\]", "</u>")

        content = StringUtils.replaceAll(
            content, IForumDecoder.ignoreCaseTag + "\\[s:(\\d+)\\]",
            "<img src='file:///android_asset/a$1.gif'>"
        )
        content = content.replace(IForumDecoder.ignoreCaseTag + "<br/><br/>", "<br/>")
        // [url][/url]
        content = StringUtils.replaceAll(
            content,
            IForumDecoder.ignoreCaseTag + "\\[url\\]/([^\\[|\\]]+)\\[/url\\]",
            "<a href=\"" + htmlData?.nGAHost + "$1\" style='color:#3181f4'>" + htmlData?.nGAHost + "$1</a>"
        )
        content = StringUtils.replaceAll(
            content,
            IForumDecoder.ignoreCaseTag + "\\[url\\]([^\\[|\\]]+)\\[/url\\]",
            "<a href=\"$1\" style='color:#3181f4'>$1</a>"
        )
        content = StringUtils.replaceAll(
            content, IForumDecoder.ignoreCaseTag
                    + "\\[url=/([^\\[|\\]]+)\\]\\s*(.+?)\\s*\\[/url\\]",
            "<a href=\"" + htmlData?.nGAHost + "$1\" style='color:#3181f4'>$2</a>"
        )
        content = StringUtils.replaceAll(
            content, IForumDecoder.ignoreCaseTag
                    + "\\[url=([^\\[|\\]]+)\\]\\s*(.+?)\\s*\\[/url\\]",
            "<a href=\"$1\">$2</a>"
        )
        content = StringUtils.replaceAll(
            content, IForumDecoder.ignoreCaseTag
                    + "\\[uid=?(\\d{0,50})\\](.+?)\\[\\/uid\\]", "$2"
        )
        content = StringUtils.replaceAll(
            content,
            IForumDecoder.ignoreCaseTag + "Post by\\s{0,}([^\\[\\s]{1,})\\s{0,}\\(",
            "Post by <a href='" + htmlData?.nGAHost + "nuke.php?func=ucp&username=$1' style='font-weight: bold;color:#3181f4'>[$1]</a> ("
        )
        content = StringUtils.replaceAll(
            content,
            IForumDecoder.ignoreCaseTag + "\\[@(.{2,20}?)\\]",
            "<a href='" + htmlData?.nGAHost + "nuke.php?func=ucp&username=$1' style='font-weight: bold;color:#3181f4'>[@$1]</a>"
        )
        content = StringUtils.replaceAll(
            content, IForumDecoder.ignoreCaseTag
                    + "\\[uid=-?(\\d{0,50})\\](.+?)\\[\\/uid\\]", "$2"
        )
        content = StringUtils.replaceAll(
            content, IForumDecoder.ignoreCaseTag
                    + "\\[hip\\](.+?)\\[\\/hip\\]",
            "$1"
        )
        content = StringUtils.replaceAll(
            content, IForumDecoder.ignoreCaseTag + "\\[tid=?(\\d{0,50})\\](.+?)\\[/tid\\]",
            "<a href='" + htmlData?.nGAHost + "read.php?tid=$1' style='font-weight: bold;color:#3181f4'>[$2]</a>"
        )
        content = StringUtils.replaceAll(
            content,
            IForumDecoder.ignoreCaseTag
                    + "\\[pid=(.+?)\\]\\[/pid\\]",
            "<a href='" + htmlData?.nGAHost + "read.php?pid=$1' style='font-weight: bold;color:#3181f4'>[Reply]</a>"
        )
        content = StringUtils.replaceAll(
            content,
            IForumDecoder.ignoreCaseTag
                    + "\\[pid=(.+?)\\](.+?)\\[/pid\\]",
            "<a href='" + htmlData?.nGAHost + "read.php?pid=$1' style='font-weight: bold;color:#3181f4'>[$2]</a>"
        )
        // flash - B 站视频链接：渲染为外链卡片，避免"像是可以就地播放"的播放器外观
        content = StringUtils.replaceAll(
            content,
            IForumDecoder.ignoreCaseTag + "\\[flash\\]((?:https?://)?(?:(?:www\\.|m\\.)?bilibili\\.com/video/|b23\\.tv/)[^\\[|\\]]+)\\[/flash\\]",
            buildVideoLinkCard(htmlData?.isDarkMode == true)
        )

        // flash - 其他链接：保留原占位图
        content = StringUtils.replaceAll(
            content,
            IForumDecoder.ignoreCaseTag + "\\[flash\\](http[^\\[|\\]]+)\\[/flash\\]",
            "<a href=\"$1\"><img src='file:///android_asset/flash.png' style= 'max-width:100%;' ></a>"
        )

        // color

        // content = StringUtils.replaceAll(content, "\\[color=([^\\[|\\]]+)\\]\\s*(.+?)\\s*\\[/color\\]"
        // ,"<b style=\"color:$1\">$2</b>");
        content = StringUtils.replaceAll(
            content, IForumDecoder.ignoreCaseTag + "\\[color=([^\\[|\\]]+)\\]",
            styleColor
        )
        content =
            StringUtils.replaceAll(content, IForumDecoder.ignoreCaseTag + "\\[/color\\]", "</span>")

        // lessernuke
        content = StringUtils.replaceAll(content, "\\[lessernuke\\]", lesserNukeStyle)
        content = StringUtils.replaceAll(content, "\\[/lessernuke\\]", endDiv)

        // [table][/table]
        content = StringUtils.replaceAll(
            content,
            "\\[table](.*?)\\[/table]",
            "<div><table cellspacing='0px' class='default'><tbody>$1</tbody></table></div>"
        )

        // [tr][/tr]
        content = StringUtils.replaceAll(content, "\\[tr](.*?)\\[/tr]", "<tr>$1</tr>")
        content = StringUtils.replaceAll(
            content, IForumDecoder.ignoreCaseTag
                    + "\\[td[ ]*(\\d+)\\]",
            "<td style='border-left:1px solid #aaa;border-bottom:1px solid #aaa'>"
        )
        content = StringUtils.replaceAll(
            content, IForumDecoder.ignoreCaseTag
                    + "\\[td\\scolspan(\\d+)\\swidth(\\d+)\\]",
            "<td colspan='$1' style='width:$2%;border-left:1px solid #aaa;border-bottom:1px solid #aaa'>"
        )
        content = StringUtils.replaceAll(
            content, IForumDecoder.ignoreCaseTag
                    + "\\[td\\swidth(\\d+)\\scolspan(\\d+)\\]",
            "<td colspan='$2' style='width:$1%;border-left:1px solid #aaa;border-bottom:1px solid #aaa'>"
        )

        content = StringUtils.replaceAll(
            content, IForumDecoder.ignoreCaseTag
                    + "\\[td\\swidth(\\d+)\\srowspan(\\d+)\\]",
            "<td rowspan='$2' style='width:$1%;border-left:1px solid #aaa;border-bottom:1px solid #aaa'>"
        )
        content = StringUtils.replaceAll(
            content, IForumDecoder.ignoreCaseTag
                    + "\\[td\\srowspan(\\d+)\\swidth(\\d+)\\]",
            "<td rowspan='$1' style='width:$2%;border-left:1px solid #aaa;border-bottom:1px solid #aaa'>"
        )

        content = StringUtils.replaceAll(
            content, IForumDecoder.ignoreCaseTag
                    + "\\[td\\scolspan(\\d+)\\srowspan(\\d+)\\swidth(\\d+)\\]",
            "<td colspan='$1' rowspan='$2' style='width:$3%;border-left:1px solid #aaa;border-bottom:1px solid #aaa'>"
        )
        content = StringUtils.replaceAll(
            content, IForumDecoder.ignoreCaseTag
                    + "\\[td\\scolspan(\\d+)\\swidth(\\d+)\\srowspan(\\d+)\\]",
            "<td colspan='$1' rowspan='$3' style='width:$2%;border-left:1px solid #aaa;border-bottom:1px solid #aaa'>"
        )
        content = StringUtils.replaceAll(
            content, IForumDecoder.ignoreCaseTag
                    + "\\[td\\srowspan(\\d+)\\scolspan(\\d+)\\swidth(\\d+)\\]",
            "<td rowspan='$1' colspan='$2' style='width:$3%;border-left:1px solid #aaa;border-bottom:1px solid #aaa'>"
        )
        content = StringUtils.replaceAll(
            content, IForumDecoder.ignoreCaseTag
                    + "\\[td\\srowspan(\\d+)\\swidth(\\d+)\\scolspan(\\d+)\\]",
            "<td rowspan='$1' colspan='$3' style='width:$2%;border-left:1px solid #aaa;border-bottom:1px solid #aaa'>"
        )
        content = StringUtils.replaceAll(
            content, IForumDecoder.ignoreCaseTag
                    + "\\[td\\swidth(\\d+)\\scolspan(\\d+)\\srowspan(\\d+)\\]",
            "<td rowspan='$3' colspan='$2' style='width:$1%;border-left:1px solid #aaa;border-bottom:1px solid #aaa'>"
        )
        content = StringUtils.replaceAll(
            content, IForumDecoder.ignoreCaseTag
                    + "\\[td\\swidth(\\d+)\\srowspan(\\d+)\\scolspan(\\d+)\\]",
            "<td rowspan='$2' colspan='$3'  style='width:$1%;border-left:1px solid #aaa;border-bottom:1px solid #aaa'>"
        )


        content = StringUtils.replaceAll(
            content, IForumDecoder.ignoreCaseTag
                    + "\\[td\\scolspan=?(\\d+)\\]",
            "<td colspan='$1' style='border-left:1px solid #aaa;border-bottom:1px solid #aaa'>"
        )
        content = StringUtils.replaceAll(
            content, IForumDecoder.ignoreCaseTag
                    + "\\[td\\srowspan=?(\\d+)\\]",
            "<td rowspan='$1' style='border-left:1px solid #aaa;border-bottom:1px solid #aaa;'>"
        )
        content = StringUtils.replaceAll(
            content,
            "\\[td\\]",
            "<td style='border-left:1px solid #aaa;border-bottom:1px solid #aaa;'>"
        )
        content = StringUtils.replaceAll(content, "\\[/td\\]", "</td>")
        // 处理表格外面的额外空行
        content = StringUtils.replaceAll(content, "<([/]?(table|tbody|tr|td))><br/>", "<$1>")
        // [i][/i]
        content = StringUtils.replaceAll(
            content, IForumDecoder.ignoreCaseTag + "\\[i\\]",
            "<i style=\"font-style:italic\">"
        )
        content = StringUtils.replaceAll(content, IForumDecoder.ignoreCaseTag + "\\[/i\\]", "</i>")
        // [del][/del]
        content = StringUtils.replaceAll(
            content,
            IForumDecoder.ignoreCaseTag + "\\[del\\]",
            "<del class=\"gray\">"
        )
        content =
            StringUtils.replaceAll(content, IForumDecoder.ignoreCaseTag + "\\[/del\\]", "</del>")

        content = StringUtils.replaceAll(
            content, IForumDecoder.ignoreCaseTag + "\\[font=([^\\[|\\]]+)\\]",
            "<span style=\"font-family:$1\">"
        )
        content =
            StringUtils.replaceAll(content, IForumDecoder.ignoreCaseTag + "\\[/font\\]", "</span>")

        // size
        content = StringUtils.replaceAll(
            content, IForumDecoder.ignoreCaseTag + "\\[size=(\\d+)%?\\]",
            "<span style=\"font-size:$1%;line-height:$1%\">"
        )
        content =
            StringUtils.replaceAll(content, IForumDecoder.ignoreCaseTag + "\\[/size\\]", "</span>")

        // [list][/list]
        // TODO: 2018/9/18  部分页面里和 collapse 标签有冲突 http://bbs.nga.cn/read.php?tid=14949699
        content = StringUtils.replaceAll(
            content,
            IForumDecoder.IGNORE_CASE_TAG + "\\[list\\](.+?)\\[/list\\]",
            "<ul>$1</ul>"
        )
        content = StringUtils.replaceAll(content, IForumDecoder.IGNORE_CASE_TAG + "\\[list\\]", "")
        content = StringUtils.replaceAll(content, IForumDecoder.IGNORE_CASE_TAG + "\\[/list\\]", "")
        content = StringUtils.replaceAll(
            content,
            IForumDecoder.IGNORE_CASE_TAG + "\\[\\*\\](.+?)<br/>",
            "<li>$1</li>"
        )

        // [h][/h]
        content = StringUtils.replaceAll(
            content,
            IForumDecoder.IGNORE_CASE_TAG + "\\[h](.+?)\\[/h]",
            "<b>$1</b>"
        )

        // [collapse][/collapse]
        content = StringUtils.replaceAll(
            content,
            "\\[collapse=(.*?)](.*?)\\[/collapse]",
            "<div><button onclick='toggleCollapse(this,\"$1\")'>点击显示内容 : $1</button><div name='collapse' class='collapse' style='display:none'>$2</div></div>"
        )
        content = StringUtils.replaceAll(
            content,
            "\\[collapse](.*?)\\[/collapse]",
            "<div><button onclick='toggleCollapse(this)'>点击显示内容</button><div name='collapse' class='collapse'style='display:none' >$1</div></div>"
        )

        // [flash=video]/flash]
        content = StringUtils.replaceAll(
            content,
            "\\[flash=video].(.*?)\\[/flash]",
            "<video src='http://$imageHost/attachments$1' controls='controls'></video>"
        )

        // [flash=audio][/flash]"
        content = StringUtils.replaceAll(
            content,
            "\\[flash=audio].(.*?)\\[/flash]",
            "<audio src='http://img.ngacn.cc/attachments$1&filename=nga_audio.mp3' controls='controls'></audio>"
        )

        // [flash][/flash]
        content = StringUtils.replaceAll(
            content,
            "\\[flash].(.*?)\\[/flash]",
            "<video src='http://$imageHost/attachments$1' controls='controls'></video>"
        )

        return content
    }

    /**
     * B 站视频的外链卡片（$1 由 replaceAll 填入原始链接）。
     * 不使用"播放器"外观，明确表达"这是要跳出去看的视频"。
     */
    private fun buildVideoLinkCard(darkMode: Boolean): String {
        val borderColor = if (darkMode) "#3d3d3d" else "#e5e5e5"
        val subColor = if (darkMode) "#9a9a9a" else "#888888"
        return "<a href=\"$1\" style=\"display:block;text-decoration:none;border:1px solid $borderColor;" +
                "border-radius:8px;padding:8px;margin:6px 0\">" +
                "<table style=\"width:100%;border-collapse:collapse\"><tr>" +
                "<td style=\"width:44px;vertical-align:middle\">" +
                "<img src=\"file:///android_asset/video_link.png\" style=\"width:40px;height:40px;display:block\">" +
                "</td>" +
                "<td style=\"vertical-align:middle;padding-left:10px\">" +
                "<div style=\"font-size:14px;font-weight:bold;color:#3181f4\">外部视频</div>" +
                "<div style=\"font-size:11px;color:$subColor;margin-top:2px\">bilibili.com · 点击用哔哩哔哩打开</div>" +
                "</td>" +
                "<td style=\"width:18px;text-align:right;vertical-align:middle;color:$subColor;font-size:14px\">&#8599;</td>" +
                "</tr></table></a>"
    }

    companion object {
        private const val lesserNukeStyle =
            "<div style='border:1px solid #B63F32;margin:10px 10px 10px 10px;padding:10px' > <span style='color:#EE8A9E'>用户因此贴被暂时禁言，此效果不会累加</span><br/>"
        private const val styleAlignRight = "<div style='text-align:right' >"
        private const val styleAlignLeft = "<div style='text-align:left' >"
        private const val styleAlignCenter = "<div style='text-align:center' >"
        private const val styleColor = "<span style='color:$1' >"
        private const val endDiv = "</div>"

        private const val STYLE_QUOTE = "<div class='quote' >"
    }
}
