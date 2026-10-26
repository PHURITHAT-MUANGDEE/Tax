package th.taxbook.data.repo;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.domain.Sort;
import org.springframework.data.repository.query.Param;
import th.taxbook.data.entity.IncomeEntry;

public interface IncomeEntryRepository extends JpaRepository<IncomeEntry, Long>, JpaSpecificationExecutor<IncomeEntry> {
    /** ค้นหาแบบกำหนดเงื่อนไขเอง (ปี เดือน ประเภท ชื่อ) พร้อมดึงปีภาษีและประเภทมาด้วยเพื่อไม่ให้ query ซ้ำ */
    @Override
    @EntityGraph(attributePaths = {"taxYear", "category"})
    List<IncomeEntry> findAll(Specification<IncomeEntry> spec, Sort sort);

    /** รายการของผู้ใช้คนนี้เท่านั้น (ไม่ใช่ของผู้ใช้อื่น) */
    @Query("select e from IncomeEntry e join fetch e.taxYear y join fetch e.category where e.id = :id and y.user.id = :uid")
    Optional<IncomeEntry> findOwned(@Param("id") long id, @Param("uid") UUID uid);

    @Query("select count(e) from IncomeEntry e where e.taxYear.user.id = :uid")
    long countByUser(@Param("uid") UUID uid);

    @Modifying
    @Query("delete from IncomeEntry e where e.taxYear.id = :yid")
    int deleteByYear(@Param("yid") long yearId);

    @Modifying
    @Query("delete from IncomeEntry e where e.taxYear.id = :yid and e.month = :month")
    int deleteByYearAndMonth(@Param("yid") long yearId, @Param("month") int month);

    /** [เดือน, ประเภท, รวมรายได้, รวมภาษีที่ถูกหัก, จำนวนรายการ] */
    @Query("select e.month, e.category.code, sum(e.amount), sum(e.withheld), count(e.id) from IncomeEntry e "
        + "where e.taxYear.id = :yid group by e.month, e.category.code order by e.month, e.category.code")
    List<Object[]> summarize(@Param("yid") long yearId);
}
