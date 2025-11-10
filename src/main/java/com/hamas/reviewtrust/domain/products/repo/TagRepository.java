package com.hamas.reviewtrust.domain.products.repo;

import com.hamas.reviewtrust.domain.products.entity.Tag;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** カテゴリタグの基本リポジトリ。 */
public interface TagRepository extends JpaRepository<Tag, UUID> {

    Optional<Tag> findByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCase(String name);

    List<Tag> findAllByOrderByNameAsc();
}

