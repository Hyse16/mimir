package com.mimir.blog;

public class BlogAssetNotFoundException extends RuntimeException {

    BlogAssetNotFoundException() {
        super("Blog image asset was not found.");
    }
}
