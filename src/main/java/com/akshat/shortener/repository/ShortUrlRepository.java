package com.akshat.shortener.repository;

import com.akshat.shortener.model.ShortUrl;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ShortUrlRepository extends JpaRepository<ShortUrl, Long> {

    Optional<ShortUrl> findByShortCode(String shortCode);

    boolean existsByShortCode(String shortCode);

    /**
     * Application boot hone par Bloom Filter ko warm-up karne ke liye saare
     * existing active shortCodes fetch karte hain.
     */
    @Query("SELECT s.shortCode FROM ShortUrl s WHERE s.isActive = true")
    List<String> findAllActiveShortCodes();

    /**
     * High-Speed Bulk Click Increment:
     * Har click par individual entity load karke save() karne se dirty checking
     * aur SELECT queries ka load padta hai. Direct in-place UPDATE query se
     * database lock contention 95% kam ho jata hai.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE ShortUrl s SET s.clickCount = s.clickCount + :delta WHERE s.shortCode = :shortCode")
    int incrementClickCount(@Param("shortCode") String shortCode, @Param("delta") long delta);

    /**
     * Admin Portal: Pagination and search support
     */
    org.springframework.data.domain.Page<ShortUrl> findAllByOrderByCreatedAtDesc(org.springframework.data.domain.Pageable pageable);

    org.springframework.data.domain.Page<ShortUrl> findByShortCodeContainingIgnoreCaseOrOriginalUrlContainingIgnoreCaseOrderByCreatedAtDesc(
            String shortCode, String originalUrl, org.springframework.data.domain.Pageable pageable);

    long countByIsActiveTrue();

    long countByIsActiveFalse();

    @Query("SELECT COALESCE(SUM(s.clickCount), 0) FROM ShortUrl s")
    long sumTotalClicks();

    @Modifying
    @Query("DELETE FROM ShortUrl s WHERE s.shortCode = :shortCode")
    void deleteByShortCode(@Param("shortCode") String shortCode);
}
