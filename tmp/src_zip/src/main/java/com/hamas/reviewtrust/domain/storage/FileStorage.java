package com.hamas.reviewtrust.domain.storage;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;

/**
 * 単純なファイルストレージの抽象化。
 * 実装（LocalFileStorage など）で保存／削除／パス解決を提供する。
 */
public interface FileStorage {

    /**
     * 入力ストリームを保存し、ストレージ内の相対パス（文字列）を返す。
     * @param in       入力ストリーム（呼び出し側で close 済みでもOK）
     * @param filename 元ファイル名（拡張子含む想定）
     * @return ストレージ内の相対パス（例: uploads/2025/10/xxx.png）
     */
    String save(InputStream in, String filename) throws IOException;

    /**
     * 相対パスでファイルを削除する。
     * @param relativePath {@link #save} が返した相対パス
     * @return 削除できたら true（存在しなかった場合は false）
     */
    boolean delete(String relativePath) throws IOException;

    /**
     * 相対パスを実ファイルの {@link Path} に解決する。
     */
    Path resolve(String relativePath);
}
