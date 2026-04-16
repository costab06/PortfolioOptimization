package com.bcfinancial.executor;

import com.bcfinancial.portfolio.Portfolio;
import com.bcfinancial.portfolio.PortfolioCalculatedData;
import com.bcfinancial.portfolio.PortfolioCalculationRequest;
import com.bcfinancial.portfolio.PortfolioCalculator;
import com.bcfinancial.utility.UtilityFunction;

import java.io.Closeable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Replaces the ComputeFarm/Jini/JavaSpaces distributed execution layer with
 * Java-native parallel execution using {@link ExecutorService} and
 * {@link CompletableFuture}.
 *
 * <p>The original pattern was:
 * <pre>
 *   Job.generateTasks() → ComputeSpace.write(task) → remote workers → ComputeSpace.take()
 * </pre>
 *
 * <p>The new pattern is:
 * <pre>
 *   generateVariants() → CompletableFuture.supplyAsync(task, executor) → join()
 * </pre>
 *
 * <p>All portfolio variant calculations run in parallel on the local JVM using a
 * fixed thread pool sized to the number of available CPU cores.  A single shared
 * {@link PortfolioCalculator} (and therefore a single shared database connection
 * pool) is reused across all tasks to avoid connection exhaustion.
 */
public class PortfolioParallelExecutor implements Closeable {

    private static final Logger logger =
            Logger.getLogger(PortfolioParallelExecutor.class.getName());

    private final ExecutorService executor;
    private final PortfolioCalculator calculator;

    /**
     * Creates an executor using all available CPU cores.
     * The underlying database connection pool is configured by
     * {@code application.properties} (db.pool.size).
     */
    public PortfolioParallelExecutor() {
        int parallelism = Runtime.getRuntime().availableProcessors();
        logger.info("PortfolioParallelExecutor initialised with " + parallelism + " threads");
        this.executor = Executors.newFixedThreadPool(parallelism);
        this.calculator = new PortfolioCalculator(false);
    }

    // -----------------------------------------------------------------------
    // Public API – mirrors the old Job/JobRunner interface
    // -----------------------------------------------------------------------

    /**
     * Calculates metrics for a single portfolio.
     * Replaces {@code PortfolioCalculateComputeFarmJob} (single-task jobs used
     * for the SP500 benchmark and the original portfolio baseline).
     */
    public PortfolioCalculatedData calculate(String name,
                                             Portfolio portfolio,
                                             Date startDate,
                                             Date endDate,
                                             int timePeriodDays,
                                             UtilityFunction utilityFunction) {
        PortfolioCalculationRequest request =
                new PortfolioCalculationRequest(name, portfolio, startDate, endDate,
                        timePeriodDays, utilityFunction);
        return executeTask(request);
    }

    /**
     * Generates one weight-shifted variant per instrument and evaluates all
     * variants in parallel, using the default step size of {@value #STEP}.
     *
     * <p>Replaces {@code PortfolioOptimizeComputeFarmJob}: the old
     * {@code generateTasks()} + {@code collectResults()} loop.
     *
     * @return one {@link PortfolioCalculatedData} per instrument in the portfolio,
     *         preserving the same order as {@code portfolio.getSymbols()}
     */
    public List<PortfolioCalculatedData> calculateVariants(Portfolio portfolio,
                                                           Date startDate,
                                                           Date endDate,
                                                           int timePeriodDays,
                                                           UtilityFunction utilityFunction,
                                                           double maxWeightPerInstrument) {
        return calculateVariants(portfolio, startDate, endDate, timePeriodDays,
                utilityFunction, maxWeightPerInstrument, STEP);
    }

    /**
     * Generates one weight-shifted variant per instrument and evaluates all
     * variants in parallel, using the supplied {@code step} size.
     *
     * <p>Momentum-based callers pass a step scaled by the current epoch streak
     * (e.g. {@code 0.01 * min(streak + 1, 5)}) so the optimiser moves faster
     * when it is on a consistent improvement run.
     *
     * @param step  weight increment per epoch (e.g. 0.01 = 1 percentage point)
     */
    public List<PortfolioCalculatedData> calculateVariants(Portfolio portfolio,
                                                           Date startDate,
                                                           Date endDate,
                                                           int timePeriodDays,
                                                           UtilityFunction utilityFunction,
                                                           double maxWeightPerInstrument,
                                                           double step) {
        List<PortfolioCalculationRequest> variants =
                generateVariants(portfolio, startDate, endDate, timePeriodDays, utilityFunction,
                        maxWeightPerInstrument, step);

        logger.info("Submitting " + variants.size() + " portfolio variants for parallel evaluation (step=" + step + ")");

        List<CompletableFuture<PortfolioCalculatedData>> futures = variants.stream()
                .map(req -> CompletableFuture.supplyAsync(() -> executeTask(req), executor))
                .collect(Collectors.toList());

        return futures.stream()
                .map(CompletableFuture::join)
                .collect(Collectors.toList());
    }

