package com.devanshbhosale.marginwatch.web;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.devanshbhosale.marginwatch.domain.AuditLog;
import com.devanshbhosale.marginwatch.domain.Product;
import com.devanshbhosale.marginwatch.domain.User;
import com.devanshbhosale.marginwatch.domain.UserRole;
import com.devanshbhosale.marginwatch.dto.MetaRes;
import com.devanshbhosale.marginwatch.dto.PageRes;
import com.devanshbhosale.marginwatch.dto.PriceReq;
import com.devanshbhosale.marginwatch.dto.ProductRes;
import com.devanshbhosale.marginwatch.exception.ApiException;
import com.devanshbhosale.marginwatch.repository.AuditLogRepository;
import com.devanshbhosale.marginwatch.repository.ProductRepository;
import com.devanshbhosale.marginwatch.repository.VendorRepository;

import jakarta.validation.Valid;

@RestController
public class ProductController {

    private final ProductRepository productRepository;
    private final VendorRepository vendorRepository;
    private final AuditLogRepository auditRepository;

    public ProductController(ProductRepository productRepository,
                             VendorRepository vendorRepository,
                             AuditLogRepository auditRepository) {
        this.productRepository = productRepository;
        this.vendorRepository = vendorRepository;
        this.auditRepository = auditRepository;
    }

    @GetMapping("/api/products")
    public PageRes<ProductRes> list(@RequestParam(required = false) String vendor,
                                    @RequestParam(required = false) String category,
                                    @RequestParam(required = false) String q,
                                    @RequestParam(defaultValue = "0") int page,
                                    @RequestParam(defaultValue = "20") int size) {
        int pageSize = Math.min(Math.max(size, 1), 100);
        var result = productRepository.search(blankToNull(q), blankToNull(vendor), blankToNull(category),
                PageRequest.of(Math.max(page, 0), pageSize));
        return PageRes.of(result, result.map(ProductController::toRes).getContent());
    }

    @GetMapping("/api/products/meta")
    public MetaRes meta() {
        return new MetaRes(productRepository.distinctCategories(), vendorRepository.allCodes());
    }

    /** ADMIN-only selling-price edit — the only way a price file never touches sellingPrice. */
    @PutMapping("/api/products/{id}/price")
    @Transactional
    public ProductRes updatePrice(@PathVariable Long id,
                                  @Valid @RequestBody PriceReq request,
                                  @AuthenticationPrincipal User actor) {
        if (actor.getRole() != UserRole.ADMIN) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Only ADMIN can edit selling prices");
        }
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ApiException(404, "Product not found"));
        BigDecimal old = product.getSellingPrice();
        product.setSellingPrice(request.sellingPrice());
        productRepository.save(product);

        AuditLog entry = new AuditLog();
        entry.setActorEmail(actor.getEmail());
        entry.setAction("PRICE_EDIT");
        entry.setEntityRef("PRODUCT:" + product.getId());
        entry.setDetails("sku=" + product.getSku() + "; selling price " + old + " → " + request.sellingPrice());
        entry.setCreatedAt(LocalDateTime.now(ZoneId.of("Asia/Kolkata")));
        auditRepository.save(entry);

        return toRes(product);
    }

    static ProductRes toRes(Product product) {
        return new ProductRes(product.getId(), product.getSku(), product.getVendor().getCode(),
                product.getCategory(), product.getName(), product.getCurrentCost(), product.getSellingPrice(),
                product.marginPct());
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
