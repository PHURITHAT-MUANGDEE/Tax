package th.taxbook.web;

import jakarta.servlet.http.HttpSession;
import java.util.UUID;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Component;
import th.taxbook.repo.Repos;

/** แปลงผู้ใช้ที่ล็อกอินด้วย Google เป็น user id ในฐานข้อมูล (สร้างแถวให้ครั้งแรก แล้วจำไว้ในเซสชัน) */
@Component
public class CurrentUser {
    private static final String KEY = "taxbook.uid";
    private final Repos repos;

    public CurrentUser(Repos repos) { this.repos = repos; }

    public UUID id(OidcUser principal, HttpSession session) {
        Object cached = session.getAttribute(KEY);
        if (cached instanceof UUID u) return u;
        UUID id = repos.upsertUser(principal.getSubject(), principal.getEmail() == null ? "" : principal.getEmail());
        session.setAttribute(KEY, id);
        return id;
    }
}
