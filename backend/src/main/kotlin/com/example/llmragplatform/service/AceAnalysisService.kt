package com.example.llmragplatform.service

import com.example.llmragplatform.domain.entity.AceCategory
import org.springframework.stereotype.Service

@Service
/**
 * 相談文を ACE モデルの観点で分類し、理由つきの分析結果を返すサービス。
 */
class AceAnalysisService {

    /**
     * situation と targetGoal をまとめて評価し、主要な ACE 観点を返す。
     *
     * @param situation 現在の状況説明。
     * @param targetGoal 達成したい状態や目標。
     * @return 主要カテゴリ、理由、カテゴリ別スコアを含む分析結果。
     */
    fun analyze(situation: String, targetGoal: String): AceAnalysisResult {
        // 状況説明と目標を連結し、同じ分類ロジックで評価できるようにする。
        val combinedText = "$situation\n$targetGoal"
        // ACE ごとのキーワード一致数を数えて、優先度を決める。
        val abilityScore = scoreOf(combinedText, ABILITY_KEYWORDS)
        val cultureScore = scoreOf(combinedText, CULTURE_KEYWORDS)
        val expectationScore = scoreOf(combinedText, EXPECTATION_KEYWORDS)
        // スコアが同点のときは Expectation を優先し、その次に Culture、最後に Ability を採用する。
        val primaryCategory = listOf(
            AceCategory.EXPECTATION to expectationScore,
            AceCategory.CULTURE to cultureScore,
            AceCategory.ABILITY to abilityScore,
        ).maxBy { it.second }.first
        // 主要カテゴリに応じた説明理由を返す。
        val reason = when (primaryCategory) {
            AceCategory.ABILITY ->
                "スキル習得、知識不足、業務手順の理解に関する表現が多く、技術学習の支援が中心課題と判断しました。"
            AceCategory.CULTURE ->
                "報連相、心理的安全性、チーム内コミュニケーション、組織の作法に関する表現が多く、文化適応が中心課題と判断しました。"
            AceCategory.EXPECTATION ->
                "役割、期待値、目標、評価、責任範囲に関する表現が多く、役割期待のすり合わせが中心課題と判断しました。"
        }

        return AceAnalysisResult(
            primaryCategory = primaryCategory,
            reason = reason,
            abilityScore = abilityScore,
            cultureScore = cultureScore,
            expectationScore = expectationScore,
        )
    }

    /**
     * 自由文テキストを単体で ACE 分類する。
     *
     * @param text 分類対象の自由文。
     * @return 主要カテゴリ、理由、カテゴリ別スコアを含む分析結果。
     */
    fun analyze(text: String): AceAnalysisResult {
        return analyze(text, "")
    }

    /**
     * 指定したキーワード群にどれだけ一致するかをスコア化する。
     *
     * @param text 評価対象テキスト。
     * @param keywords 一致を調べるキーワード一覧。
     * @return キーワード一致数に基づくカテゴリスコア。
     */
    private fun scoreOf(text: String, keywords: Set<String>): Double {
        // 大文字小文字差の影響を減らすため小文字化してから一致判定する。
        val normalizedText = text.lowercase()
        // 一致したキーワード数を数え、最低 1.0 を持たせて同率時の分類を安定させる。
        return 1.0 + keywords.count { keyword -> normalizedText.contains(keyword) }.toDouble()
    }

    companion object {
        private val ABILITY_KEYWORDS = setOf(
            "スキル", "知識", "学習", "技術", "手順", "業務理解", "キャッチアップ",
            "習得", "理解できていない", "できない", "不慣れ", "オンボード", "トレーニング",
            "週報", "提出", "業務", "実務"
        )
        private val CULTURE_KEYWORDS = setOf(
            "文化", "価値観", "報連相", "コミュニケーション", "心理的安全性", "雰囲気", "なじめない",
            "相談しづらい", "暗黙知", "チーム", "振る舞い", "作法", "関係性", "1on1", "フィードバック"
        )
        private val EXPECTATION_KEYWORDS = setOf(
            "期待", "期待値", "役割", "目標", "成果", "責任", "評価", "評価面談", "すり合わせ",
            "求める", "ミッション", "優先順位", "任せたい", "ゴール", "重要性", "振り返り"
        )
    }
}

/**
 * ACE 分類の結果を表す内部モデル。
 */
data class AceAnalysisResult(
    val primaryCategory: AceCategory,
    val reason: String,
    val abilityScore: Double,
    val cultureScore: Double,
    val expectationScore: Double,
)
