package com.akshat.shortener.service;

import com.akshat.shortener.repository.ShortUrlRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BloomFilterServiceTest {

    @Mock
    private ShortUrlRepository shortUrlRepository;

    private BloomFilterService bloomFilterService;

    @BeforeEach
    void setUp() {
        when(shortUrlRepository.findAllActiveShortCodes()).thenReturn(List.of("alpha", "beta"));
        bloomFilterService = new BloomFilterService(shortUrlRepository);
        ReflectionTestUtils.setField(bloomFilterService, "expectedInsertions", 10000);
        ReflectionTestUtils.setField(bloomFilterService, "fpp", 0.01);
        bloomFilterService.init();
    }

    @Test
    @DisplayName("Bloom filter should contain pre-warmed elements")
    void testPrewarmedElements() {
        assertTrue(bloomFilterService.mightContain("alpha"));
        assertTrue(bloomFilterService.mightContain("beta"));
    }

    @Test
    @DisplayName("Bloom filter should contain dynamically added elements")
    void testDynamicallyAddedElements() {
        assertFalse(bloomFilterService.mightContain("gamma"));
        bloomFilterService.add("gamma");
        assertTrue(bloomFilterService.mightContain("gamma"));
    }

    @Test
    @DisplayName("Bloom filter should return false for definitely non-existent element")
    void testDefiniteNegative() {
        assertFalse(bloomFilterService.mightContain("definitely_non_existent_key_99999"));
    }
}
