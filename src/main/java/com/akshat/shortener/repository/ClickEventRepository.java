package com.akshat.shortener.repository;

import com.akshat.shortener.model.ClickEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ClickEventRepository extends JpaRepository<ClickEvent, Long> {

    long countByShortCode(String shortCode);

    List<ClickEvent> findTop50ByShortCodeOrderByTimestampDesc(String shortCode);

    /**
     * Analytics Aggregations:
     * ClickHouse / TimescaleDB jaisi heavy infra ki zaroorat nahi hai 
     * medium scale par. Ye standard SQL aggregations sub-50ms me execute hoti hain
     * indexed shortCode column par.
     */
    @Query("SELECT c.country, COUNT(c) FROM ClickEvent c WHERE c.shortCode = :shortCode GROUP BY c.country ORDER BY COUNT(c) DESC")
    List<Object[]> countClicksByCountry(@Param("shortCode") String shortCode);

    @Query("SELECT c.deviceType, COUNT(c) FROM ClickEvent c WHERE c.shortCode = :shortCode GROUP BY c.deviceType ORDER BY COUNT(c) DESC")
    List<Object[]> countClicksByDeviceType(@Param("shortCode") String shortCode);

    @Query("SELECT c.referer, COUNT(c) FROM ClickEvent c WHERE c.shortCode = :shortCode GROUP BY c.referer ORDER BY COUNT(c) DESC")
    List<Object[]> countClicksByReferer(@Param("shortCode") String shortCode);

    @Query("SELECT c.browser, COUNT(c) FROM ClickEvent c WHERE c.shortCode = :shortCode GROUP BY c.browser ORDER BY COUNT(c) DESC")
    List<Object[]> countClicksByBrowser(@Param("shortCode") String shortCode);
}
