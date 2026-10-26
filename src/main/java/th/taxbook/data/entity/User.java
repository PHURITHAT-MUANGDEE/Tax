package th.taxbook.data.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** ผู้ใช้ (ล็อกอินด้วย Google) ตาราง users แถวถูกสร้างตอนล็อกอินครั้งแรกโดย Repos.upsertUser */
@Entity
@Table(name = "users")
public class User {
    @Id
    private UUID id;

    @Column(name = "google_sub", nullable = false, updatable = false)
    private String googleSub;

    @Column(nullable = false)
    private String email;

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "tos_version")
    private String tosVersion;

    @Column(name = "tos_accepted_at")
    private Instant tosAcceptedAt;

    protected User() {}

    public UUID getId() { return id; }
    public String getGoogleSub() { return googleSub; }
    public String getEmail() { return email; }
    public Instant getCreatedAt() { return createdAt; }
    public String getTosVersion() { return tosVersion; }
    public Instant getTosAcceptedAt() { return tosAcceptedAt; }
}
