package gov.anzong.androidnga.service

import com.justwen.androidnga.base.service.api.IThemeManagerService
import sp.phone.theme.ThemeManager

class ThemeManagerService : IThemeManagerService {
    override fun isNightMode(): Boolean {
        return ThemeManager.getInstance().isNightMode()
    }

    override fun getThemeIndex(): Int {
        return ThemeManager.getInstance().getThemeIndex()
    }

    override fun getTheme(): Int {
        return ThemeManager.getInstance().getTheme(true)
    }
}
