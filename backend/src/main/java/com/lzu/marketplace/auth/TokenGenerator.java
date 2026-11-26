package com.lzu.marketplace.auth;

import java.security.SecureRandom;

/**
 * 生成随机 token：32 字节 {@link SecureRandom} → 64 位十六进制。
 *
 * <p>不用 UUID：{@code UUID.randomUUID()} 本质也是 122 位随机数，
 * 但 {@link SecureRandom} 的熵源与长度选择更直接可控。
 */
final class TokenGenerator {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final char[] HEX = "0123456789abcdef".toCharArray();

    private TokenGenerator() {
    }

    static String next() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        StringBuilder sb = new StringBuilder(64);
        for (byte b : bytes) {
            sb.append(HEX[(b >> 4) & 0xF]).append(HEX[b & 0xF]);
        }
        return sb.toString();
    }
}
