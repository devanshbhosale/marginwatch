package com.devanshbhosale.marginwatch.repository;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.devanshbhosale.marginwatch.domain.PriceFileRow;
import com.devanshbhosale.marginwatch.domain.RowFlag;
import com.devanshbhosale.marginwatch.domain.RowStatus;

public interface PriceFileRowRepository extends JpaRepository<PriceFileRow, Long> {

    Page<PriceFileRow> findByPriceFileId(Long priceFileId, Pageable pageable);

    Page<PriceFileRow> findByPriceFileIdAndFlag(Long priceFileId, RowFlag flag, Pageable pageable);

    Page<PriceFileRow> findByPriceFileIdAndStatus(Long priceFileId, RowStatus status, Pageable pageable);

    Page<PriceFileRow> findByPriceFileIdAndFlagAndStatus(Long priceFileId, RowFlag flag, RowStatus status,
            Pageable pageable);

    List<PriceFileRow> findByPriceFileIdOrderByRowNumberAsc(Long priceFileId);

    List<PriceFileRow> findByPriceFileIdAndStatus(Long priceFileId, RowStatus status);

    long countByPriceFileIdAndStatus(Long priceFileId, RowStatus status);

    long countByStatus(RowStatus status);

    long countByStatusAndFlag(RowStatus status, RowFlag flag);

    List<PriceFileRow> findByFlag(RowFlag flag);

    List<PriceFileRow> findByStatusAndFlag(RowStatus status, RowFlag flag);

    List<PriceFileRow> findByChangePctIsNotNull();

    /**
     * PENDING-only guard for apply/skip: the conditional update returns 0 when
     * another actor already moved the row, which callers turn into a 409.
     * clearAutomatically re-reads the row afterwards without stale state.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE PriceFileRow r SET r.status = :status WHERE r.id = :id AND r.status = :expected")
    int transitionStatus(@Param("id") Long id, @Param("expected") RowStatus expected,
            @Param("status") RowStatus status);

    @Query("SELECT r.flag, COUNT(r) FROM PriceFileRow r WHERE r.priceFile.id = :fileId GROUP BY r.flag")
    List<Object[]> countByFlagGrouped(@Param("fileId") Long fileId);

    @Query("SELECT r.status, COUNT(r) FROM PriceFileRow r WHERE r.priceFile.id = :fileId GROUP BY r.status")
    List<Object[]> countByStatusGrouped(@Param("fileId") Long fileId);
}
