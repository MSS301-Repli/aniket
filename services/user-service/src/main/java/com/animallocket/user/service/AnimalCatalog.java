package com.animallocket.user.service;

import com.animallocket.user.domain.Animal;
import com.animallocket.user.repo.AnimalRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.List;
import java.util.Random;
import java.util.Set;

/** Danh mục con vật. Nạp idempotent khi khởi động (thêm con mới chỉ cần thêm vào danh sách). */
@Component
public class AnimalCatalog implements ApplicationRunner {
    static final List<Animal> SEED = List.of(
            new Animal("cat", "Mèo", Animal.Rarity.COMMON, null),
            new Animal("dog", "Chó", Animal.Rarity.COMMON, null),
            new Animal("rabbit", "Thỏ", Animal.Rarity.COMMON, null),
            new Animal("hamster", "Chuột hamster", Animal.Rarity.COMMON, null),
            new Animal("duck", "Vịt", Animal.Rarity.COMMON, null),
            new Animal("pig", "Heo", Animal.Rarity.COMMON, null),
            new Animal("cow", "Bò", Animal.Rarity.COMMON, null),
            new Animal("chicken", "Gà", Animal.Rarity.COMMON, null),
            new Animal("fox", "Cáo", Animal.Rarity.RARE, null),
            new Animal("panda", "Gấu trúc", Animal.Rarity.RARE, null),
            new Animal("koala", "Koala", Animal.Rarity.RARE, null),
            new Animal("penguin", "Chim cánh cụt", Animal.Rarity.RARE, null),
            new Animal("owl", "Cú", Animal.Rarity.RARE, null),
            new Animal("frog", "Ếch", Animal.Rarity.RARE, null),
            new Animal("lion", "Sư tử", Animal.Rarity.EPIC, null),
            new Animal("tiger", "Hổ", Animal.Rarity.EPIC, null),
            new Animal("wolf", "Sói", Animal.Rarity.EPIC, null),
            new Animal("elephant", "Voi", Animal.Rarity.RARE, "SAFARI"),
            new Animal("giraffe", "Hươu cao cổ", Animal.Rarity.RARE, "SAFARI"),
            new Animal("zebra", "Ngựa vằn", Animal.Rarity.RARE, "SAFARI"),
            new Animal("hippo", "Hà mã", Animal.Rarity.EPIC, "SAFARI"),
            new Animal("whale", "Cá voi", Animal.Rarity.EPIC, "OCEAN"),
            new Animal("octopus", "Bạch tuộc", Animal.Rarity.RARE, "OCEAN"),
            new Animal("turtle", "Rùa", Animal.Rarity.RARE, "OCEAN"),
            new Animal("shark", "Cá mập", Animal.Rarity.EPIC, "OCEAN"));

    private final AnimalRepository animals;
    private final Random random;

    public AnimalCatalog(AnimalRepository animals) {
        this.animals = animals;
        this.random = new SecureRandom();
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        Set<String> existing = new java.util.HashSet<>();
        animals.findAll().forEach(a -> existing.add(a.getCode()));
        SEED.stream().filter(a -> !existing.contains(a.getCode())).forEach(animals::save);
    }

    /** Con vật đầu tiên khi đăng ký: chỉ từ nhóm COMMON của bộ cơ bản. */
    public Animal randomStarter() {
        List<Animal> commons = animals.findByPackCodeIsNullAndRarity(Animal.Rarity.COMMON);
        if (commons.isEmpty()) {
            throw new IllegalStateException("Danh mục con vật trống");
        }
        return commons.get(random.nextInt(commons.size()));
    }

    /** Quay thưởng: chọn theo trọng số độ hiếm trong bộ cơ bản, loại con đã có. Rỗng nếu đã đủ bộ. */
    public java.util.Optional<Animal> spin(Set<String> owned) {
        List<Animal> pool = animals.findByPackCodeIsNull().stream()
                .filter(a -> !owned.contains(a.getCode())).toList();
        return pick(pool, random);
    }

    static java.util.Optional<Animal> pick(List<Animal> pool, Random random) {
        if (pool.isEmpty()) {
            return java.util.Optional.empty();
        }
        int total = pool.stream().mapToInt(a -> weight(a.getRarity())).sum();
        int roll = random.nextInt(total);
        for (Animal a : pool) {
            roll -= weight(a.getRarity());
            if (roll < 0) {
                return java.util.Optional.of(a);
            }
        }
        return java.util.Optional.of(pool.get(pool.size() - 1));
    }

    static int weight(Animal.Rarity r) {
        return switch (r) {
            case COMMON -> 70;
            case RARE -> 25;
            case EPIC -> 5;
        };
    }
}
