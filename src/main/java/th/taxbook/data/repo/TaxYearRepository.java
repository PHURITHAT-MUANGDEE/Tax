package th.taxbook.data.repo;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import th.taxbook.data.entity.TaxYear;

public interface TaxYearRepository extends JpaRepository<TaxYear, Long> {
    @Query("select y from TaxYear y where y.user.id = :uid order by y.yearBe desc")
    List<TaxYear> findAllByUser(@Param("uid") UUID uid);

    @Query("select y from TaxYear y where y.user.id = :uid and y.yearBe = :year")
    Optional<TaxYear> findOne(@Param("uid") UUID uid, @Param("year") int year);

    /** [ปี, จำนวนรายการ, รวมรายได้, รวมภาษีที่ถูกหัก] ต่อปี (รวมถึงปีที่ยังไม่มีรายการ ผลรวมเป็น null) */
    @Query("select y.yearBe, count(e.id), sum(e.amount), sum(e.withheld) from TaxYear y left join y.entries e "
        + "where y.user.id = :uid group by y.yearBe order by y.yearBe desc")
    List<Object[]> totals(@Param("uid") UUID uid);
}
