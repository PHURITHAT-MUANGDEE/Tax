package th.taxbook.data.repo;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import th.taxbook.data.entity.User;

public interface UserRepository extends JpaRepository<User, UUID> {}
