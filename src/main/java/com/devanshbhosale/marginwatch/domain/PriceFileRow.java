package com.devanshbhosale.marginwatch.domain;

import java.math.BigDecimal;

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

/**
 * One parsed line of a price file with its diff outcome. old/new cost and the
 * derived percentages are frozen at parse time; the apply step moves the
 * product's current_cost to new_cost under a PENDING-only conditional update.
 */
@Entity
@Table(name = "price_file_rows")
public class PriceFileRow {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "price_file_id", nullable = false)
    private PriceFile priceFile;

    /** 1-based data-row number inside the file (header excluded). */
    @Column(name = "row_number", nullable = false)
    private int rowNumber;

    @Column(length = 64)
    private String sku;

    @Column(name = "product_name", length = 200)
    private String productName;

    @Column(name = "old_cost", precision = 14, scale = 2)
    private BigDecimal oldCost;

    @Column(name = "new_cost", precision = 14, scale = 2)
    private BigDecimal newCost;

    @Column(name = "change_pct", precision = 7, scale = 2)
    private BigDecimal changePct;

    @Column(name = "resulting_margin_pct", precision = 7, scale = 2)
    private BigDecimal resultingMarginPct;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RowFlag flag;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RowStatus status = RowStatus.PENDING;

    /** The raw cells joined, so operators can see exactly what was ingested. */
    @Column(name = "raw_row", nullable = false, columnDefinition = "text")
    private String rawRow;

    /** Why a row failed resolution (NOT_FOUND) — shown in the diff preview. */
    @Column(length = 200)
    private String note;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public PriceFile getPriceFile() {
        return priceFile;
    }

    public void setPriceFile(PriceFile priceFile) {
        this.priceFile = priceFile;
    }

    public int getRowNumber() {
        return rowNumber;
    }

    public void setRowNumber(int rowNumber) {
        this.rowNumber = rowNumber;
    }

    public String getSku() {
        return sku;
    }

    public void setSku(String sku) {
        this.sku = sku;
    }

    public String getProductName() {
        return productName;
    }

    public void setProductName(String productName) {
        this.productName = productName;
    }

    public BigDecimal getOldCost() {
        return oldCost;
    }

    public void setOldCost(BigDecimal oldCost) {
        this.oldCost = oldCost;
    }

    public BigDecimal getNewCost() {
        return newCost;
    }

    public void setNewCost(BigDecimal newCost) {
        this.newCost = newCost;
    }

    public BigDecimal getChangePct() {
        return changePct;
    }

    public void setChangePct(BigDecimal changePct) {
        this.changePct = changePct;
    }

    public BigDecimal getResultingMarginPct() {
        return resultingMarginPct;
    }

    public void setResultingMarginPct(BigDecimal resultingMarginPct) {
        this.resultingMarginPct = resultingMarginPct;
    }

    public RowFlag getFlag() {
        return flag;
    }

    public void setFlag(RowFlag flag) {
        this.flag = flag;
    }

    public RowStatus getStatus() {
        return status;
    }

    public void setStatus(RowStatus status) {
        this.status = status;
    }

    public String getRawRow() {
        return rawRow;
    }

    public void setRawRow(String rawRow) {
        this.rawRow = rawRow;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }
}
