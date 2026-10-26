package th.taxbook.web;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** sitemap.xml และ robots.txt สร้างจาก APP_URL เพื่อให้ลิงก์ตรงกับโดเมนจริง (แอปเป็นหน้าเดียว เมนูใช้ # จึงลงเฉพาะหน้าสาธารณะ) */
@RestController
public class SitemapController {
    private final String base;

    public SitemapController(@Value("${app.base-url}") String appUrl) {
        this.base = appUrl.replaceAll("/+$", "");
    }

    @GetMapping(value = "/sitemap.xml", produces = MediaType.APPLICATION_XML_VALUE)
    public String sitemap() {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
            + "<urlset xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\">\n"
            + "  <url><loc>" + base + "/</loc><changefreq>monthly</changefreq><priority>1.0</priority></url>\n"
            + "  <url><loc>" + base + "/privacy.html</loc><changefreq>yearly</changefreq><priority>0.3</priority></url>\n"
            + "  <url><loc>" + base + "/terms.html</loc><changefreq>yearly</changefreq><priority>0.3</priority></url>\n"
            + "</urlset>\n";
    }

    @GetMapping(value = "/robots.txt", produces = MediaType.TEXT_PLAIN_VALUE)
    public String robots() {
        return "User-agent: *\nAllow: /\nDisallow: /api/\nDisallow: /admin/\nDisallow: /unsubscribe.html\n"
            + "Sitemap: " + base + "/sitemap.xml\n";
    }
}
