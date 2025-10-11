// ProductRepository.java (placeholder)
package com.hamas.reviewtrust.domain.products.repo;

import com.hamas.reviewtrust.domain.products.entity.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.*;

public interface ProductRepository extends JpaRepository<Product, UUID> {

    Optional<Product> findByAsin(String asin);

    boolean existsByAsin(String asin);

    List<Product> findTop100ByVisibleTrueOrderByUpdatedAtDesc();

    @Query("""
        select p from Product p
        where (:visible is null or p.visible = :visible)
          and (:q is null or lower(p.title) like lower(concat('%', :q, '%'))
               or upper(p.asin) like upper(concat('%', :q, '%')))
        order by p.updatedAt desc
        """)
    List<Product> search(@Param("q") String q, @Param("visible") Boolean visible);
}
