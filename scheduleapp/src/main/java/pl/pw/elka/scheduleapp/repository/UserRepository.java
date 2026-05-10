package pl.pw.elka.scheduleapp.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import pl.pw.elka.scheduleapp.model.AppUser;

@Repository
public interface UserRepository extends JpaRepository<AppUser, Long> {
    Optional<AppUser> findByEmail(String email);
    Optional<AppUser> findByUuid(String uuid);
    boolean existsByEmail(String email);
}
