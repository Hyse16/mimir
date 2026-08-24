package com.mimir.blog;

public class InvalidBlogAssetException extends RuntimeException {

    InvalidBlogAssetException(String message) {
        super(message);
    }
}
