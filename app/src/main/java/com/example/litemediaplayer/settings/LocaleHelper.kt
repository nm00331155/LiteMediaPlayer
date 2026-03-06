package com.example.litemediaplayer.settings

import android.app.LocaleManager
import android.content.Context
import android.os.Build
import android.os.LocaleList
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat

object LocaleHelper {

    fun applyLanguage(context: Context, language: AppLanguage) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val localeManager = context.getSystemService(LocaleManager::class.java)
            if (language == AppLanguage.SYSTEM) {
                localeManager.applicationLocales = LocaleList.getEmptyLocaleList()
            } else {
                localeManager.applicationLocales = LocaleList.forLanguageTags(language.toLanguageTag())
            }
        } else {
            val tag = if (language == AppLanguage.SYSTEM) "" else language.toLanguageTag()
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tag))
        }
    }

    fun getCurrentLanguage(): AppLanguage {
        val locales = AppCompatDelegate.getApplicationLocales()
        if (locales.isEmpty) {
            return AppLanguage.SYSTEM
        }

        return when (locales[0]?.language) {
            "ja" -> AppLanguage.JA
            "en" -> AppLanguage.EN
            else -> AppLanguage.SYSTEM
        }
    }

    private fun AppLanguage.toLanguageTag(): String {
        return when (this) {
            AppLanguage.JA -> "ja"
            AppLanguage.EN -> "en"
            AppLanguage.SYSTEM -> ""
        }
    }
}
