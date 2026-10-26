package th.taxbook.data.repo;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import th.taxbook.data.entity.WithholdPref;

public interface WithholdPrefRepository extends JpaRepository<WithholdPref, Long> {
    @Query("select p from WithholdPref p join fetch p.category c where p.user.id = :uid order by c.code")
    List<WithholdPref> findAllByUser(@Param("uid") UUID uid);

    @Query("select p from WithholdPref p where p.user.id = :uid and p.category.code = :code")
    Optional<WithholdPref> findOne(@Param("uid") UUID uid, @Param("code") String code);
}
