package com.mimir.blog;

import java.util.UUID;

public class BlogNotFoundException extends RuntimeException {

    BlogNotFoundException(UUID postId) {
        super("Blog post not found: " + postId);
    }
}
