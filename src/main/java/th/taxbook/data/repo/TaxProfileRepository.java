package th.taxbook.data.repo;

import java.math.BigDecimal;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import th.taxbook.data.entity.TaxProfile;

public interface TaxProfileRepository extends JpaRepository<TaxProfile, UUID> {

    /** สร้างหรือแก้โปรไฟล์ในคำสั่งเดียว (หนึ่งแถวต่อผู้ใช้) */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(nativeQuery = true, value = """
        insert into tax_profile (user_id, nick, marital, kids, kids2, parents, sp_parents, disabled,
                                 ss, life, health, phealth, retire, home, donate,
                                 rent_type, prof_type, biz_rate, excl4, project_remaining, is_sample, updated_at)
        values (:uid, :nick, :marital, :kids, :kids2, :parents, :spParents, :disabled,
                :ss, :life, :health, :phealth, :retire, :home, :donate,
                :rentType, :profType, :bizRate, :excl4, :project, :sample, now())
        on conflict (user_id) do update set
                nick = excluded.nick, marital = excluded.marital, kids = excluded.kids, kids2 = excluded.kids2,
                parents = excluded.parents, sp_parents = excluded.sp_parents, disabled = excluded.disabled,
                ss = excluded.ss, life = excluded.life, health = excluded.health, phealth = excluded.phealth,
                retire = excluded.retire, home = excluded.home, donate = excluded.donate,
                rent_type = excluded.rent_type, prof_type = excluded.prof_type, biz_rate = excluded.biz_rate,
                excl4 = excluded.excl4, project_remaining = excluded.project_remaining,
                is_sample = excluded.is_sample, updated_at = now()
        """)
    int upsert(@Param("uid") UUID uid, @Param("nick") String nick, @Param("marital") String marital,
               @Param("kids") int kids, @Param("kids2") int kids2, @Param("parents") int parents,
               @Param("spParents") int spParents, @Param("disabled") int disabled,
               @Param("ss") BigDecimal ss, @Param("life") BigDecimal life, @Param("health") BigDecimal health,
               @Param("phealth") BigDecimal phealth, @Param("retire") BigDecimal retire,
               @Param("home") BigDecimal home, @Param("donate") BigDecimal donate,
               @Param("rentType") BigDecimal rentType, @Param("profType") BigDecimal profType,
               @Param("bizRate") BigDecimal bizRate, @Param("excl4") boolean excl4,
               @Param("project") boolean project, @Param("sample") boolean sample);
}