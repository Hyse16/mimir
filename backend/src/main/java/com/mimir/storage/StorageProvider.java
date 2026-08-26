package com.mimir.storage;

public interface StorageProvider {

    void store(String key, byte[] content);

    byte[] read(String key);

    void delete(String key);
}
