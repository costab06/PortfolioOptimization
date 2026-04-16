package com.bcfinancial.portfolio;

import com.bcfinancial.data.EquityDataLoader;
import com.bcfinancial.data.EquityRepository;
import com.bcfinancial.instruments.Equity;

import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

public class PortfolioCalculator {
    private static final Logger logger =
        Logger.getLogger(PortfolioCalculator.class.getName());

    static boolean DEBUG = false;

    /** Max calendar days to look back when no price exists on the exact date. */
    private static final int MAX_DAYS_BACK = 30;

    private final EquityRepository repository;
    private final EquityDataLoader  loader;

    /** Earliest date downloaded per symbol — tracks how far back we've fetched. */
    private final ConcurrentHashMap<String, Date> earliestDownloaded = new ConcurrentHashMap<>();

    /** Per-symbol locks so only one thread downloads while others wait. */
    private final ConcurrentHashMap<String, Object> symbolLocks = new ConcurrentHashMap<>();

    public PortfolioCalculator(boolean useCache) {
        // useCache parameter retained for API compatibility
        this.repository = new EquityRepository();
        this.loader     = new EquityDataLoader(repository);
    }

    /** Package-private constructor for unit tests — injects a mock/stub repository. */
    PortfolioCalculator(EquityRepository repository) {
        this.repository = repository;
        this.loader     = new EquityDataLoader(repository);
    }

    public double calcCompoundAnnualGrowthRate(String[] symbols, Double[] weights,
                                               Date startDate, Date endDate,
                                               int timePeriodDays) throws Exception {
        double ret = 0.0;

        for (int i = 0; i < symbols.length; i++) {
            Equity start = getNearestEquityOnOrAfter(symbols[i], startDate);
            if (start == null) {
                throw new Exception("No data for start date for symbol: " + startDate + " " + symbols[i]);
            }

            Equity end = getNearestEquity(symbols[i], endDate);
            if (end == null) {
                throw new Exception("No data for end date for symbol: " + endDate + " " + symbols[i]);
            }

            double base  = end.getAdjClose() / start.getAdjClose();
            double power = 1.0 / (timePeriodDays / 365.0);
            double cagr  = Math.pow(base, power) - 1;
            ret += weights[i] * cagr;
        }

        return ret;
    }

    /**
     * Returns the effective start date to use for {@code symbols}: the requested
     * {@code startDate} clipped forward to the latest earliest-available date
     * across all symbols.  Symbols with no data yet are auto-downloaded first.
     * A warning is logged whenever the start date is pushed forward.
     */
    public Date effectiveStartDate(String[] symbols, Date startDate) throws Exception {
        Date effective = startDate;
        for (String sym : symbols) {
            // Trigger download if needed so findEarliestDate reflects real availability
            ensureDownloaded(sym, startDate);
            try {
                Date earliest = repository.findEarliestDate(sym);
                if (earliest != null && earliest.after(effective)) {
                    logger.warning(sym + " data starts " + earliest
                            + " — clipping start date from " + startDate + " to " + earliest);
                    effective = earliest;
                }
            } catch (java.sql.SQLException e) {
                throw new Exception("DB error finding earliest date for " + sym + ": " + e.getMessage(), e);
            }
        }
        return effective;
    }

    public double[] getSortedReturnDistribution(PortfolioCalculationRequest req) throws Exception {
        String[] symbols    = req.getPortfolio().getSymbols();
        Double[] weights    = req.getPortfolio().getWeights();
        Date startDate      = effectiveStartDate(symbols, req.getStartDate());
        Date endDate        = req.getEndDate();
        int timePeriodDays  = req.getTimePeriodDays();

        List<Double> returns = new ArrayList<>();

        GregorianCalendar start = new GregorianCalendar();
        start.setTime(startDate);

        GregorianCalendar end = new GregorianCalendar();
        end.setTime(startDate);
        end.add(GregorianCalendar.DATE, timePeriodDays);

        GregorianCalendar last = new GregorianCalendar();
        last.setTime(endDate);
        last.add(GregorianCalendar.DATE, -timePeriodDays);

        while (end.before(last)) {
            returns.add(calcCompoundAnnualGrowthRate(symbols, weights,
                    start.getTime(), end.getTime(), timePeriodDays));
            start.add(GregorianCalendar.DATE, 1);
            end.add(GregorianCalendar.DATE, 1);
        }

        Collections.sort(returns);

        double[] ret = new double[returns.size()];
        for (int i = 0; i < ret.length; i++) {
            ret[i] = returns.get(i);
        }
        return ret;
    }

