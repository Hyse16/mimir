package com.mimir.blog;

public class BlogAssetLimitExceededException extends RuntimeException {

    BlogAssetLimitExceededException() {
        super("A blog post can contain at most 20 images.");
    }
}
