package com.devanshbhosale.marginwatch.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.devanshbhosale.marginwatch.domain.MarginPolicy;

public interface MarginPolicyRepository extends JpaRepository<MarginPolicy, Long> {

    Optional<MarginPolicy> findByCategory(String category);

    /** The one global row (null category). */
    @Query("SELECT p FROM MarginPolicy p WHERE p.category IS NULL")
    Optional<MarginPolicy> findGlobal();

    List<MarginPolicy> findAllByOrderByIdAsc();
}
