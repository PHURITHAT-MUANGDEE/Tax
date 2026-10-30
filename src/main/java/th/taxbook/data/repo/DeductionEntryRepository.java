package th.taxbook.data.repo;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import th.taxbook.data.entity.DeductionEntry;

public interface DeductionEntryRepository extends JpaRepository<DeductionEntry, Long> {
    /** รายการของผู้ใช้คนนี้ทุกปี เรียงตามปีแล้วตามลำดับที่เพิ่ม */
    @Query("select d from DeductionEntry d join fetch d.taxYear y where y.user.id = :uid order by y.yearBe, d.id")
    List<DeductionEntry> findAllByUser(@Param("uid") UUID uid);

    /** รายการของผู้ใช้คนนี้เท่านั้น (ไม่ใช่ของผู้ใช้อื่น) */
    @Query("select d from DeductionEntry d join fetch d.taxYear y where d.id = :id and y.user.id = :uid")
    Optional<DeductionEntry> findOwned(@Param("id") long id, @Param("uid") UUID uid);

    @Query("select count(d) from DeductionEntry d where d.taxYear.user.id = :uid")
    long countByUser(@Param("uid") UUID uid);
}