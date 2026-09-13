package com.example.clashroyaleapi.nameindex;

import com.example.clashroyaleapi.config.NameIndexProperties;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * プレイヤー名/クランメンバー名からタグを検索できるようにするための簡易インデックス。
 * Clash Royale公式APIには名前検索エンドポイントが存在しないため、
 * これまでにタグ検索で判明した「名前→タグ」の対応をテキストファイル(JSON)に蓄積して代用する。
 */
@Service
public class NameIndexService {

    private static final Logger log = LoggerFactory.getLogger(NameIndexService.class);

    private final Path filePath;
    private final ObjectMapper objectMapper;
    private final Map<String, String> tagToName = new ConcurrentHashMap<>();
    private final Object writeLock = new Object();

    public NameIndexService(NameIndexProperties properties, ObjectMapper objectMapper) {
        String configuredPath = (properties.file() == null || properties.file().isBlank())
                ? "data/name-index.json"
                : properties.file();
        this.filePath = Path.of(configuredPath);
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    void load() {
        if (!Files.exists(filePath)) {
            return;
        }
        try {
            List<NameIndexEntry> entries = objectMapper.readValue(
                    filePath.toFile(), new TypeReference<List<NameIndexEntry>>() {
                    });
            entries.forEach(e -> tagToName.put(e.tag(), e.name()));
            log.info("名前インデックスを読み込みました({}件): {}", tagToName.size(), filePath.toAbsolutePath());
        } catch (IOException e) {
            log.warn("名前インデックスファイルの読み込みに失敗しました: {}", filePath.toAbsolutePath(), e);
        }
    }

    public void register(String tag, String name) {
        if (tag == null || tag.isBlank() || name == null || name.isBlank()) {
            return;
        }
        String normalizedTag = normalizeTag(tag);
        String previous = tagToName.put(normalizedTag, name);
        if (!name.equals(previous)) {
            persist();
        }
    }

    public List<NameIndexEntry> search(String nameQuery) {
        String needle = nameQuery.toLowerCase(Locale.ROOT);
        return tagToName.entrySet().stream()
                .filter(e -> e.getValue().toLowerCase(Locale.ROOT).contains(needle))
                .map(e -> new NameIndexEntry(e.getKey(), e.getValue()))
                .sorted(Comparator.comparing(NameIndexEntry::name, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    private String normalizeTag(String tag) {
        String upper = tag.trim().toUpperCase(Locale.ROOT);
        return upper.startsWith("#") ? upper : "#" + upper;
    }

    private void persist() {
        synchronized (writeLock) {
            try {
                Path parent = filePath.toAbsolutePath().getParent();
                if (parent != null) {
                    Files.createDirectories(parent);
                }
                List<NameIndexEntry> entries = tagToName.entrySet().stream()
                        .map(e -> new NameIndexEntry(e.getKey(), e.getValue()))
                        .toList();
                Path tmpFile = Files.createTempFile(
                        filePath.toAbsolutePath().getParent(), "name-index", ".json.tmp");
                objectMapper.writerWithDefaultPrettyPrinter().writeValue(tmpFile.toFile(), entries);
                Files.move(tmpFile, filePath, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                log.warn("名前インデックスファイルの保存に失敗しました: {}", filePath.toAbsolutePath(), e);
            }
        }
    }
}
