package com.devanshbhosale.marginwatch.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.devanshbhosale.marginwatch.domain.FileColumnMapping;

public interface FileColumnMappingRepository extends JpaRepository<FileColumnMapping, Long> {

    List<FileColumnMapping> findByVendorIdOrderByIdAsc(Long vendorId);

    List<FileColumnMapping> findByVendorId(Long vendorId);
}
