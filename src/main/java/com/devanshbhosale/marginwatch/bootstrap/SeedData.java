package com.devanshbhosale.marginwatch.bootstrap;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.devanshbhosale.marginwatch.domain.AuditLog;
import com.devanshbhosale.marginwatch.domain.FileColumnMapping;
import com.devanshbhosale.marginwatch.domain.FileColumnMapping.TargetField;
import com.devanshbhosale.marginwatch.domain.MarginPolicy;
import com.devanshbhosale.marginwatch.domain.PriceFile;
import com.devanshbhosale.marginwatch.domain.PriceFileRow;
import com.devanshbhosale.marginwatch.domain.PriceFileStatus;
import com.devanshbhosale.marginwatch.domain.Product;
import com.devanshbhosale.marginwatch.domain.RowFlag;
import com.devanshbhosale.marginwatch.domain.RowStatus;
import com.devanshbhosale.marginwatch.domain.User;
import com.devanshbhosale.marginwatch.domain.UserRole;
import com.devanshbhosale.marginwatch.domain.Vendor;
import com.devanshbhosale.marginwatch.repository.AuditLogRepository;
import com.devanshbhosale.marginwatch.repository.FileColumnMappingRepository;
import com.devanshbhosale.marginwatch.repository.MarginPolicyRepository;
import com.devanshbhosale.marginwatch.repository.PriceFileRepository;
import com.devanshbhosale.marginwatch.repository.PriceFileRowRepository;
import com.devanshbhosale.marginwatch.repository.ProductRepository;
import com.devanshbhosale.marginwatch.repository.UserRepository;
import com.devanshbhosale.marginwatch.repository.VendorRepository;
import com.devanshbhosale.marginwatch.service.DiffMath;

/**
 * One-time seed for an empty database (users table empty): six accounts, four
 * vendors, a 63-SKU INR catalog, margin policies, column mappings (LENOVO
 * deliberately left unmapped — the mapping gate 422 is part of the demo), two
 * historical PARSED files whose rows are APPLIED/SKIPPED with the audit trail
 * to match, and one PENDING file showing every diff flag at once.
 */
