package com.bcfinancial.portfolio;

import com.bcfinancial.data.EquityRepository;
import com.bcfinancial.instruments.Equity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.sql.SQLException;
import java.util.Calendar;
import java.util.Date;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PortfolioCalculatorTest {

    @Mock
    private EquityRepository repository;

    private PortfolioCalculator calculator;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        calculator = new PortfolioCalculator(repository);
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    /** Build a Date at midnight for the given year/month/day. */
    private static Date date(int year, int month, int day) {
        Calendar c = Calendar.getInstance();
        c.set(year, month - 1, day, 0, 0, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c.getTime();
    }

    /** Build an Equity where adjClose == close (simplifies CAGR assertions). */
    private static Equity equity(String symbol, Date date, double price) {
        return new Equity(symbol, date, price, price, price, price, price, 1_000_000L);
    }

    private void stubPrice(String symbol, Date date, double price) throws SQLException {
        Optional<Equity> eq = Optional.of(equity(symbol, date, price));
        when(repository.findNearestOnOrBefore(eq(symbol), eq(date), anyInt())).thenReturn(eq);
        when(repository.findNearestOnOrAfter(eq(symbol), eq(date), anyInt())).thenReturn(eq);
    }

    private void stubMissing(String symbol, Date date) throws SQLException {
        when(repository.findNearestOnOrBefore(eq(symbol), eq(date), anyInt())).thenReturn(Optional.empty());
        when(repository.findNearestOnOrAfter(eq(symbol), eq(date), anyInt())).thenReturn(Optional.empty());
    }

    // ------------------------------------------------------------------
    // calcCompoundAnnualGrowthRate — single symbol
    // ------------------------------------------------------------------

    @Test
    void cagrSingleSymbolExactOneYear() throws Exception {
        Date start = date(2023, 1, 1);
        Date end   = date(2024, 1, 1);
        stubPrice("SPY", start, 100.0);
        stubPrice("SPY", end,   110.0);

        double cagr = calculator.calcCompoundAnnualGrowthRate(
                new String[]{"SPY"}, new Double[]{1.0}, start, end, 365);

        // (110/100)^(365/365) - 1 = 0.10
        assertEquals(0.10, cagr, 1e-9);
    }

    @Test
    void cagrSingleSymbolNoChange() throws Exception {
        Date start = date(2023, 1, 1);
        Date end   = date(2024, 1, 1);
        stubPrice("SPY", start, 200.0);
        stubPrice("SPY", end,   200.0);

        double cagr = calculator.calcCompoundAnnualGrowthRate(
                new String[]{"SPY"}, new Double[]{1.0}, start, end, 365);

        assertEquals(0.0, cagr, 1e-9);
    }

    @Test
    void cagrSingleSymbolNegativeReturn() throws Exception {
        Date start = date(2023, 1, 1);
        Date end   = date(2024, 1, 1);
        stubPrice("SPY", start, 100.0);
        stubPrice("SPY", end,    90.0);

        double cagr = calculator.calcCompoundAnnualGrowthRate(
                new String[]{"SPY"}, new Double[]{1.0}, start, end, 365);

        // (90/100)^1 - 1 = -0.10
        assertEquals(-0.10, cagr, 1e-9);
    }

    // ------------------------------------------------------------------
    // calcCompoundAnnualGrowthRate — multiple symbols / weights
    // ------------------------------------------------------------------

    @Test
    void cagrTwoSymbolsWeightedAverage() throws Exception {
        Date start = date(2023, 1, 1);
        Date end   = date(2024, 1, 1);
        // SPY: +20%,  QQQ: 0%,  weights: 50/50 → expected 10%
        stubPrice("SPY", start, 100.0);
        stubPrice("SPY", end,   120.0);
        stubPrice("QQQ", start, 300.0);
        stubPrice("QQQ", end,   300.0);

        double cagr = calculator.calcCompoundAnnualGrowthRate(
                new String[]{"SPY", "QQQ"},
                new Double[]{0.5, 0.5},
                start, end, 365);

        assertEquals(0.10, cagr, 1e-9);
    }

    @Test
    void cagrUsesAdjCloseNotRawClose() throws Exception {
        Date start = date(2023, 1, 1);
        Date end   = date(2024, 1, 1);
        // adjClose differs from close — calculator must use adjClose
        Equity startEq = new Equity("SPY", start, 200.0, 210.0, 195.0, 205.0, 100.0, 1_000_000L);
        Equity endEq   = new Equity("SPY", end,   210.0, 220.0, 205.0, 215.0, 110.0, 1_000_000L);

        when(repository.findNearestOnOrBefore(eq("SPY"), eq(start), anyInt())).thenReturn(Optional.of(startEq));
        when(repository.findNearestOnOrAfter(eq("SPY"), eq(start), anyInt())).thenReturn(Optional.of(startEq));
        when(repository.findNearestOnOrBefore(eq("SPY"), eq(end), anyInt())).thenReturn(Optional.of(endEq));

        double cagr = calculator.calcCompoundAnnualGrowthRate(
                new String[]{"SPY"}, new Double[]{1.0}, start, end, 365);

        // adjClose: 100 → 110, CAGR = 0.10
        assertEquals(0.10, cagr, 1e-9);
    }

    @Test
    void cagrSubYearTimePeriodScalesCorrectly() throws Exception {
        Date start = date(2023, 1, 1);
        Date end   = date(2023, 7, 2); // ~182 days
        stubPrice("SPY", start, 100.0);
        stubPrice("SPY", end,   105.0);

        double cagr = calculator.calcCompoundAnnualGrowthRate(
                new String[]{"SPY"}, new Double[]{1.0}, start, end, 182);

        // base=1.05, power=365/182≈2.005, CAGR≈1.05^2.005 - 1 ≈ 0.1023
        double expected = Math.pow(1.05, 365.0 / 182.0) - 1.0;
        assertEquals(expected, cagr, 1e-9);
    }

    // ------------------------------------------------------------------
    // calcCompoundAnnualGrowthRate — missing price error handling
    // ------------------------------------------------------------------

    @Test
    void missingStartPriceThrowsException() throws Exception {
        Date start = date(2023, 1, 1);
        Date end   = date(2024, 1, 1);
        stubMissing("SPY", start);
        stubPrice("SPY", end, 110.0);

        Exception ex = assertThrows(Exception.class, () ->
                calculator.calcCompoundAnnualGrowthRate(
                        new String[]{"SPY"}, new Double[]{1.0}, start, end, 365));

        assertTrue(ex.getMessage().contains("SPY"));
    }

    @Test
    void missingEndPriceThrowsException() throws Exception {
        Date start = date(2023, 1, 1);
        Date end   = date(2024, 1, 1);
        stubPrice("SPY", start, 100.0);
        stubMissing("SPY", end);

        Exception ex = assertThrows(Exception.class, () ->
                calculator.calcCompoundAnnualGrowthRate(
                        new String[]{"SPY"}, new Double[]{1.0}, start, end, 365));

        assertTrue(ex.getMessage().contains("SPY"));
    }

    @Test
    void dbExceptionWrappedAndRethrown() throws Exception {
        Date start = date(2023, 1, 1);
        Date end   = date(2024, 1, 1);
        when(repository.findNearestOnOrBefore(any(), any(), anyInt())).thenThrow(new SQLException("connection refused"));
        when(repository.findNearestOnOrAfter(any(), any(), anyInt())).thenThrow(new SQLException("connection refused"));

        assertThrows(Exception.class, () ->
                calculator.calcCompoundAnnualGrowthRate(
                        new String[]{"SPY"}, new Double[]{1.0}, start, end, 365));
    }

    // ------------------------------------------------------------------
    // getSortedReturnDistribution
    // ------------------------------------------------------------------

    @Test
    void returnDistributionIsSortedAscending() throws Exception {
        // timePeriodDays=3, startDate=Jan1, endDate=Jan10
        // Windows (end starts at Jan4, last=Jan7):
        //   [Jan1 -> Jan4], [Jan2 -> Jan5], [Jan3 -> Jan6]
        // Prices chosen to yield distinct, unsorted CAGRs so we can verify ordering.
        Date jan1  = date(2023, 1, 1);
        Date jan2  = date(2023, 1, 2);
        Date jan3  = date(2023, 1, 3);
        Date jan4  = date(2023, 1, 4);
        Date jan5  = date(2023, 1, 5);
        Date jan6  = date(2023, 1, 6);
        Date jan10 = date(2023, 1, 10);

        // Window 1 [Jan1,Jan4]: 100 → 110  (positive return)
        stubPrice("SPY", jan1, 100.0);
        stubPrice("SPY", jan4, 110.0);
        // Window 2 [Jan2,Jan5]: 100 → 100  (zero return)
        stubPrice("SPY", jan2, 100.0);
        stubPrice("SPY", jan5, 100.0);
        // Window 3 [Jan3,Jan6]: 100 → 90   (negative return)
        stubPrice("SPY", jan3, 100.0);
        stubPrice("SPY", jan6,  90.0);

        Portfolio portfolio = new Portfolio("test",
                new String[]{"SPY"}, new Double[]{1.0});
        PortfolioCalculationRequest req = new PortfolioCalculationRequest(
                "test", portfolio, jan1, jan10, 3, null);

        double[] dist = calculator.getSortedReturnDistribution(req);

        assertEquals(3, dist.length);
        assertTrue(dist[0] < dist[1], "expected dist[0] < dist[1]");
        assertTrue(dist[1] < dist[2], "expected dist[1] < dist[2]");
    }

    @Test
    void returnDistributionEmptyWhenRangeTooNarrow() throws Exception {
        // If the entire date range is smaller than 2 * timePeriodDays there are no windows.
        // startDate=Jan1, endDate=Jan5, timePeriodDays=3
        // end=Jan4, last=Jan2 → end(Jan4) is NOT before last(Jan2) → 0 iterations
        Date jan1 = date(2023, 1, 1);
        Date jan5 = date(2023, 1, 5);

        Portfolio portfolio = new Portfolio("test",
                new String[]{"SPY"}, new Double[]{1.0});
        PortfolioCalculationRequest req = new PortfolioCalculationRequest(
                "test", portfolio, jan1, jan5, 3, null);

        double[] dist = calculator.getSortedReturnDistribution(req);

        assertEquals(0, dist.length);
    }
}
