package fr.medislot.repository;

import fr.medislot.domain.AppUser;

import org.springframework.data.jpa.repository.*;

public interface UserRepository extends JpaRepository<AppUser, Long> {
    java.util.Optional<AppUser> findByEmail(String email);

    java.util.Optional<AppUser> findByCalendarTokenHash(String hash);

    boolean existsByEmail(String email);
}
