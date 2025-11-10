package com.hamas.reviewtrust.domain.storage;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.*;
import java.time.LocalDate;

/**
 * ローカルディスクに保存する実装。
 * application.yml: storage.root でルートディレクトリを指定可能。
 */
@Component
public class LocalFileStorage implements FileStorage {

    private final Path root;

    public LocalFileStorage(
            @Value("${storage.root:./var/storage}") String rootDir
    ) throws IOException {
        this.root = Paths.get(rootDir).toAbsolutePath().normalize();
        Files.createDirectories(this.root);
    }

    @Override
    public String save(InputStream in, String filename) throws IOException {
        // 保存先: uploads/YYYY/MM/ 直下に、重複しにくいファイル名で保存
        LocalDate today = LocalDate.now();
        Path dir = root.resolve(Paths.get("uploads",
                String.valueOf(today.getYear()),
                String.format("%02d", today.getMonthValue())));
        Files.createDirectories(dir);

        String base = (filename == null || filename.isBlank()) ? "file" : filename;
        String safe = base.replaceAll("[^A-Za-z0-9._-]", "_");
        Path target = uniquify(dir.resolve(safe));
        Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);

        // ルートからの相対パスを返す
        return root.relativize(target).toString().replace('\\', '/');
    }

    @Override
    public boolean delete(String relativePath) throws IOException {
        if (relativePath == null || relativePath.isBlank()) return false;
        Path p = resolve(relativePath);
        try {
            return Files.deleteIfExists(p);
        } catch (NoSuchFileException e) {
            return false;
        }
    }

    @Override
    public Path resolve(String relativePath) {
        Path p = root.resolve(relativePath).normalize();
        if (!p.startsWith(root)) {
            // ルート外へのパス・トラバーサル防止
            throw new IllegalArgumentException("Invalid path: " + relativePath);
        }
        return p;
    }

    private Path uniquify(Path path) throws IOException {
        if (!Files.exists(path)) return path;
        String name = path.getFileName().toString();
        String stem = name;
        String ext = "";
        int dot = name.lastIndexOf('.');
        if (dot > 0) {
            stem = name.substring(0, dot);
            ext = name.substring(dot);
        }
        for (int i = 1; i < 10_000; i++) {
            Path p = path.getParent().resolve(stem + "-" + i + ext);
            if (!Files.exists(p)) return p;
        }
        throw new IOException("Too many conflicting filenames: " + name);
    }
}
