package com.mimir.storage;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class LocalStorageProvider implements StorageProvider {

    private final Path root;

    public LocalStorageProvider(@Value("${mimir.storage.local-root:./data/assets}") String root) {
        this.root = Path.of(root).toAbsolutePath().normalize();
    }

    @Override
    public void store(String key, byte[] content) {
        Path target = target(key);
        Path temporary = null;
        try {
            Files.createDirectories(target.getParent());
            temporary = Files.createTempFile(target.getParent(), "upload-", ".tmp");
            Files.write(temporary, content);
            moveIntoPlace(temporary, target);
        } catch (IOException error) {
            throw new StorageException("Unable to store image asset.", error);
        } finally {
            if (temporary != null) {
                try {
                    Files.deleteIfExists(temporary);
                } catch (IOException ignored) {
                    // The completed target or original storage error remains authoritative.
                }
            }
        }
    }

    @Override
    public void delete(String key) {
        try {
            Files.deleteIfExists(target(key));
        } catch (IOException error) {
            throw new StorageException("Unable to delete image asset.", error);
        }
    }

    private Path target(String key) {
        Path target = root.resolve(key).normalize();
        if (!target.startsWith(root)) {
            throw new IllegalArgumentException("Storage key escapes the configured root.");
        }
        return target;
    }

    private void moveIntoPlace(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
