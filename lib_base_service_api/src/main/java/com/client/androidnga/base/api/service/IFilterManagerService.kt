package com.client.androidnga.base.api.service



interface IFilterManagerService {

    companion object {
        const val ROUTER_PATH = "/main/filter"
    }

    fun isFilterUser(uid: String): Boolean

    fun addFilterUser(uid: String, userName: String)

    fun removeFilterUser(uid: String)

}