package org.ghkdqhrbals.oauth.utils

import java.security.SecureRandom

internal object RandomUtils {

    private val charset = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
    private val random = SecureRandom()

    /**
     * 지정된 길이만큼의 랜덤 문자열을 생성합니다.
     * @param length 생성할 문자열 길이 (1 이상)
     * @return length 길이의 난수 문자열
     */
    fun generate(length: Int): String {
        require(length > 0) { "length must be > 0" }

        return buildString(length) {
            repeat(length) {
                append(charset[random.nextInt(charset.length)])
            }
        }
    }
}