@Component
public class SeedData implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SeedData.class);

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    private static final LocalDateTime START = LocalDateTime.now(IST);

    private static final String OVERRIDE_REASON = "Vendor contractual price hike — approved by Finance";

    /** sku, name, vendor code, category, current cost, selling price. */
    private static final String[][] PRODUCTS = {
            // CISCO — Networking
            {"C9200L-24P-4G", "Catalyst 9200L 24-Port PoE+ 4x1G Switch", "CISCO", "Networking", "298000.00", "348000.00"},
            {"C9200L-48P-4G", "Catalyst 9200L 48-Port PoE+ 4x1G Switch", "CISCO", "Networking", "452000.00", "525000.00"},
            {"C9300L-24T-4G-E", "Catalyst 9300L 24-Port Data 4x1G Switch", "CISCO", "Networking", "512000.00", "595000.00"},
            {"C1000-24T", "Catalyst 1000 24-Port Gigabit Switch", "CISCO", "Networking", "74500.00", "86000.00"},
            {"C1000-48T", "Catalyst 1000 48-Port Gigabit Switch", "CISCO", "Networking", "118500.00", "136000.00"},
            {"C1000-24FP-4G-L", "Catalyst 1000 24-Port PoE Switch", "CISCO", "Networking", "89500.00", "103000.00"},
            {"C1000-8FP-2G-L", "Catalyst 1000 8-Port PoE Switch", "CISCO", "Networking", "21800.00", "25600.00"},
            {"CBS350-24P-4G", "Business 350 24-Port PoE Gigabit Switch", "CISCO", "Networking", "34800.00", "41200.00"},
            {"SG350X-24", "SG350X 24-Port Gigabit Managed Switch", "CISCO", "Networking", "42600.00", "49800.00"},
            {"MR46", "Meraki MR46 Wi-Fi 6 Access Point", "CISCO", "Networking", "58200.00", "67500.00"},
            {"MR36", "Meraki MR36 Wi-Fi 6 Access Point", "CISCO", "Networking", "31200.00", "36500.00"},
            {"C9120AXI-B", "Catalyst 9120AXI Access Point", "CISCO", "Networking", "46800.00", "53200.00"},
            {"C9115AXI-B", "Catalyst 9115AXI Access Point", "CISCO", "Networking", "39800.00", "45400.00"},
            {"C1111-4P", "ISR 1111-4P Integrated Services Router", "CISCO", "Networking", "38900.00", "45000.00"},
            {"C1121-8P", "ISR 1121-8P Integrated Services Router", "CISCO", "Networking", "62300.00", "72000.00"},
            {"AIR-AP1832I-B-K9", "Aironet 1832i Dual-Band Access Point", "CISCO", "Networking", "24500.00", "28900.00"},
            {"SFP-10G-SR", "10GBASE-SR SFP+ Module", "CISCO", "Networking", "8450.00", "9900.00"},
            {"CAB-10G-SFPP", "10GBASE-CU SFP+ Passive Cable 3m", "CISCO", "Networking", "10400.00", "12500.00"},
            // HP — Laptops
            {"7Y0K2PA", "ProBook 440 G10 Notebook PC i5", "HP", "Laptops", "58400.00", "67200.00"},
            {"7Y0Z6PA", "EliteBook 840 G10 Notebook PC i7", "HP", "Laptops", "118000.00", "134500.00"},
            {"7Y1K9PA", "250 G10 Laptop i5 13th Gen", "HP", "Laptops", "38600.00", "44900.00"},
            {"7Y4L6PA", "ProBook 450 G10 Notebook PC i7", "HP", "Laptops", "62800.00", "72400.00"},
            {"7Y2T8PA", "ZBook Firefly 14 G10 Mobile Workstation", "HP", "Laptops", "142000.00", "162000.00"},
            {"7Y6P1PA", "Spectre x360 14 Laptop i7", "HP", "Laptops", "128500.00", "148000.00"},
            // HP — Printers (M404dn cost reflects the overridden breach applied on Aug 25)
            {"2Z616F", "LaserJet M141w Multifunction Printer", "HP", "Printers", "11800.00", "13900.00"},
            {"W1A53A", "LaserJet Pro M404dn Printer", "HP", "Printers", "29400.00", "31200.00"},
            {"1MR78B", "OfficeJet Pro 9020 All-in-One Printer", "HP", "Printers", "22400.00", "26400.00"},
            {"6GW99F", "LaserJet MFP M234sdw Printer", "HP", "Printers", "19800.00", "23200.00"},
            {"6NN97B", "DeskJet 2720e All-in-One Printer", "HP", "Printers", "6400.00", "7800.00"},
            // HP — Desktops
            {"6G8L7PA", "ProDesk 400 G9 Microtower Desktop", "HP", "Desktops", "44500.00", "51800.00"},
            {"7DZ54PA", "EliteDesk 800 G9 Tower Desktop", "HP", "Desktops", "68200.00", "78900.00"},
            {"7DZ75PA", "ProOne 600 G9 All-in-One Desktop", "HP", "Desktops", "58400.00", "67800.00"},
            {"7DF2LPA", "Z2 Tower G9 Workstation", "HP", "Desktops", "96500.00", "111500.00"},
            {"7DZ12PA", "Pro Mini 400 G9 Desktop PC", "HP", "Desktops", "39800.00", "46400.00"},
            // LENOVO — Laptops
            {"21E3004WIN", "ThinkPad E14 Gen 5 Laptop i5", "LENOVO", "Laptops", "56200.00", "65400.00"},
            {"21JCC00WIN", "ThinkBook 15 G6 IML Laptop i5", "LENOVO", "Laptops", "47800.00", "55800.00"},
            {"82XQ00BIN", "IdeaPad Slim 3 Laptop i5", "LENOVO", "Laptops", "32400.00", "38400.00"},
            {"21L5000WIN", "ThinkPad L14 Gen 4 Laptop i5", "LENOVO", "Laptops", "64500.00", "74500.00"},
            {"82YX00BVIN", "V14 G4 Laptop i3", "LENOVO", "Laptops", "28900.00", "34200.00"},
            {"21HD001WIN", "ThinkPad T14 Gen 4 Laptop i7", "LENOVO", "Laptops", "108500.00", "124000.00"},
            {"82Y7002WIN", "Yoga Slim 7 Laptop i5", "LENOVO", "Laptops", "78500.00", "91200.00"},
            // LENOVO — Desktops
            {"11SCS0AA00", "ThinkCentre Neo 50t Desktop i5", "LENOVO", "Desktops", "38600.00", "45200.00"},
            {"11T3002WIN", "ThinkCentre M70q Gen 4 Desktop", "LENOVO", "Desktops", "47800.00", "55600.00"},
            {"F0G7002WIN", "IdeaCentre AIO 3 Desktop 24\"", "LENOVO", "Desktops", "42500.00", "49800.00"},
            {"30K1001WIN", "ThinkStation P3 Tower Workstation", "LENOVO", "Desktops", "118000.00", "135000.00"},
            {"11EF002WIN", "V50t-13IMB Desktop i5", "LENOVO", "Desktops", "29800.00", "35400.00"},
            {"11MX001WIN", "ThinkCentre M90q Gen 4 Desktop", "LENOVO", "Desktops", "78600.00", "90200.00"},
            {"90GX00RWIN", "IdeaCentre Mini 5i Desktop", "LENOVO", "Desktops", "45200.00", "52800.00"},
            // INTEL — Processors
            {"BX8071514400", "Core i5-14400 14-Core Processor", "INTEL", "Processors", "14200.00", "16800.00"},
            {"BX8071514700K", "Core i7-14700K Unlocked Processor", "INTEL", "Processors", "32800.00", "38400.00"},
            {"BX8071514900K", "Core i9-14900K Unlocked Processor", "INTEL", "Processors", "48600.00", "56800.00"},
            {"BX8071512400", "Core i5-12400 12-Core Processor", "INTEL", "Processors", "10800.00", "12800.00"},
            {"BX8071514600K", "Core i5-14600K Unlocked Processor", "INTEL", "Processors", "21800.00", "25400.00"},
            {"BX8070112100", "Core i3-12100 Quad-Core Processor", "INTEL", "Processors", "7200.00", "8800.00"},
            {"CD8068904659401", "Xeon Silver 4410Y Processor", "INTEL", "Processors", "88500.00", "102500.00"},
            {"CD8068904670901", "Xeon Gold 5416S Processor", "INTEL", "Processors", "128500.00", "148000.00"},
            // INTEL — Storage
            {"SSDSC10KB960", "D3-S4510 960GB SATA SSD", "INTEL", "Storage", "8900.00", "10600.00"},
            {"SSDSC2KB480G8", "S4520 480GB SATA SSD", "INTEL", "Storage", "4900.00", "5900.00"},
            {"SSDSC2KB240G8", "S4510 240GB SATA SSD", "INTEL", "Storage", "2900.00", "3500.00"},
            {"SSDSC2KB960G8", "S4510 960GB SATA SSD", "INTEL", "Storage", "9200.00", "10800.00"},
            {"SSDSC2KB076T8", "S4510 7.68TB SATA SSD", "INTEL", "Storage", "68500.00", "79500.00"},
            {"SSDP5520U38T", "P5520U 3.84TB U.2 NVMe SSD", "INTEL", "Storage", "128000.00", "148000.00"},
            {"SSDP5520X16T", "D7-P5520 1.6TB NVMe SSD", "INTEL", "Storage", "62800.00", "72400.00"},
    };

    /** sku in PRODUCTS, applied old cost, minutes-after-upload for the Aug 19 Cisco file. */
    private static final Object[][] CISCO_AUG19_ROWS = {
            {"C9200L-24P-4G", "290550.00", 8},
            {"C1000-24T", "72600.00", 10},
            {"MR46", "56800.00", 12},
            {"C1111-4P", "38000.00", 14},
            {"C9300L-24T-4G-E", "499000.00", 16},
            {"CBS350-24P-4G", "34000.00", 18},
            {"C1000-8FP-2G-L", "21300.00", 20},
            {"MR36", "30400.00", 22},
            {"SFP-10G-SR", "8250.00", 24},
            {"C1121-8P", "60800.00", 26},
            {"AIR-AP1832I-B-K9", "23900.00", 28},
            {"SG350X-24", "41600.00", 30},
    };

    private final UserRepository userRepository;
    private final VendorRepository vendorRepository;
    private final ProductRepository productRepository;
    private final FileColumnMappingRepository mappingRepository;
    private final MarginPolicyRepository policyRepository;
    private final PriceFileRepository fileRepository;
    private final PriceFileRowRepository rowRepository;
    private final AuditLogRepository auditRepository;
    private final PasswordEncoder passwordEncoder;
    private final DiffMath diffMath;
    private final String adminPassword;

    public SeedData(UserRepository userRepository,
                    VendorRepository vendorRepository,
                    ProductRepository productRepository,
                    FileColumnMappingRepository mappingRepository,
                    MarginPolicyRepository policyRepository,
                    PriceFileRepository fileRepository,
                    PriceFileRowRepository rowRepository,
                    AuditLogRepository auditRepository,
                    PasswordEncoder passwordEncoder,
                    DiffMath diffMath,
                    @Value("${app.seed.admin-password}") String adminPassword) {
        this.userRepository = userRepository;
        this.vendorRepository = vendorRepository;
        this.productRepository = productRepository;
        this.mappingRepository = mappingRepository;
        this.policyRepository = policyRepository;
        this.fileRepository = fileRepository;
        this.rowRepository = rowRepository;
        this.auditRepository = auditRepository;
        this.passwordEncoder = passwordEncoder;
        this.diffMath = diffMath;
        this.adminPassword = adminPassword;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (userRepository.count() > 0) {
            return;
        }
        Map<String, User> users = seedUsers();
        Map<String, Vendor> vendors = seedVendors();
        List<MarginPolicy> policies = seedPolicies();
        seedMappings(vendors);
        Map<String, Product> products = seedProducts(vendors);
        seedFiles(users, vendors, products, policies);
        log.info("MarginWatch seed complete: {} users, {} vendors, {} products, {} price files, {} audit rows",
                userRepository.count(), vendorRepository.count(), productRepository.count(),
                fileRepository.count(), auditRepository.count());
    }

    private Map<String, User> seedUsers() {
        String adminHash = passwordEncoder.encode(adminPassword);
        String analystHash = passwordEncoder.encode("Analyst@Local2026");
        String viewerHash = passwordEncoder.encode("Viewer@Local2026");
        String demoAdminHash = passwordEncoder.encode("Demo@Admin2026");
        String demoAnalystHash = passwordEncoder.encode("Demo@Analyst2026");
        String demoViewerHash = passwordEncoder.encode("Demo@Viewer2026");

        Map<String, User> byKey = new LinkedHashMap<>();
        byKey.put("admin", userRepository.save(
                user("priya.nair@marginwatch.in", adminHash, "Priya Nair", UserRole.ADMIN, false)));
        byKey.put("analyst", userRepository.save(
                user("rahul.mehta@marginwatch.in", analystHash, "Rahul Mehta", UserRole.ANALYST, false)));
        byKey.put("viewer", userRepository.save(
                user("sneha.rao@marginwatch.in", viewerHash, "Sneha Rao", UserRole.VIEWER, false)));
        byKey.put("demoAdmin", userRepository.save(
                user("admin.demo@marginwatch.in", demoAdminHash, "Demo Admin", UserRole.ADMIN, true)));
        byKey.put("demoAnalyst", userRepository.save(
                user("analyst.demo@marginwatch.in", demoAnalystHash, "Demo Analyst", UserRole.ANALYST, true)));
        byKey.put("demoViewer", userRepository.save(
                user("viewer.demo@marginwatch.in", demoViewerHash, "Demo Viewer", UserRole.VIEWER, true)));
        return byKey;
    }

    private Map<String, Vendor> seedVendors() {
        Map<String, Vendor> byCode = new LinkedHashMap<>();
        byCode.put("CISCO", vendorRepository.save(vendor("CISCO", "Cisco Systems")));
        byCode.put("HP", vendorRepository.save(vendor("HP", "HP Inc.")));
        byCode.put("LENOVO", vendorRepository.save(vendor("LENOVO", "Lenovo India")));
        byCode.put("INTEL", vendorRepository.save(vendor("INTEL", "Intel India")));
        return byCode;
    }

    private List<MarginPolicy> seedPolicies() {
        List<MarginPolicy> saved = new ArrayList<>();
        MarginPolicy global = new MarginPolicy();
        global.setCategory(null);
        global.setMinMarginPct(new BigDecimal("8.00"));
        saved.add(policyRepository.save(global));
        MarginPolicy networking = new MarginPolicy();
        networking.setCategory("Networking");
        networking.setMinMarginPct(new BigDecimal("12.00"));
        saved.add(policyRepository.save(networking));
        MarginPolicy laptops = new MarginPolicy();
        laptops.setCategory("Laptops");
        laptops.setMinMarginPct(new BigDecimal("10.00"));
        saved.add(policyRepository.save(laptops));
        return saved;
    }

    private void seedMappings(Map<String, Vendor> vendors) {
        mapping(vendors.get("CISCO"), "SKU", TargetField.SKU);
        mapping(vendors.get("CISCO"), "Description", TargetField.DESCRIPTION);
        mapping(vendors.get("CISCO"), "Cost Price", TargetField.COST_PRICE);
        mapping(vendors.get("HP"), "HP SKU", TargetField.SKU);
        mapping(vendors.get("HP"), "Product Name", TargetField.DESCRIPTION);
        mapping(vendors.get("HP"), "Partner Cost", TargetField.COST_PRICE);
        mapping(vendors.get("INTEL"), "Product Code", TargetField.SKU);
        mapping(vendors.get("INTEL"), "Item Description", TargetField.DESCRIPTION);
        mapping(vendors.get("INTEL"), "Unit Price INR", TargetField.COST_PRICE);
        // LENOVO deliberately left unmapped: the upload mapping gate (422) is part of the demo.
    }

    private Map<String, Product> seedProducts(Map<String, Vendor> vendors) {
        Map<String, Product> bySku = new LinkedHashMap<>();
        for (String[] row : PRODUCTS) {
            Product product = new Product();
            product.setSku(row[0]);
            product.setName(row[1]);
            product.setVendor(vendors.get(row[2]));
            product.setCategory(row[3]);
            product.setCurrentCost(new BigDecimal(row[4]));
            product.setSellingPrice(new BigDecimal(row[5]));
            bySku.put(row[0], productRepository.save(product));
        }
        return bySku;
    }

    private void seedFiles(Map<String, User> users, Map<String, Vendor> vendors,
                           Map<String, Product> products, List<MarginPolicy> policies) {
        User analyst = users.get("analyst");
        User demoAdmin = users.get("demoAdmin");
        User demoAnalyst = users.get("demoAnalyst");

        // ---- Historical file 1: CISCO csv, 10 days ago, 12 OK rows all applied by the analyst.
        LocalDateTime uploaded1 = START.minusDays(10).withHour(11).withMinute(20).withSecond(0).withNano(0);
        PriceFile file1 = priceFile(vendors.get("CISCO"), "cisco-price-list-aug19.csv", PriceFileStatus.PARSED,
                uploaded1, analyst, 12, 12, 0, uploaded1.plusMinutes(3));
        uploadedAudit(analyst, file1, uploaded1);
        int n1 = 1;
        for (Object[] plan : CISCO_AUG19_ROWS) {
            String sku = (String) plan[0];
            BigDecimal oldCost = new BigDecimal((String) plan[1]);
            Product product = products.get(sku);
            PriceFileRow row = resolvedRow(file1, n1++, sku, product.getName(), oldCost, product.getCurrentCost(),
                    product.getSellingPrice(), product.getCategory(), RowFlag.OK, RowStatus.APPLIED,
                    sku + ", " + product.getName() + ", " + product.getCurrentCost().toPlainString(), null);
            auditRepository.save(appliedAudit(analyst, row, product.getCategory(),
                    uploaded1.plusMinutes((Integer) plan[2]), null));
        }

        // ---- Historical file 2: HP xlsx, 4 days ago, 6 applied (1 breach with override) + 2 skipped.
        LocalDateTime uploaded2 = START.minusDays(4).withHour(15).withMinute(5).withSecond(0).withNano(0);
        PriceFile file2 = priceFile(vendors.get("HP"), "hp-partner-price-aug25.xlsx", PriceFileStatus.PARSED,
                uploaded2, analyst, 8, 6, 0, uploaded2.plusMinutes(4));
        uploadedAudit(analyst, file2, uploaded2);

        List<PlanRow> hpRows = List.of(
                new PlanRow("7Y0K2PA", "ProBook 440 G10 Notebook PC i5", "58,400.00",
                        new BigDecimal("57200.00"), new BigDecimal("58400.00"), "Laptops",
                        RowFlag.OK, RowStatus.APPLIED, null),
                new PlanRow("7Y0Z6PA", "EliteBook 840 G10 Notebook PC i7", "1,18,000.00",
                        new BigDecimal("115600.00"), new BigDecimal("118000.00"), "Laptops",
                        RowFlag.OK, RowStatus.APPLIED, null),
                new PlanRow("7Y1K9PA", "250 G10 Laptop i5 13th Gen", "38,600.00",
                        new BigDecimal("37900.00"), new BigDecimal("38600.00"), "Laptops",
                        RowFlag.OK, RowStatus.APPLIED, null),
                new PlanRow("W1A53A", "LaserJet Pro M404dn Printer", "29,400.00",
                        new BigDecimal("26800.00"), new BigDecimal("29400.00"), "Printers",
                        RowFlag.BREACH, RowStatus.APPLIED, null),
                new PlanRow("6G8L7PA", "ProDesk 400 G9 Microtower Desktop", "44,500.00",
                        new BigDecimal("43600.00"), new BigDecimal("44500.00"), "Desktops",
                        RowFlag.OK, RowStatus.APPLIED, null),
                new PlanRow("2Z616F", "LaserJet M141w Multifunction Printer", "11,800.00",
                        new BigDecimal("11500.00"), new BigDecimal("11800.00"), "Printers",
                        RowFlag.OK, RowStatus.APPLIED, null),
                new PlanRow("1MR78B", "OfficeJet Pro 9020 All-in-One Printer", "23,100.00",
                        new BigDecimal("22400.00"), new BigDecimal("23100.00"), "Printers",
                        RowFlag.OK, RowStatus.SKIPPED, null),
                new PlanRow("5TW13AA", "HP USB-C Dock G5", "12,900.00",
                        null, new BigDecimal("12900.00"), null,
                        RowFlag.NEW_SKU, RowStatus.SKIPPED, null));
        int n2 = 1;
        int minute = 8;
        for (PlanRow plan : hpRows) {
            Product product = products.get(plan.sku());
            BigDecimal selling = product == null ? null : product.getSellingPrice();
            PriceFileRow row = resolvedRow(file2, n2++, plan.sku(), plan.name(), plan.oldCost(), plan.newCost(),
                    selling, plan.category(), plan.flag(), plan.status(),
                    plan.sku() + ", " + plan.name() + ", " + plan.costText(), plan.note());
            if (row.getStatus() == RowStatus.APPLIED) {
                User actor = row.getFlag() == RowFlag.BREACH ? demoAdmin : analyst;
                String reason = row.getFlag() == RowFlag.BREACH ? OVERRIDE_REASON : null;
                auditRepository.save(appliedAudit(actor, row, plan.category(),
                        uploaded2.plusMinutes(minute), reason));
            }
            minute += 6;
        }

        // ---- Pending file: CISCO csv uploaded today by the demo analyst — every flag at once.
        LocalDateTime uploaded3 = START.minusHours(3);
        PriceFile file3 = priceFile(vendors.get("CISCO"), "cisco-price-list-aug29.csv", PriceFileStatus.PARSED,
                uploaded3, demoAnalyst, 11, 8, 1, uploaded3.plusMinutes(2));
        uploadedAudit(demoAnalyst, file3, uploaded3);

        List<PlanRow> ciscoRows = List.of(
                new PlanRow("C1000-24T", "Catalyst 1000 24-Port Gigabit Switch", "74,900.00",
                        new BigDecimal("74500.00"), new BigDecimal("74900.00"), "Networking",
                        RowFlag.OK, RowStatus.PENDING, null),
                new PlanRow("MR46", "Meraki MR46 Wi-Fi 6 Access Point", "57,650.00",
                        new BigDecimal("58200.00"), new BigDecimal("57650.00"), "Networking",
                        RowFlag.OK, RowStatus.PENDING, null),
                new PlanRow("C1111-4P", "ISR 1111-4P Integrated Services Router", "39,450.00",
                        new BigDecimal("38900.00"), new BigDecimal("39450.00"), "Networking",
                        RowFlag.OK, RowStatus.PENDING, null),
                new PlanRow("SG350X-24", "SG350X 24-Port Gigabit Managed Switch", "42,100.00",
                        new BigDecimal("42600.00"), new BigDecimal("42100.00"), "Networking",
                        RowFlag.OK, RowStatus.PENDING, null),
                new PlanRow("C9120AXI-B", "Catalyst 9120AXI Access Point", "48,200.00",
                        new BigDecimal("46800.00"), new BigDecimal("48200.00"), "Networking",
                        RowFlag.BREACH, RowStatus.PENDING, null),
                new PlanRow("C9115AXI-B", "Catalyst 9115AXI Access Point", "40,900.00",
                        new BigDecimal("39800.00"), new BigDecimal("40900.00"), "Networking",
                        RowFlag.BREACH, RowStatus.PENDING, null),
                new PlanRow("C1000-48T", "Catalyst 1000 48-Port Gigabit Switch", "1,21,400.00",
                        new BigDecimal("118500.00"), new BigDecimal("121400.00"), "Networking",
                        RowFlag.BREACH, RowStatus.PENDING, null),
                new PlanRow("CAB-10G-SFPP", "10GBASE-CU SFP+ Passive Cable 3m", "13,900.00",
                        new BigDecimal("10400.00"), new BigDecimal("13900.00"), "Networking",
                        RowFlag.BREACH, RowStatus.PENDING, null),
                new PlanRow("C9130AXI-B", "Catalyst 9130AXI Wi-Fi 6E Access Point", "52,400.00",
                        null, new BigDecimal("52400.00"), null,
                        RowFlag.NEW_SKU, RowStatus.PENDING, null),
                new PlanRow("C1000-24FP-4G-L", "Catalyst 1000 24-Port PoE Switch", "N/A",
                        null, null, "Networking",
                        RowFlag.NOT_FOUND, RowStatus.PENDING, "Cost not numeric"),
                new PlanRow("C1000-24T", "Catalyst 1000 24-Port Gigabit Switch", "75,000.00",
                        null, new BigDecimal("75000.00"), "Networking",
                        RowFlag.DUPLICATE, RowStatus.PENDING, "SKU already seen earlier in this file"));
        int n3 = 1;
        for (PlanRow plan : ciscoRows) {
            Product product = products.get(plan.sku());
            BigDecimal selling = product == null ? null : product.getSellingPrice();
            resolvedRow(file3, n3++, plan.sku(), plan.name(), plan.oldCost(), plan.newCost(),
                    selling, plan.category(), plan.flag(), plan.status(),
                    plan.sku() + ", " + plan.name() + ", " + plan.costText(), plan.note());
        }

        // ---- A few extra audit entries so the audit screen is alive.
        simpleAudit(users.get("admin"), "MAPPING_UPDATED", "VENDOR:CISCO",
                "3 column mappings saved: SKU, Description, Cost Price",
                START.minusDays(20).withHour(10).withMinute(12));
        simpleAudit(analyst, "MAPPING_UPDATED", "VENDOR:INTEL",
                "3 column mappings saved: Product Code, Item Description, Unit Price INR",
                START.minusDays(18).withHour(16).withMinute(40));
        simpleAudit(demoAdmin, "POLICY_UPDATED", "MARGIN_POLICY:" + policies.get(0).getId(),
                "category=(global); minMarginPct=8.00",
                START.minusDays(22).withHour(9).withMinute(30));
        simpleAudit(demoAdmin, "POLICY_UPDATED", "MARGIN_POLICY:" + policies.get(1).getId(),
                "category=Networking; minMarginPct=12.00 (was 10.00)",
                START.minusDays(21).withHour(9).withMinute(45));
        simpleAudit(users.get("admin"), "PRICE_EDIT", "PRODUCT:" + products.get("7Y0Z6PA").getId(),
                "sku=7Y0Z6PA; selling price 136000.00 → 134500.00",
                START.minusDays(6).withHour(12).withMinute(15));
    }

    private record PlanRow(String sku, String name, String costText, BigDecimal oldCost, BigDecimal newCost,
                           String category, RowFlag flag, RowStatus status, String note) {
    }

    /** Mirrors the parser: costs and percentages only freeze for OK/BREACH rows. */
    private PriceFileRow resolvedRow(PriceFile file, int rowNumber, String sku, String name,
                                     BigDecimal oldCost, BigDecimal newCost, BigDecimal selling, String category,
                                     RowFlag flag, RowStatus status, String raw, String note) {
        PriceFileRow row = new PriceFileRow();
        row.setPriceFile(file);
        row.setRowNumber(rowNumber);
        row.setSku(sku);
        row.setProductName(name);
        if (flag == RowFlag.OK || flag == RowFlag.BREACH) {
            row.setOldCost(oldCost);
            row.setChangePct(DiffMath.changePct(oldCost, newCost));
            row.setResultingMarginPct(DiffMath.marginPct(selling, newCost));
        }
        row.setNewCost(newCost);
        row.setFlag(flag);
        row.setStatus(status);
        row.setRawRow(raw);
        row.setNote(note);
        return rowRepository.save(row);
    }

    private AuditLog appliedAudit(User actor, PriceFileRow row, String category, LocalDateTime at, String reason) {
        AuditLog entry = new AuditLog();
        entry.setActorEmail(actor.getEmail());
        entry.setAction("PRICE_APPLIED");
        entry.setEntityRef("PRICE_FILE_ROW:" + row.getId());
        entry.setDetails("sku=" + row.getSku()
                + "; cost " + row.getOldCost() + " → " + row.getNewCost()
                + "; change " + row.getChangePct() + "%"
                + "; resulting margin " + row.getResultingMarginPct() + "%"
                + "; floor " + diffMath.floorFor(category)
                + (reason == null ? "" : "; override reason: " + reason));
        entry.setCreatedAt(at);
        return entry;
    }

    private void simpleAudit(User actor, String action, String entityRef, String details, LocalDateTime at) {
        AuditLog entry = new AuditLog();
        entry.setActorEmail(actor.getEmail());
        entry.setAction(action);
        entry.setEntityRef(entityRef);
        entry.setDetails(details);
        entry.setCreatedAt(at);
        auditRepository.save(entry);
    }

    private void uploadedAudit(User actor, PriceFile file, LocalDateTime at) {
        simpleAudit(actor, "FILE_UPLOADED", "PRICE_FILE:" + file.getId(),
                file.getFilename() + " (" + file.getVendor().getCode() + ")", at);
    }

    private PriceFile priceFile(Vendor vendor, String filename, PriceFileStatus status, LocalDateTime uploadedAt,
                                User uploader, int totalRows, int updatedRows, int errorRows,
                                LocalDateTime processedAt) {
        PriceFile file = new PriceFile();
        file.setVendor(vendor);
        file.setFilename(filename);
        file.setStatus(status);
        file.setUploadedAt(uploadedAt);
        file.setUploadedBy(uploader);
        file.setTotalRows(totalRows);
        file.setUpdatedRows(updatedRows);
        file.setErrorRows(errorRows);
        file.setProcessedAt(processedAt);
        return fileRepository.save(file);
    }

    private void mapping(Vendor vendor, String header, TargetField target) {
        FileColumnMapping mapping = new FileColumnMapping();
        mapping.setVendor(vendor);
        mapping.setSourceHeader(header);
        mapping.setTargetField(target);
        mappingRepository.save(mapping);
    }

    private User user(String email, String passwordHash, String fullName, UserRole role, boolean demo) {
        User u = new User();
        u.setEmail(email);
        u.setPasswordHash(passwordHash);
        u.setFullName(fullName);
        u.setRole(role);
        u.setDemo(demo);
        return u;
    }

    private Vendor vendor(String code, String name) {
        Vendor v = new Vendor();
        v.setCode(code);
        v.setName(name);
        return v;
    }
}
