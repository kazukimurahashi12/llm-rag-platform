package com.example.llmragplatform.service

import com.example.llmragplatform.generated.model.AdviceRequest
import com.example.llmragplatform.generated.model.AdviceResponse

/**
 * マネジメントアドバイス生成のユースケースを表すサービス契約。
 */
interface AdviceService {
    /**
     * 相談内容からアドバイスと根拠文書を生成して返す。
     *
     * @param request 相談内容と出力設定を含むリクエスト。
     * @return 生成アドバイス、usage、根拠文書一覧を含むレスポンス。
     */
    fun generateAdvice(request: AdviceRequest): AdviceResponse
}
