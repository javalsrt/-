package com.znxsgl.exception;

/**
 * 限流异常：当请求被全局限流或用户级限流拦截时抛出。
 */
public class RateLimitException extends RuntimeException {
    public RateLimitException(String message) {
        super(message);
    }
}