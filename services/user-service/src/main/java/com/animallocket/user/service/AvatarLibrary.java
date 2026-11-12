package com.animallocket.user.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Thư viện avatar (libs/avatar-lib/avatar-lib.vN.json, đóng vào classpath ở {@code avatar/}).
 * Server không vẽ: chỉ liệt kê tổ hợp hợp lệ để seed kho và tra tên con vật.
 * Khoá avatar = "animal:palette:accessory"; một khoá thuộc phiên bản đầu tiên có nó.
 */
@Component
public class AvatarLibrary {
    private static final Pattern FILE = Pattern.compile("avatar-lib\\.v(\\d+)\\.json$");

    /** Một tổ hợp avatar. {@code version} = phiên bản thư viện đầu tiên có tổ hợp này. */
    public record Avatar(int version, String animal, int palette, String accessory) {
        public String key() {
            return animal + ":" + palette + ":" + accessory;
        }

        /** Mã gửi cho client: "v:animal:palette:accessory". */
        public String code() {
            return version + ":" + key();
        }

        public static Avatar fromKey(int version, String key) {
            String[] p = key.split(":");
            if (p.length != 3) {
                throw new IllegalArgumentException("avatar_key không hợp lệ: " + key);
            }
            return new Avatar(version, p[0], Integer.parseInt(p[1]), p[2]);
        }
    }

    private final TreeMap<Integer, byte[]> raw = new TreeMap<>();
    /** khoá → phiên bản đầu tiên có nó, giữ thứ tự để seed ổn định. */
    private final Map<String, Integer> keys = new LinkedHashMap<>();
    private final Map<String, String> animalNames = new LinkedHashMap<>();

    @org.springframework.beans.factory.annotation.Autowired
    public AvatarLibrary(ObjectMapper json) throws IOException {
        this(json, new PathMatchingResourcePatternResolver().getResources("classpath*:avatar/avatar-lib.v*.json"));
    }

    AvatarLibrary(ObjectMapper json, Resource[] files) throws IOException {
        for (Resource r : files) {
            Matcher m = FILE.matcher(String.valueOf(r.getFilename()));
            if (!m.find()) {
                continue;
            }
            try (InputStream in = r.getInputStream()) {
                raw.put(Integer.parseInt(m.group(1)), in.readAllBytes());
            }
        }
        if (raw.isEmpty()) {
            throw new IllegalStateException("Không tìm thấy avatar-lib trên classpath (avatar/avatar-lib.vN.json)");
        }
        for (Map.Entry<Integer, byte[]> e : raw.entrySet()) {
            JsonNode lib = json.readTree(e.getValue());
            if (lib.path("version").asInt() != e.getKey()) {
                throw new IllegalStateException("avatar-lib v" + e.getKey() + " ghi sai trường version");
            }
            int palettes = lib.path("palettes").size();
            for (Iterator<Map.Entry<String, JsonNode>> it = lib.path("animals").fields(); it.hasNext(); ) {
                Map.Entry<String, JsonNode> a = it.next();
                animalNames.put(a.getKey(), a.getValue().path("name").asText(a.getKey()));
                for (int p = 0; p < palettes; p++) {
                    for (Iterator<String> acc = lib.path("accessories").fieldNames(); acc.hasNext(); ) {
                        keys.putIfAbsent(a.getKey() + ":" + p + ":" + acc.next(), e.getKey());
                    }
                }
            }
        }
    }

    public int latestVersion() {
        return raw.lastKey();
    }

    /** Mọi tổ hợp của mọi phiên bản: khoá → phiên bản đầu tiên. */
    public Map<String, Integer> allKeys() {
        return Collections.unmodifiableMap(keys);
    }

    public Optional<Avatar> find(String key) {
        Integer v = keys.get(key);
        return v == null ? Optional.empty() : Optional.of(Avatar.fromKey(v, key));
    }

    public String animalName(String animal) {
        return animalNames.getOrDefault(animal, animal);
    }

    public Optional<byte[]> rawJson(int version) {
        return Optional.ofNullable(raw.get(version));
    }
}
