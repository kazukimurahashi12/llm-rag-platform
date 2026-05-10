package com.example.llmragplatform.infrastructure

import com.example.llmragplatform.exception.OpenAiTemporarilyUnavailableException
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker
import io.github.resilience4j.retry.annotation.Retry
import org.springframework.http.HttpStatus
import org.springframework.http.HttpStatusCode
import org.springframework.stereotype.Service
import org.springframework.web.client.ResourceAccessException
import org.springframework.web.client.RestClientResponseException

@Service
/**
 * OpenAI 呼び出しに timeout / retry / circuit breaker をまとめて適用するラッパー。
 */
class OpenAiResilienceService {

    /**
     * OpenAI への外部呼び出しを保護付きで実行する。
     *
     * @param operationName ログ・例外用の操作名。
     * @param block 実際の OpenAI 呼び出し。
     * @return OpenAI 応答。
     */
    @Retry(name = "openai", fallbackMethod = "handleRetryFallback")
    @CircuitBreaker(name = "openai", fallbackMethod = "handleCircuitBreakerFallback")
    fun <T> execute(operationName: String, block: () -> T): T {
        try {
            return block()
        } catch (ex: RestClientResponseException) {
            if (ex.statusCode.isRetryableOpenAiStatus()) {
                throw OpenAiTemporarilyUnavailableException(
                    message = "OpenAI temporary failure during $operationName",
                    details = listOf("status=${ex.rawStatusCode}", ex.responseBodyAsString.take(500))
                )
            }
            throw ex
        } catch (ex: ResourceAccessException) {
            throw OpenAiTemporarilyUnavailableException(
                message = "OpenAI temporary access failure during $operationName",
                details = listOfNotNull(ex.mostSpecificCause?.message ?: ex.message)
            )
        }
    }

    /**
     * retry または circuit breaker からの最終失敗を一時障害例外へ統一する。
     *
     * @param operationName 実行していた OpenAI 操作名。
     * @param block 実際の処理本体。
     * @param throwable 最終失敗原因。
     * @return 戻り値は返さず常に例外を送出する。
     */
    @Suppress("UNUSED_PARAMETER")
    private fun <T> handleRetryFallback(operationName: String, block: () -> T, throwable: Throwable): T {
        throw temporaryUnavailable(operationName, throwable)
    }

    /**
     * circuit breaker OPEN 時の fail-fast を一時障害例外へ統一する。
     *
     * @param operationName 実行していた OpenAI 操作名。
     * @param block 実際の処理本体。
     * @param throwable fail-fast の原因。
     * @return 戻り値は返さず常に例外を送出する。
     */
    @Suppress("UNUSED_PARAMETER")
    private fun <T> handleCircuitBreakerFallback(operationName: String, block: () -> T, throwable: Throwable): T {
        throw temporaryUnavailable(operationName, throwable)
    }

    /**
     * 例外を OpenAI 一時障害として利用者向けの例外へ変換する。
     *
     * @param operationName 実行していた OpenAI 操作名。
     * @param throwable 元の失敗原因。
     * @return 一時障害用例外。
     */
    private fun temporaryUnavailable(operationName: String, throwable: Throwable): OpenAiTemporarilyUnavailableException {
        return when (throwable) {
            is OpenAiTemporarilyUnavailableException -> throwable
            else -> OpenAiTemporarilyUnavailableException(
                message = "OpenAI is temporarily unavailable during $operationName",
                details = listOfNotNull(throwable.message)
            )
        }
    }

    /**
     * retry 対象にする OpenAI 側一時障害 HTTP ステータスかを判定する。
     *
     * @return retry 対象なら true。
     */
    private fun HttpStatusCode.isRetryableOpenAiStatus(): Boolean {
        return this.value() == HttpStatus.TOO_MANY_REQUESTS.value() ||
            this.value() == HttpStatus.BAD_GATEWAY.value() ||
            this.value() == HttpStatus.SERVICE_UNAVAILABLE.value() ||
            this.value() == HttpStatus.GATEWAY_TIMEOUT.value()
    }
}
