package th.taxbook.data.repo;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import th.taxbook.data.entity.ExpenseEntry;

public interface ExpenseEntryRepository extends JpaRepository<ExpenseEntry, Long> {
    @Query("select e from ExpenseEntry e join fetch e.taxYear y where y.user.id = :uid order by y.yearBe, e.month, e.id")
    List<ExpenseEntry> findAllByUser(@Param("uid") UUID uid);

    @Query("select e from ExpenseEntry e join fetch e.taxYear y where e.id = :id and y.user.id = :uid")
    Optional<ExpenseEntry> findOwned(@Param("id") long id, @Param("uid") UUID uid);

    @Query("select count(e) from ExpenseEntry e where e.taxYear.user.id = :uid")
    long countByUser(@Param("uid") UUID uid);
}