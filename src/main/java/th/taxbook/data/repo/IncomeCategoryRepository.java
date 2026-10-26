package th.taxbook.data.repo;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import th.taxbook.data.entity.IncomeCategory;

public interface IncomeCategoryRepository extends JpaRepository<IncomeCategory, String> {
    List<IncomeCategory> findAllByOrderByCodeAsc();
}
