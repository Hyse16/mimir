package com.mimir.storage;

public class StorageException extends RuntimeException {

    StorageException(String message, Throwable cause) {
        super(message, cause);
    }
}
