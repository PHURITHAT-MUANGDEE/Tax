package th.taxbook.data;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import th.taxbook.data.entity.IncomeCategory;
import th.taxbook.data.entity.IncomeEntry;
import th.taxbook.data.entity.TaxProfile;
import th.taxbook.data.entity.TaxYear;
import th.taxbook.data.entity.User;
import th.taxbook.data.entity.WithholdPref;
import th.taxbook.data.repo.IncomeCategoryRepository;
import th.taxbook.data.repo.IncomeEntryRepository;
import th.taxbook.data.repo.TaxProfileRepository;
import th.taxbook.data.repo.TaxYearRepository;
import th.taxbook.data.repo.UserRepository;
import th.taxbook.data.repo.WithholdPrefRepository;
import th.taxbook.validation.DataValidator;
import th.taxbook.validation.DataValidator.EntryInput;
import th.taxbook.validation.DataValidator.Invalid;
import th.taxbook.validation.DataValidator.ProfileInput;

/**
 * ตรรกะจัดการข้อมูลภาษีของผู้ใช้: โปรไฟล์ ปีภาษี รายการรายได้ วิธีหักภาษี ทุกเมธอดรับ userId จากเซสชัน
 * และกรองด้วย userId เสมอ ผู้ใช้จึงเข้าถึงข้อมูลของคนอื่นไม่ได้
 */
@Service
public class TaxDataService {
    /** กันผู้ใช้คนเดียวสร้างรายการมากเกินไป */
    static final int MAX_ENTRIES_PER_USER = 5000;
    private static final Sort ENTRY_ORDER = Sort.by("taxYear.yearBe", "month", "category.code", "id");

    private final UserRepository users;
    private final TaxProfileRepository profiles;
    private final TaxYearRepository years;
    private final IncomeCategoryRepository categories;
    private final IncomeEntryRepository entries;
    private final WithholdPrefRepository prefs;

    public TaxDataService(UserRepository users, TaxProfileRepository profiles, TaxYearRepository years,
                          IncomeCategoryRepository categories, IncomeEntryRepository entries, WithholdPrefRepository prefs) {
        this.users = users;
        this.profiles = profiles;
        this.years = years;
        this.categories = categories;
        this.entries = entries;
        this.prefs = prefs;
    }

    // ---------- ประเภทเงินได้ ----------

    @Transactional(readOnly = true)
    public List<Dtos.Category> categories() {
        return categories.findAllByOrderByCodeAsc().stream().map(Dtos.Category::of).toList();
    }

    // ---------- โปรไฟล์ (1:1 กับผู้ใช้) ----------

    @Transactional(readOnly = true)
    public Dtos.Profile profile(UUID uid) {
        return profiles.findById(uid).map(Dtos.Profile::of).orElseGet(Dtos.Profile::defaults);
    }

    @Transactional
    public Dtos.Profile saveProfile(UUID uid, ProfileInput in) {
         profiles.upsert(uid, in.nick(), in.marital(), in.kids(), in.kids2(), in.parents(), in.spParents(), in.disabled(),
            in.ss(), in.life(), in.health(), in.phealth(), in.retire(), in.home(), in.donate(),
            in.rentType(), in.profType(), in.bizRate(), in.excl4(), in.project(), in.sample());
        return Dtos.Profile.of(profiles.findById(uid).orElseThrow());
    }

    // ---------- ปีภาษี ----------

    @Transactional(readOnly = true)
    public List<Dtos.Year> listYears(UUID uid) {
        List<Dtos.Year> out = new ArrayList<>();
        for (Object[] r : years.totals(uid))
            out.add(new Dtos.Year(((Number) r[0]).intValue(), ((Number) r[1]).longValue(), nz(r[2]), nz(r[3])));
        return out;
    }

    /** สร้างปีภาษี ถ้ามีอยู่แล้วคืนปีเดิม (created = false) */
    @Transactional
    public Map<String, Object> createYear(UUID uid, int yearBe) {
        boolean existed = years.findOne(uid, yearBe).isPresent();
        TaxYear y = findOrCreateYear(uid, yearBe);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("yearBe", y.getYearBe());
        out.put("created", !existed);
        return out;
    }

