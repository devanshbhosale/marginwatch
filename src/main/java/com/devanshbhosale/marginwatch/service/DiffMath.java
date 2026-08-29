package com.devanshbhosale.marginwatch.service;

import java.math.BigDecimal;
import java.math.RoundingMode;

import com.devanshbhosale.marginwatch.domain.MarginPolicy;
import com.devanshbhosale.marginwatch.repository.MarginPolicyRepository;
import org.springframework.stereotype.Service;

/**
 * Shared diff arithmetic and floor lookup, used by the parser, the apply
 * pipeline and the seed data so every number is rounded the same way.
 */
@Service
public class DiffMath {

    private final MarginPolicyRepository policyRepository;

    public DiffMath(MarginPolicyRepository policyRepository) {
        this.policyRepository = policyRepository;
    }

    /** (new − old) / old × 100; null when old is null or zero. */
    public static BigDecimal changePct(BigDecimal oldCost, BigDecimal newCost) {
        if (oldCost == null || oldCost.compareTo(BigDecimal.ZERO) == 0) {
            return null;
        }
        return newCost.subtract(oldCost)
                .multiply(BigDecimal.valueOf(100))
                .divide(oldCost, 2, RoundingMode.HALF_UP);
    }

    /** (selling − cost) / selling × 100; null when selling or cost is null, or selling is zero. */
    public static BigDecimal marginPct(BigDecimal sellingPrice, BigDecimal cost) {
        if (sellingPrice == null || sellingPrice.compareTo(BigDecimal.ZERO) == 0 || cost == null) {
            return null;
        }
        return sellingPrice.subtract(cost)
                .multiply(BigDecimal.valueOf(100))
                .divide(sellingPrice, 2, RoundingMode.HALF_UP);
    }

    /**
     * Floor for a product: the category policy when one exists, else the global
     * (null-category) policy; null when no policy exists at all.
     */
    public BigDecimal floorFor(String category) {
        if (category != null) {
            BigDecimal floor = policyRepository.findByCategory(category).map(MarginPolicy::getMinMarginPct).orElse(null);
            if (floor != null) {
                return floor;
            }
        }
        return policyRepository.findGlobal().map(MarginPolicy::getMinMarginPct).orElse(null);
    }
}
