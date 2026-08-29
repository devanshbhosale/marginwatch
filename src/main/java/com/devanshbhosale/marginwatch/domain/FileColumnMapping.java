package com.devanshbhosale.marginwatch.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * Per-vendor saved column mapping: a source header from the vendor's price
 * file points at one target field. The parse step refuses to run for a vendor
 * without SKU + COST_PRICE mapped.
 */
@Entity
@Table(name = "file_column_mappings",
        uniqueConstraints = @UniqueConstraint(columnNames = {"vendor_id", "source_header"}))
public class FileColumnMapping {

    public enum TargetField {
        SKU, DESCRIPTION, COST_PRICE
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "vendor_id", nullable = false)
    private Vendor vendor;

    /** Header text exactly as it appears in the vendor's file. */
    @Column(name = "source_header", nullable = false, length = 120)
    private String sourceHeader;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_field", nullable = false, length = 20)
    private TargetField targetField;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Vendor getVendor() {
        return vendor;
    }

    public void setVendor(Vendor vendor) {
        this.vendor = vendor;
    }

    public String getSourceHeader() {
        return sourceHeader;
    }

    public void setSourceHeader(String sourceHeader) {
        this.sourceHeader = sourceHeader;
    }

    public TargetField getTargetField() {
        return targetField;
    }

    public void setTargetField(TargetField targetField) {
        this.targetField = targetField;
    }
}
