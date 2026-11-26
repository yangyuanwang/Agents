package com.example.rag.service;

import com.example.rag.config.FileStorageProperties;
import org.springframework.stereotype.Component;
import java.io.IOException;
import java.nio.file.*;
import java.util.UUID;

@Component
public class LocalFileStorage {
    private final Path root;
    public LocalFileStorage(FileStorageProperties properties) throws IOException {
        root=properties.getStorageRoot().toAbsolutePath().normalize(); Files.createDirectories(root.resolve(".staging"));
    }
    public Path newStagingPath() { return root.resolve(".staging").resolve(UUID.randomUUID()+".part").normalize(); }
    public String newStorageKey(){String id=UUID.randomUUID().toString();return id.substring(0,2)+"/"+id;}
    public Path resolve(String key) throws IOException {
        Path result=root.resolve(key).normalize();
        if(!result.startsWith(root) || key.startsWith("/") || key.contains("\\") || key.contains("..")) throw new IOException("Invalid storage key");
        return result;
    }
    public void publish(Path staging,String key) throws IOException {
        Path dest=resolve(key);Files.createDirectories(dest.getParent());
        try { Files.move(staging,dest,StandardCopyOption.ATOMIC_MOVE); }
        catch(AtomicMoveNotSupportedException ex){Files.move(staging,dest);}
    }
    public void delete(String key) throws IOException { Files.deleteIfExists(resolve(key)); }
    public void deleteStaging(Path path) throws IOException { if(path!=null && path.normalize().startsWith(root.resolve(".staging"))) Files.deleteIfExists(path); }
}