    @Transactional
    public void deleteYear(UUID uid, int yearBe) {
        TaxYear y = years.findOne(uid, yearBe).orElseThrow(() -> new NotFound("year"));
        years.delete(y);        // รายการรายได้ของปีนั้นถูกลบตาม (cascade)
    }

    @Transactional(readOnly = true)
    public Dtos.YearSummary yearSummary(UUID uid, int yearBe) {
        TaxYear y = years.findOne(uid, yearBe).orElseThrow(() -> new NotFound("year"));
        List<Dtos.MonthSummary> rows = new ArrayList<>();
        BigDecimal ta = BigDecimal.ZERO, tw = BigDecimal.ZERO;
        long n = 0;
        for (Object[] r : entries.summarize(y.getId())) {
            Dtos.MonthSummary m = new Dtos.MonthSummary(((Number) r[0]).intValue(), (String) r[1], nz(r[2]), nz(r[3]), ((Number) r[4]).longValue());
            rows.add(m);
            ta = ta.add(m.amount());
            tw = tw.add(m.withheld());
            n += m.entries();
        }
        return new Dtos.YearSummary(yearBe, n, ta, tw, rows);
    }

    // ---------- รายการรายได้ ----------

    /** ดูรายการ/ค้นหา: เงื่อนไขที่เป็น null ไม่ใช้กรอง ชื่อค้นแบบมีคำนี้อยู่ (ไม่สนตัวพิมพ์) */
    @Transactional(readOnly = true)
    public List<Dtos.Entry> search(UUID uid, Integer yearBe, Integer month, String category, String q) {
        Specification<IncomeEntry> spec = (root, query, cb) -> cb.equal(root.get("taxYear").get("user").get("id"), uid);
        if (yearBe != null) spec = spec.and((root, query, cb) -> cb.equal(root.get("taxYear").get("yearBe"), yearBe));
        if (month != null) spec = spec.and((root, query, cb) -> cb.equal(root.get("month"), month));
        if (category != null) spec = spec.and((root, query, cb) -> cb.equal(root.get("category").get("code"), category));
        if (q != null && !q.isBlank()) {
            String like = "%" + q.trim().toLowerCase().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
            spec = spec.and((root, query, cb) -> cb.like(cb.lower(root.get("name")), like, '\\'));
        }
        return entries.findAll(spec, ENTRY_ORDER).stream().map(Dtos.Entry::of).toList();
    }

    @Transactional(readOnly = true)
    public Dtos.Entry entry(UUID uid, long id) {
        return Dtos.Entry.of(entries.findOwned(id, uid).orElseThrow(() -> new NotFound("entry")));
    }

    @Transactional
    public Dtos.Entry createEntry(UUID uid, EntryInput in) {
        if (entries.countByUser(uid) >= MAX_ENTRIES_PER_USER) throw new Conflict("too_many_entries");
        IncomeCategory cat = categories.findById(in.category()).orElseThrow(() -> new Invalid("invalid_category"));
        TaxYear y = findOrCreateYear(uid, in.yearBe());
        IncomeEntry e = entries.save(new IncomeEntry(y, in.month(), cat, in.amount(), in.withheld(), in.method(), in.name()));
        return Dtos.Entry.of(e);
    }

    /** แก้เฉพาะฟิลด์ที่ส่งมา (ย้ายไปปีอื่น เดือนอื่น หรือประเภทอื่นได้) */
    @Transactional
    public Dtos.Entry updateEntry(UUID uid, long id, EntryInput in) {
        IncomeEntry e = entries.findOwned(id, uid).orElseThrow(() -> new NotFound("entry"));
        if (in.yearBe() != null && in.yearBe() != e.getTaxYear().getYearBe()) e.setTaxYear(findOrCreateYear(uid, in.yearBe()));
        if (in.month() != null) e.setMonth(in.month());
        if (in.category() != null) e.setCategory(categories.findById(in.category()).orElseThrow(() -> new Invalid("invalid_category")));
        if (in.amount() != null) e.setAmount(in.amount());
        if (in.withheld() != null) e.setWithheld(in.withheld());
        if (in.method() != null) e.setMethod(in.method());
        if (in.name() != null) e.setName(in.name());
        return Dtos.Entry.of(entries.save(e));
    }