    // -----------------------------------------------------------------------
    // Internal helpers
    // -----------------------------------------------------------------------

    /** Absolute weight step per epoch (1 percentage point). */
    private static final double STEP = 0.01;

    /**
     * Builds one {@link PortfolioCalculationRequest} per symbol where that
     * symbol's weight is increased by {@code step} and every other symbol's
     * weight is reduced by {@code step / (length - 1)} equally, so the total
     * allocation stays at 100%.  Variants where any weight would go negative
     * or would exceed the per-instrument cap are skipped.
     *
     * <p>Mirrors {@code PortfolioOptimizeComputeFarmJob.generateTasks()}.
     */
    private List<PortfolioCalculationRequest> generateVariants(Portfolio portfolio,
                                                               Date startDate,
                                                               Date endDate,
                                                               int timePeriodDays,
                                                               UtilityFunction utilityFunction,
                                                               double maxWeightPerInstrument,
                                                               double step) {
        int length = portfolio.getWeights().length;
        List<PortfolioCalculationRequest> variants = new ArrayList<>(length);
        if (length <= 1) return variants;

        for (int i = 0; i < length; i++) {
            Double[] tmpWeights = Arrays.copyOf(portfolio.getWeights(), length);

            // Skip if boosted weight would exceed the per-instrument cap
            if (tmpWeights[i] + step > maxWeightPerInstrument) continue;

            // Proportional reduction: each donor gives up weight pro-rata to its
            // current holding.  This is always feasible — no donor goes negative —
            // and works correctly for any portfolio size or weight distribution.
            double donorSum = 0;
            for (int j = 0; j < length; j++) {
                if (j != i && tmpWeights[j] > 0.0) donorSum += tmpWeights[j];
            }
            if (donorSum <= 0) continue; // nowhere to take weight from

            tmpWeights[i] += step;
            for (int j = 0; j < length; j++) {
                if (j != i && tmpWeights[j] > 0.0) {
                    tmpWeights[j] -= step * (tmpWeights[j] / donorSum);
                }
            }

            Portfolio variant = new Portfolio(
                    portfolio.getPortfolioName() + "_" + i,
                    portfolio.getSymbols(),
                    tmpWeights);

            variants.add(new PortfolioCalculationRequest(
                    portfolio.getSymbols()[i], variant,
                    startDate, endDate, timePeriodDays, utilityFunction));
        }

        return variants;
    }

    /**
     * Executes one portfolio calculation task.
     * Mirrors {@code PortfolioComputeFarmTask.execute()}.
     *
     * <p>Exceptions are captured in the returned {@link PortfolioCalculatedData}
     * via {@link PortfolioCalculatedData#setException(Exception)} so that the
     * caller can decide how to handle them after all futures complete.
     */
    private PortfolioCalculatedData executeTask(PortfolioCalculationRequest request) {
        logger.info("Computing portfolio: " + request.getPortfolioName());
        try {
            double[] sortedReturns = calculator.getSortedReturnDistribution(request);
            double[] sortedUtilities = request.getUtilityFunction().getUtilities(sortedReturns);
            double weightedUtility = request.getUtilityFunction().getWeightedUtility(sortedReturns);
            double maxDrawdown = 0.0;
            try {
                maxDrawdown = calculator.calcMaxDrawdown(request);
            } catch (Exception e) {
                logger.warning("Could not compute max drawdown for " + request.getPortfolioName() + ": " + e.getMessage());
            }
            double fullPeriodCagr = 0.0;
            try {
                // Use the effective start date (clipped to the latest symbol inception)
                // so symbols with shorter histories don't produce a 0 CAGR.
                Date effectiveStart = calculator.effectiveStartDate(
                        request.getPortfolio().getSymbols(), request.getStartDate());
                long daysLong = (request.getEndDate().getTime() - effectiveStart.getTime()) / (1000L * 60 * 60 * 24);
                int totalDays = (int) Math.max(1, daysLong);
                fullPeriodCagr = calculator.calcCompoundAnnualGrowthRate(
                        request.getPortfolio().getSymbols(), request.getPortfolio().getWeights(),
                        effectiveStart, request.getEndDate(), totalDays);
            } catch (Exception e) {
                logger.warning("Could not compute full-period CAGR for " + request.getPortfolioName() + ": " + e.getMessage());
            }
            return new PortfolioCalculatedData(request, sortedReturns, sortedUtilities, weightedUtility, maxDrawdown, fullPeriodCagr);
        } catch (Exception e) {
            logger.warning("Exception computing portfolio " + request.getPortfolioName() + ": " + e);
            // Return a sentinel result – the caller checks getException() and can either
            // skip this variant or propagate the error.
            PortfolioCalculatedData failed =
                    new PortfolioCalculatedData(request, new double[0], new double[0], Double.NEGATIVE_INFINITY, 0.0, 0.0);
            failed.setException(e);
            return failed;
        }
    }

    // -----------------------------------------------------------------------
    // Lifecycle
    // -----------------------------------------------------------------------

    @Override
    public void close() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(30, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
