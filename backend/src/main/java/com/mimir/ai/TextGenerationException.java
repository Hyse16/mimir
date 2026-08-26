package com.mimir.ai;

public class TextGenerationException extends RuntimeException {

    public TextGenerationException(String message) {
        super(message);
    }

    public TextGenerationException(String message, Throwable cause) {
        super(message, cause);
    }
}
