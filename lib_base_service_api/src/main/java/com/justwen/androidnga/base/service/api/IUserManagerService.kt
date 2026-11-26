package com.justwen.androidnga.base.service.api

import android.content.Context

import kotlinx.coroutines.flow.StateFlow

interface IUserManagerService  {

    companion object {
        const val ROUTER_PATH = "/user/service"
    }

    val activeAccountIndexState: StateFlow<Int>

    fun showUserSwitchDialog(context: Context, callback: (() -> Unit)? = null)

    fun addUser(uid: String, cid: String, name: String)

    fun isActiveAccount(uid: String, name: String = "") : Boolean

    /**
     * 更新本地保存的头像地址（侧边栏、账号管理页均读取该字段）
     */
    fun setAvatarUrl(uid: String, url: String)

}