package com.devanshbhosale.marginwatch.repository;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.devanshbhosale.marginwatch.domain.AuditLog;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    /**
     * actor matches case-insensitively as a substring; action is exact.
     * Null/blank means "no filter". Newest first, id as the tiebreak.
     */
    @Query("""
            SELECT a FROM AuditLog a
            WHERE (COALESCE(:actor, '') = '' OR LOWER(a.actorEmail) LIKE LOWER(CONCAT('%', :actor, '%')))
              AND (COALESCE(:action, '') = '' OR a.action = :action)
            ORDER BY a.createdAt DESC, a.id DESC
            """)
    Page<AuditLog> search(@Param("actor") String actor, @Param("action") String action, Pageable pageable);

    @Query("SELECT a FROM AuditLog a WHERE a.action = 'PRICE_APPLIED' AND a.createdAt >= :since")
    List<AuditLog> appliedSince(@Param("since") LocalDateTime since);
}