    /**
     * Computes maximum drawdown by stepping through non-overlapping periods and
     * compounding the portfolio value, then finding the largest peak-to-trough decline.
     * Returns a value in [0, 1] where 0.25 means a 25% drawdown.
     */
    public double calcMaxDrawdown(PortfolioCalculationRequest req) throws Exception {
        String[] symbols   = req.getPortfolio().getSymbols();
        Double[] weights   = req.getPortfolio().getWeights();
        Date startDate     = effectiveStartDate(symbols, req.getStartDate());
        Date endDate       = req.getEndDate();
        int timePeriodDays = req.getTimePeriodDays();

        double portfolioValue = 1.0;
        double peak = 1.0;
        double maxDrawdown = 0.0;

        GregorianCalendar pStart = new GregorianCalendar();
        pStart.setTime(startDate);
        GregorianCalendar pEnd = new GregorianCalendar();
        pEnd.setTime(startDate);
        pEnd.add(GregorianCalendar.DATE, timePeriodDays);
        GregorianCalendar endCal = new GregorianCalendar();
        endCal.setTime(endDate);

        while (!pEnd.after(endCal)) {
            double cagr = calcCompoundAnnualGrowthRate(symbols, weights,
                    pStart.getTime(), pEnd.getTime(), timePeriodDays);
            double periodReturn = Math.pow(1.0 + cagr, timePeriodDays / 365.0) - 1.0;
            portfolioValue *= (1.0 + periodReturn);
            if (portfolioValue > peak) peak = portfolioValue;
            double drawdown = (peak - portfolioValue) / peak;
            if (drawdown > maxDrawdown) maxDrawdown = drawdown;
            pStart.add(GregorianCalendar.DATE, timePeriodDays);
            pEnd.add(GregorianCalendar.DATE, timePeriodDays);
        }

        return maxDrawdown;
    }

    // ------------------------------------------------------------------
    // Lookup helpers with auto-download on miss
    // ------------------------------------------------------------------

    private Equity getNearestEquity(String symbol, Date date) throws Exception {
        try {
            Optional<Equity> opt = repository.findNearestOnOrBefore(symbol, date, MAX_DAYS_BACK);
            if (opt.isPresent()) return opt.get();
            ensureDownloaded(symbol, date);
            return repository.findNearestOnOrBefore(symbol, date, MAX_DAYS_BACK).orElse(null);
        } catch (SQLException e) {
            throw new Exception("DB error looking up " + symbol + " near " + date + ": " + e.getMessage(), e);
        }
    }

    private Equity getNearestEquityOnOrAfter(String symbol, Date date) throws Exception {
        try {
            Optional<Equity> opt = repository.findNearestOnOrAfter(symbol, date, MAX_DAYS_BACK);
            if (opt.isPresent()) return opt.get();
            ensureDownloaded(symbol, date);
            return repository.findNearestOnOrAfter(symbol, date, MAX_DAYS_BACK).orElse(null);
        } catch (SQLException e) {
            throw new Exception("DB error looking up " + symbol + " near " + date + ": " + e.getMessage(), e);
        }
    }

    /**
     * Downloads history for {@code symbol} back to at least {@code from} if we
     * haven't already fetched that far.  Per-symbol locking ensures only one
     * thread triggers a download while others wait for it to finish.
     */
    private void ensureDownloaded(String symbol, Date from) {
        Date earliest = earliestDownloaded.get(symbol);
        if (earliest != null && !from.before(earliest)) return; // already covers this range

        Object lock = symbolLocks.computeIfAbsent(symbol, k -> new Object());
        synchronized (lock) {
            earliest = earliestDownloaded.get(symbol);
            if (earliest != null && !from.before(earliest)) return; // another thread beat us

            // Add a small buffer before the requested start date
            Calendar fromCal = Calendar.getInstance();
            fromCal.setTime(from);
            fromCal.add(Calendar.DATE, -30);
            Calendar to = Calendar.getInstance();

            logger.info("No DB data for " + symbol + " — auto-downloading from " + fromCal.getTime());
            try {
                int rows = loader.loadSymbol(symbol, fromCal, to);
                logger.info("Auto-downloaded " + rows + " rows for " + symbol);
                earliestDownloaded.put(symbol, fromCal.getTime());
            } catch (Exception e) {
                logger.warning("Auto-download failed for " + symbol + ": " + e.getMessage());
                // Record the attempt so we don't hammer the server on every miss
                earliestDownloaded.putIfAbsent(symbol, from);
            }
        }
    }
}
