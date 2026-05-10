package pl.pw.elka.scheduleapp.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import pl.pw.elka.scheduleapp.model.Operation;

@Repository
public interface OperationRepository extends JpaRepository<Operation, Long> {
    List<Operation> findByUserId(String userId);
    Optional<Operation> findByIdAndUserId(Long id, String userId);
    @Transactional
    void deleteByUserId(String userId);
}
