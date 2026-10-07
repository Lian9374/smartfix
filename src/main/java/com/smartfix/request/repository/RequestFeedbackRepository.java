package com.smartfix.request.repository;

import com.smartfix.request.domain.RequestFeedback;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RequestFeedbackRepository extends JpaRepository<RequestFeedback, Long> {
    Optional<RequestFeedback> findByRequestId(Long requestId);

    boolean existsByRequestId(Long requestId);
}
