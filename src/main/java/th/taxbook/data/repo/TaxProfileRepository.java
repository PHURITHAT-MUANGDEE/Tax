package th.taxbook.data.repo;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import th.taxbook.data.entity.TaxProfile;

public interface TaxProfileRepository extends JpaRepository<TaxProfile, UUID> {}
