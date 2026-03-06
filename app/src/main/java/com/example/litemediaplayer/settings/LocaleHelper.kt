package com.example.litemediaplayer.settings

import android.app.LocaleManager
import android.content.Context
import android.os.Build
import android.os.LocaleList
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat

object LocaleHelper {

    /**
     * 現在適用されているロケールを取得（AppCompatDelegate から）。
     */
    fun getCurrentAppliedLanguage(): AppLanguage {
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

    /**
     * 言語を適用する。
     * 現在と同じ言語であれば何もしない。
     *
     * @return true=ロケール変更あり, false=変更なし
     */
    fun applyLanguageIfNeeded(context: Context, language: AppLanguage): Boolean {
        val current = getCurrentAppliedLanguage()
        if (current == language) {
            return false
        }

        applyLanguageForce(context, language)
        return true
    }

    /**
     * 強制的にロケールを適用する（呼び出し元が重複チェック済みの場合のみ使用）。
     */
    private fun applyLanguageForce(context: Context, language: AppLanguage) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            try {
                val localeManager = context.getSystemService(LocaleManager::class.java)
                if (language == AppLanguage.SYSTEM) {
                    localeManager.applicationLocales = LocaleList.getEmptyLocaleList()
                } else {
                    localeManager.applicationLocales =
                        LocaleList.forLanguageTags(language.toLanguageTag())
                }
                return
            } catch (_: Exception) {
                // Android 13 でも例外時は AppCompatDelegate へフォールバック
            }
        }

        val tag = if (language == AppLanguage.SYSTEM) "" else language.toLanguageTag()
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tag))
    }

    private fun AppLanguage.toLanguageTag(): String {
        return when (this) {
            AppLanguage.JA -> "ja"
            AppLanguage.EN -> "en"
            AppLanguage.SYSTEM -> ""
        }
    }
}
