package com.suishi.ai.provider;

/** Safe classification only; never retain an upstream body, headers, URL query or exception message. */
public final class AiFailure extends RuntimeException {
    public enum Kind { TIMEOUT, UNAVAILABLE, AUTHENTICATION, PROTOCOL, UNSAFE_OUTPUT, CANCELLED, STALE_CONTEXT, DISABLED }
    private final Kind kind;
    private final boolean retryable;
    public AiFailure(Kind kind,boolean retryable){super(kind.name());this.kind=kind;this.retryable=retryable;}
    public Kind kind(){return kind;}
    public boolean retryable(){return retryable;}
}
