package th.taxbook.web;

import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import th.taxbook.data.Dtos;
import th.taxbook.data.TaxDataService;

/** ประเภทเงินได้ 40(1)–40(8) (ข้อมูลอ้างอิง อ่านอย่างเดียว) */
@RestController
@RequestMapping("/api/categories")
public class CategoryController {
    private final TaxDataService data;

    public CategoryController(TaxDataService data) { this.data = data; }

    @GetMapping
    public List<Dtos.Category> list() { return data.categories(); }
}
