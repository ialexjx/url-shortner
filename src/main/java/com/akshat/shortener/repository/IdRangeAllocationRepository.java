package com.akshat.shortener.repository;

import com.akshat.shortener.model.IdRangeAllocation;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface IdRangeAllocationRepository extends JpaRepository<IdRangeAllocation, String> {

    /**
     * Pessimistic Write Lock (SELECT FOR UPDATE):
     * Agar multiple pods ek saath naya ID block request karein,
     * toh database row lock ensure karega ki koi do pods ko same range na mile.
     * Ye operation har 10,000 URLs me sirf 1 baar chalta hai, isliye zero bottleneck hai.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM IdRangeAllocation r WHERE r.id = :id")
    Optional<IdRangeAllocation> findByIdWithLock(@Param("id") String id);
}
