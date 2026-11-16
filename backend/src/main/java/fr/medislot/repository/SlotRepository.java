package fr.medislot.repository;

import fr.medislot.domain.Slot;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface SlotRepository extends JpaRepository<Slot, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Slot s where s.id = :id")
    Optional<Slot> lockById(Long id);

    @Query(
            """
            select s from Slot s
            where s.professionalId = :professionalId
              and s.startsAt >= :from and s.startsAt < :until
              and not exists (
                select a.id from Appointment a where a.slotId = s.id and a.status = 'CONFIRMED')
            order by s.startsAt
            """)
    List<Slot> available(Long professionalId, Instant from, Instant until);
}
