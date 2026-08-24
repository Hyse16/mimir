package com.mimir.blog;

public class StaleDraftVersionException extends RuntimeException {

    StaleDraftVersionException() {
        super("The base draft version is not the currently selected version.");
    }
}
