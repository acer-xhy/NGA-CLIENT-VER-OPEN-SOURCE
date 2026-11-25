package gov.anzong.androidnga.activity.compose.board

import android.content.Context
import com.alibaba.fastjson2.JSON
import com.justwen.androidnga.base.network.retrofit.RetrofitHelper
import com.justwen.androidnga.base.network.retrofit.awaitString
import gov.anzong.androidnga.Utils
import gov.anzong.androidnga.activity.compose.board.ForumBoardViewModel.BOARD_REMOTE_REQUEST_TIME_KEY
import gov.anzong.androidnga.activity.compose.board.data.ForumsListBean
import gov.anzong.androidnga.base.util.PreferenceUtils
import gov.anzong.androidnga.base.utils.Files
import gov.anzong.androidnga.common.util.LogUtils
import gov.anzong.androidnga.core.board.data.BoardEntity
import java.io.File

object ForumBoardRepository {

    private const val BOARD_FILE_NAME = "board_list.json"

    private const val BOARD_BOOKMARK_FILE_NAME = "board_bookmark.json"

    private const val BOARD_REMOTE_FILE_NAME = "board_list_remote.json"

    private const val FORUM_URL: String = "app_api.php?__lib=home&__act=category"

    private const val BOARD_LOCAL_VERSION_CURRENT = 6

    private const val BOARD_LOCAL_VERSION_KEY = "board_local_version"

    fun loadLocalBoardList(context: Context): MutableList<BoardEntity> {
        val boardJson: String
        val fileName = BOARD_FILE_NAME
        val dataFile = File(context.filesDir, fileName)

        // 诊断（样本收集期保留）：在版本自检/删除之前记录持久化文件原文与结构，
        // 用于确认「顶层板块 children 为 null」是否来自历史版本写平的 board_list.json。
        dumpLocalBoardDiag(context, dataFile)

        checkLocalDataVersion(dataFile)

        boardJson = if (!dataFile.exists()) {
            Files.readAssetString(context, fileName)
        } else {
            Files.readFile(dataFile)
        }
        return JSON.parseArray(
            boardJson, BoardEntity::class.java
        )
    }

