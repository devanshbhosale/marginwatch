package com.devanshbhosale.marginwatch.repository;

import java.util.Collection;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.devanshbhosale.marginwatch.domain.PriceFile;
import com.devanshbhosale.marginwatch.domain.PriceFileStatus;

public interface PriceFileRepository extends JpaRepository<PriceFile, Long> {

    Page<PriceFile> findAllByOrderByUploadedAtDescIdDesc(Pageable pageable);

    List<PriceFile> findByStatusIn(Collection<PriceFileStatus> statuses);

    long countByUploadedAtAfter(java.time.LocalDateTime since);
}
