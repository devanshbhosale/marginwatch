package com.devanshbhosale.marginwatch.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.devanshbhosale.marginwatch.domain.Vendor;

public interface VendorRepository extends JpaRepository<Vendor, Long> {

    Optional<Vendor> findByCode(String code);

    @Query("SELECT v.code FROM Vendor v ORDER BY v.code")
    List<String> allCodes();
}
