package fr.medislot.repository;

import fr.medislot.domain.Professional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface ProfessionalRepository extends JpaRepository<Professional, Long> {
    @Query(
            """
            select p from Professional p
            where (:specialty = '' or p.specialty = :specialty)
              and (:city = '' or lower(p.city) like lower(concat('%', :city, '%')))
              and (:q = '' or lower(p.name) like lower(concat('%', :q, '%')))
            """)
    Page<Professional> search(String specialty, String city, String q, Pageable pageable);

    // Filtre grossier ; minLon > maxLon quand la boîte traverse l'antiméridien.
    @Query(
            """
            select p from Professional p
            where p.latitude between :minLat and :maxLat
              and ((:minLon <= :maxLon and p.longitude between :minLon and :maxLon)
                or (:minLon > :maxLon and (p.longitude >= :minLon or p.longitude <= :maxLon)))
              and (:specialty = '' or p.specialty = :specialty)
            """)
    List<Professional> findInBox(
            double minLat, double maxLat, double minLon, double maxLon, String specialty);
}