    @Transactional
    public void deleteEntry(UUID uid, long id) {
        entries.delete(entries.findOwned(id, uid).orElseThrow(() -> new NotFound("entry")));
    }

    /** ลบรายการทั้งหมดของปี (หรือเฉพาะเดือน ถ้าส่ง month) โดยไม่ลบตัวปีภาษี คืนจำนวนที่ลบ */
    @Transactional
    public int clearEntries(UUID uid, int yearBe, Integer month) {
        TaxYear y = years.findOne(uid, yearBe).orElseThrow(() -> new NotFound("year"));
        return month == null ? entries.deleteByYear(y.getId()) : entries.deleteByYearAndMonth(y.getId(), month);
    }

    // ---------- วิธีหักภาษีที่เลือกล่าสุด ----------

    @Transactional(readOnly = true)
    public Map<String, String> prefs(UUID uid) {
        Map<String, String> out = new LinkedHashMap<>();
        for (WithholdPref p : prefs.findAllByUser(uid)) out.put(p.getCategory().getCode(), p.getMethod());
        return out;
    }

    @Transactional
    public Map<String, String> savePref(UUID uid, String category, String method) {
        IncomeCategory cat = categories.findById(category).orElseThrow(() -> new Invalid("invalid_category"));
        WithholdPref p = prefs.findOne(uid, category).orElseGet(() -> new WithholdPref(users.getReferenceById(uid), cat, method));
        p.setMethod(method);
        prefs.save(p);
        return Map.of("category", category, "method", method);
    }

    @Transactional
    public void deletePref(UUID uid, String category) {
        prefs.delete(prefs.findOne(uid, category).orElseThrow(() -> new NotFound("pref")));
    }

    // ---------- มุมมองรวม (ใช้โหลดหน้าเว็บและส่งออกข้อมูล) ----------

    /**
     * รวมข้อมูลทั้งหมดของผู้ใช้เป็นโครงสร้างเดียวที่หน้าเว็บใช้:
     * profile, wm (วิธีหักที่เลือก), years[ปี].months[เดือน][ประเภท] = [{id, i, w, t, n}]
     */
    @Transactional(readOnly = true)
    public Map<String, Object> workbook(UUID uid) {
        Map<String, Object> yearMap = new LinkedHashMap<>();
        for (TaxYear y : years.findAllByUser(uid)) {
            Map<String, Object> ym = new LinkedHashMap<>();
            ym.put("months", new LinkedHashMap<String, Object>());
            yearMap.put(String.valueOf(y.getYearBe()), ym);
        }
        for (Dtos.Entry e : search(uid, null, null, null, null)) {
            @SuppressWarnings("unchecked")
            Map<String, Object> ym = (Map<String, Object>) yearMap.get(String.valueOf(e.yearBe()));
            @SuppressWarnings("unchecked")
            Map<String, Object> months = (Map<String, Object>) ym.get("months");
            @SuppressWarnings("unchecked")
            Map<String, Object> cats = (Map<String, Object>) months.computeIfAbsent(String.valueOf(e.month()), k -> new LinkedHashMap<String, Object>());
            @SuppressWarnings("unchecked")
            List<Object> list = (List<Object>) cats.computeIfAbsent(e.category(), k -> new ArrayList<Object>());
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", e.id());
            item.put("i", e.amount());
            item.put("w", e.withheld());
            item.put("t", e.method());
            item.put("n", e.name());
            list.add(item);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("profile", profile(uid));
        out.put("wm", prefs(uid));
        out.put("years", yearMap);
        return out;
    }

    // ---------- ใช้ภายใน ----------

    private TaxYear findOrCreateYear(UUID uid, int yearBe) {
        return years.findOne(uid, yearBe).orElseGet(() -> years.save(new TaxYear(users.getReferenceById(uid), yearBe)));
    }

    private static BigDecimal nz(Object v) { return v == null ? BigDecimal.ZERO : (BigDecimal) v; }
}
