package com.devanshbhosale.marginwatch.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.devanshbhosale.marginwatch.domain.Product;

public interface ProductRepository extends JpaRepository<Product, Long> {

    Optional<Product> findBySkuIgnoreCase(String sku);

    List<Product> findBySkuIn(Collection<String> skus);

    /**
     * Catalog search. A null or blank q / vendor / category means "no filter";
     * q is matched against SKU and name case-insensitively, vendor is the
     * vendor code.
     */
    @Query("""
            SELECT p FROM Product p
            WHERE (COALESCE(:q, '') = ''
                   OR LOWER(p.sku) LIKE LOWER(CONCAT('%', :q, '%'))
                   OR LOWER(p.name) LIKE LOWER(CONCAT('%', :q, '%')))
              AND (COALESCE(:vendor, '') = '' OR p.vendor.code = :vendor)
              AND (COALESCE(:category, '') = '' OR p.category = :category)
            ORDER BY p.sku, p.id
            """)
    Page<Product> search(@Param("q") String q,
                         @Param("vendor") String vendor,
                         @Param("category") String category,
                         Pageable pageable);

    @Query("SELECT DISTINCT p.category FROM Product p ORDER BY p.category")
    List<String> distinctCategories();
}
