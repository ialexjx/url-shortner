package com.akshat.shortener.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.*;

import java.time.LocalDateTime;

/**
 * ==============================================================================
 * IdRangeAllocation Entity (Key Generation Service State)
 * ==============================================================================
 * 
 * Range-based token allocation state ko DB me persist karne ke liye entity.
 * Row example:
 * id = "GLOBAL_URL_SEQ"
 * currentMaxId = 500000
 * stepSize = 10000
 * 
 * Jab pod ko naya batch chahiye hota hai, toh wo atomic transaction me
 * currentMaxId ko currentMaxId + stepSize kar deta hai.
 */
@Entity
@Table(name = "id_range_allocations")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IdRangeAllocation {

    @Id
    @Column(length = 64)
    private String id; // e.g. "GLOBAL_URL_SEQ"

    @Column(nullable = false)
    private Long currentMaxId;

    @Column(nullable = false)
    private Long stepSize;

    @Column(nullable = false)
    private LocalDateTime updatedAt;
}
