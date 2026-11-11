package io.github.chenlongapps.modelatlas.ai

/** 不保留远端 body/message、请求或底层异常 cause，避免错误上报泄露密钥及对话内容。 */
sealed class LlmException(message: String) : Exception(message) {
    class Http(val statusCode: Int, val retryAfterSeconds: Long?) : LlmException("LLM HTTP error ($statusCode)")
    class Network : LlmException("LLM network request failed")
    class Timeout : LlmException("LLM request timed out")
    class Protocol : LlmException("Invalid or incomplete LLM response")
    class Remote : LlmException("LLM service reported an error")
}
