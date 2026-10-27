package org.ecom.quote;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

interface QuoteRequestRepository extends JpaRepository<QuoteRequest, UUID> {

    Page<QuoteRequest> findByStatus(QuoteStatus status, Pageable pageable);

}
