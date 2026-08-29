package com.devanshbhosale.marginwatch.domain;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * Margin floor policy. One row with a null category is the global floor;
 * category rows override it for that category only.
 */
@Entity
@Table(name = "margin_policies",
        uniqueConstraints = @UniqueConstraint(columnNames = {"category"}))
public class MarginPolicy {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Null = global floor. */
    @Column(length = 60)
    private String category;

    @Column(name = "min_margin_pct", nullable = false, precision = 5, scale = 2)
    private BigDecimal minMarginPct;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public BigDecimal getMinMarginPct() {
        return minMarginPct;
    }

    public void setMinMarginPct(BigDecimal minMarginPct) {
        this.minMarginPct = minMarginPct;
    }
}
