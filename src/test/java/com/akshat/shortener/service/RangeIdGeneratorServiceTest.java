package com.akshat.shortener.service;

import com.akshat.shortener.model.IdRangeAllocation;
import com.akshat.shortener.repository.IdRangeAllocationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RangeIdGeneratorServiceTest {

    @Mock
    private IdRangeAllocationRepository allocationRepository;

    private RangeIdGeneratorService rangeIdGeneratorService;

    @BeforeEach
    void setUp() {
        IdRangeAllocation mockAllocation = IdRangeAllocation.builder()
                .id("GLOBAL_URL_SEQ")
                .currentMaxId(100000L)
                .stepSize(100L)
                .updatedAt(LocalDateTime.now())
                .build();

        when(allocationRepository.findByIdWithLock("GLOBAL_URL_SEQ"))
                .thenReturn(Optional.of(mockAllocation));
        when(allocationRepository.saveAndFlush(any()))
                .thenAnswer(invocation -> invocation.getArgument(0));

        PlatformTransactionManager stubTxManager = new PlatformTransactionManager() {
            @Override
            public TransactionStatus getTransaction(TransactionDefinition definition) throws TransactionException {
                return new SimpleTransactionStatus();
            }

            @Override
            public void commit(TransactionStatus status) throws TransactionException {
            }

            @Override
            public void rollback(TransactionStatus status) throws TransactionException {
            }
        };

        rangeIdGeneratorService = new RangeIdGeneratorService(allocationRepository, stubTxManager);
        ReflectionTestUtils.setField(rangeIdGeneratorService, "rangeBlockSize", 100L);
        rangeIdGeneratorService.init();
    }

    @Test
    @DisplayName("Range generator should produce strictly increasing sequential IDs")
    void testSequentialIdGeneration() {
        long id1 = rangeIdGeneratorService.nextId();
        long id2 = rangeIdGeneratorService.nextId();
        long id3 = rangeIdGeneratorService.nextId();

        assertEquals(id1 + 1, id2);
        assertEquals(id2 + 1, id3);
        assertTrue(id1 >= 100000L);
    }
}
