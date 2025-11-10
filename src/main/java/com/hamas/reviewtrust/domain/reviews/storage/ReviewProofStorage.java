package com.hamas.reviewtrust.domain.reviews.storage;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.UUID;

/**
 * レビュー投稿時の購入証明ファイルをローカルストレージに保存するための簡易ヘルパー。
 * 保存先は {@code ${storage.root}/uploads/review-proofs/<UUID>.<ext>}。
 */
@Component
public class ReviewProofStorage {

    private final Path root;

    public ReviewProofStorage(@Value("${storage.root:./var/storage}") String rootDir) throws IOException {
        this.root = Paths.get(rootDir).toAbsolutePath().normalize();
        Files.createDirectories(resolveProofDir());
    }

    /**
     * ファイルを保存し、ストレージルートからの相対パスを返す。
     */
    public String saveProof(MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            return null;
        }
        String filename = buildFileName(file.getOriginalFilename());
        Path target = resolveProofDir().resolve(filename).normalize();
        Files.createDirectories(target.getParent());
        try (InputStream in = file.getInputStream()) {
            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
        }
        return root.relativize(target).toString().replace('\\', '/');
    }

    private Path resolveProofDir() {
        return root.resolve(Paths.get("uploads", "review-proofs")).normalize();
    }

    private String buildFileName(String originalName) {
        String ext = "";
        if (originalName != null) {
            int dot = originalName.lastIndexOf('.');
            if (dot > -1 && dot < originalName.length() - 1) {
                ext = originalName.substring(dot).toLowerCase(Locale.ROOT);
            }
        }
        if (ext.isBlank()) {
            ext = ".bin";
        }
        return UUID.randomUUID() + ext;
    }
}
