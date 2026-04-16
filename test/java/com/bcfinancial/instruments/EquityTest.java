package com.bcfinancial.instruments;

import org.junit.jupiter.api.Test;

import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;

class EquityTest {

    private static final Date DATE_A = new Date(1_000_000L);
    private static final Date DATE_B = new Date(2_000_000L);

    private static Equity spy(Date date, double close, double adjClose) {
        return new Equity("SPY", date, 99.0, 105.0, 98.0, close, adjClose, 1_000_000L);
    }

    // ------------------------------------------------------------------
    // getAdjClose fallback logic
    // ------------------------------------------------------------------

    @Test
    void adjCloseReturnedWhenPositive() {
        Equity e = spy(DATE_A, 100.0, 102.5);
        assertEquals(102.5, e.getAdjClose());
    }

    @Test
    void adjCloseFallsBackToCloseWhenZero() {
        Equity e = spy(DATE_A, 100.0, 0.0);
        assertEquals(100.0, e.getAdjClose());
    }

    @Test
    void adjCloseFallsBackToCloseWhenNegative() {
        Equity e = spy(DATE_A, 100.0, -5.0);
        assertEquals(100.0, e.getAdjClose());
    }

    // ------------------------------------------------------------------
    // Accessor round-trip
    // ------------------------------------------------------------------

    @Test
    void allFieldsStoredCorrectly() {
        Equity e = new Equity("AAPL", DATE_A, 1.0, 2.0, 3.0, 4.0, 5.0, 6L);
        assertEquals("AAPL", e.getSymbol());
        assertEquals(DATE_A, e.getDate());
        assertEquals(1.0, e.getOpen());
        assertEquals(2.0, e.getHigh());
        assertEquals(3.0, e.getLow());
        assertEquals(4.0, e.getClose());
        assertEquals(5.0, e.getAdjClose());
        assertEquals(6L, e.getVolume());
    }

    // ------------------------------------------------------------------
    // equals / hashCode — identity is (symbol, date)
    // ------------------------------------------------------------------

    @Test
    void equalWhenSymbolAndDateMatch() {
        Equity e1 = spy(DATE_A, 100.0, 102.0);
        Equity e2 = spy(DATE_A, 200.0, 204.0); // different prices, same identity
        assertEquals(e1, e2);
    }

    @Test
    void notEqualWhenSymbolDiffers() {
        Equity e1 = new Equity("SPY", DATE_A, 100, 105, 99, 100, 100, 1_000_000L);
        Equity e2 = new Equity("QQQ", DATE_A, 100, 105, 99, 100, 100, 1_000_000L);
        assertNotEquals(e1, e2);
    }

    @Test
    void notEqualWhenDateDiffers() {
        Equity e1 = spy(DATE_A, 100.0, 100.0);
        Equity e2 = spy(DATE_B, 100.0, 100.0);
        assertNotEquals(e1, e2);
    }

    @Test
    void equalToItself() {
        Equity e = spy(DATE_A, 100.0, 100.0);
        assertEquals(e, e);
    }

    @Test
    void notEqualToNull() {
        Equity e = spy(DATE_A, 100.0, 100.0);
        assertNotEquals(null, e);
    }

    @Test
    void notEqualToDifferentType() {
        Equity e = spy(DATE_A, 100.0, 100.0);
        assertNotEquals("SPY", e);
    }

    @Test
    void hashCodeConsistentWithEquals() {
        Equity e1 = spy(DATE_A, 100.0, 102.0);
        Equity e2 = spy(DATE_A, 200.0, 204.0);
        assertEquals(e1.hashCode(), e2.hashCode());
    }

    @Test
    void hashCodeDiffersForDifferentSymbol() {
        Equity e1 = new Equity("SPY", DATE_A, 100, 105, 99, 100, 100, 0L);
        Equity e2 = new Equity("QQQ", DATE_A, 100, 105, 99, 100, 100, 0L);
        assertNotEquals(e1.hashCode(), e2.hashCode());
    }

    // ------------------------------------------------------------------
    // toString
    // ------------------------------------------------------------------

    @Test
    void toStringContainsSymbol() {
        Equity e = spy(DATE_A, 100.0, 100.0);
        assertTrue(e.toString().contains("SPY"));
    }

    @Test
    void toStringContainsPriceFields() {
        Equity e = new Equity("SPY", DATE_A, 1.0, 2.0, 3.0, 4.0, 5.0, 999L);
        String s = e.toString();
        assertTrue(s.contains("1.0000") || s.contains("1,0000"));
        assertTrue(s.contains("999"));
    }
}
