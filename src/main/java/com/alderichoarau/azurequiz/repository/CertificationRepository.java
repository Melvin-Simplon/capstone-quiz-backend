package com.alderichoarau.azurequiz.repository;

import com.alderichoarau.azurequiz.entity.Certification;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CertificationRepository extends JpaRepository<Certification, UUID> {
    List<Certification> findAllByOrderByPositionAsc();
}
