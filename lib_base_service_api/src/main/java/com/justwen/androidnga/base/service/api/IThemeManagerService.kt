package com.justwen.androidnga.base.service.api



interface IThemeManagerService  {

    companion object {
        const val ROUTER_PATH = "/main/theme"
    }

    fun isNightMode(): Boolean

    fun getThemeIndex(): Int

    fun getTheme(): Int

}