    /**
     * 诊断（样本收集期保留，攒够样本后再删）：把本地持久化的 board_list.json 原文与结构落盘。
     * 记录顶层条目数量、每条的 type/children，用来判定「切 tab 崩溃」是否由历史版本写平的列表引起。
     */
    private fun dumpLocalBoardDiag(context: Context, file: File) {
        try {
            val diagDir = File(context.getExternalFilesDir(null), "diag")
            if (!diagDir.exists() && !diagDir.mkdirs()) {
                return
            }
            val now = System.currentTimeMillis()
            val stamp = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US)
                .format(java.util.Date(now))
            val sb = StringBuilder()
            sb.append("time=").append(stamp).append('\n')
            sb.append("file=").append(file.absolutePath).append('\n')
            sb.append("exists=").append(file.exists()).append('\n')
            if (file.exists()) {
                sb.append("size=").append(file.length()).append('\n')
                val raw = runCatching { Files.readFile(file) }.getOrNull()
                sb.append("len=").append(raw?.length ?: -1).append('\n')
                val list = raw?.let {
                    runCatching { JSON.parseArray(it, BoardEntity::class.java) }.getOrNull()
                }
                sb.append("topLevel=").append(list?.size ?: -1).append('\n')
                list?.forEachIndexed { index, board ->
                    val name = runCatching { board.name }.getOrElse { "<uninit>" }
                    sb.append("  [").append(index).append("] name=").append(name)
                        .append(" type=").append(board.type)
                        .append(" fid=").append(board.fid)
                        .append(" stid=").append(board.stid)
                        .append(" children=").append(board.children?.size ?: "NULL")
                        .append('\n')
                }
                sb.append("---RAW---\n").append(raw ?: "null")
            }
            Files.writeFile(File(diagDir, "boardlist_" + now + ".txt"), sb.toString())
            val files = diagDir.listFiles { _, n -> n.startsWith("boardlist_") }
            if (files != null && files.size > 10) {
                files.sortBy { it.lastModified() }
                for (i in 0 until files.size - 10) {
                    files[i].delete()
                }
            }
        } catch (ignore: Throwable) {
        }
    }

    private fun checkLocalDataVersion(file: File) {
        val currentVersion =
            PreferenceUtils.getData(BOARD_LOCAL_VERSION_KEY, BOARD_LOCAL_VERSION_CURRENT)
        if (currentVersion != BOARD_LOCAL_VERSION_CURRENT) {
            PreferenceUtils.putData(BOARD_LOCAL_VERSION_KEY, BOARD_LOCAL_VERSION_CURRENT)
            PreferenceUtils.putData(BOARD_REMOTE_REQUEST_TIME_KEY, 0L)
            if (file.exists()) {
                Files.delete(file)
            }
        }
    }

    fun loadBookmarkBoardList(context: Context): BoardEntity {
        val fileName = BOARD_BOOKMARK_FILE_NAME
        val dataFile = File(context.filesDir, fileName)

        val bookmarkBoard = BoardEntity().apply {
            id = "bookmark"
            name = "我的收藏"
            type = BoardEntity.BoardType.BOOKMARK
            children = mutableListOf()
        }
        if (dataFile.exists()) {
            val boardJson = Files.readFile(dataFile)
            val boardList: MutableList<BoardEntity> = JSON.parseArray(
                boardJson, BoardEntity::class.java
            )
            bookmarkBoard.children!!.addAll(boardList)
        }
        return bookmarkBoard
    }

    fun writeBookmarkBoard(context: Context, boardList: List<BoardEntity>) {
        val boardJson = JSON.toJSONString(boardList)
        val fileName = BOARD_BOOKMARK_FILE_NAME
        val dataFile = File(context.filesDir, fileName)
        Files.writeFile(dataFile, boardJson)
    }


    fun writeLocalBoardList(context: Context, boardList: List<BoardEntity>) {
        PreferenceUtils.putData(BOARD_LOCAL_VERSION_KEY, BOARD_LOCAL_VERSION_CURRENT)
        val boardJson = JSON.toJSONString(boardList)
        val fileName = BOARD_FILE_NAME
        val dataFile = File(context.filesDir, fileName)
        Files.writeFile(dataFile, boardJson)
    }

    suspend fun requestRemoteBoardList(context: Context): ForumsListBean? {
        try {
            val url = Utils.getNGAHost() + FORUM_URL
            val result = RetrofitHelper.getInstance().serviceKt.getString(url).awaitString()
            val bean = JSON.parseObject(result, ForumsListBean::class.java)
            if (bean != null) {
                writeRemoteBoardList(context, result)
            }
            return bean
        }  catch (e: Exception) {
            // 诊断（样本收集期保留）：保留完整栈，便于定位是签名被裁还是业务异常
            LogUtils.e("ForumBoardRepository", "requestRemoteBoardList: ${e.message}\n${android.util.Log.getStackTraceString(e)}")
            return null
        }
    }

    fun loadRemoteBoardList(context: Context): ForumsListBean? {
        val fileName = BOARD_REMOTE_FILE_NAME
        val dataFile = File(context.filesDir, fileName)

        if (!dataFile.exists()) {
            return null
        }
        val result = Files.readFile(dataFile)
        return JSON.parseObject(result, ForumsListBean::class.java)
    }

    private fun writeRemoteBoardList(context: Context, boardJson: String) {
        val fileName = BOARD_REMOTE_FILE_NAME
        val dataFile = File(context.filesDir, fileName)
        PreferenceUtils.putData(BOARD_LOCAL_VERSION_KEY, BOARD_LOCAL_VERSION_CURRENT)
        Files.writeFile(dataFile, boardJson)
    }

}