package com.example.llmragplatform.service

import org.springframework.core.io.ClassPathResource
import org.springframework.stereotype.Component

@Component
/**
 * prompt テンプレートの読み込みと変数展開を担当するサービス。
 */
class PromptManager {

    private val templateCache = mutableMapOf<String, String>()

    /**
     * テンプレートを読み込み、指定変数を差し込んで完成 prompt を返す。
     *
     * @param templateName 利用するテンプレート名。
     * @param variables 差し込むテンプレート変数一覧。
     * @return 変数展開後の prompt 文字列。
     */
    fun buildPrompt(templateName: String, variables: Map<String, String>): String {
        // テンプレート本文を読み込み、差し込み対象の元文字列を得る。
        val template = loadTemplate(templateName)
        // {{key}} 形式のプレースホルダを順に実データへ置換する。
        return variables.entries.fold(template) { acc, (key, value) ->
            acc.replace("{{$key}}", value)
        }
    }

    /**
     * テンプレートファイルを classpath から読み込み、キャッシュして返す。
     *
     * @param name 読み込むテンプレート名。
     * @return テンプレート本文。
     */
    private fun loadTemplate(name: String): String {
        // 一度読んだテンプレートはキャッシュし、次回以降は再読込を避ける。
        return templateCache.getOrPut(name) {
            ClassPathResource("prompts/$name.txt")
                .inputStream
                .bufferedReader()
                .use { it.readText() }
        }
    }
}
