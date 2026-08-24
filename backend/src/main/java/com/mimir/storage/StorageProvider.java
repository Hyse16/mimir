package com.mimir.storage;

public interface StorageProvider {

    void store(String key, byte[] content);

    void delete(String key);
}
