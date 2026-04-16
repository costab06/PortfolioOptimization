package com.independencefinancial.computeFarmTasksJobs.squares;

import java.util.logging.Level;
import java.util.logging.Logger;

import org.tiling.computefarm.CancelledException;
import org.tiling.computefarm.CannotTakeResultException;
import org.tiling.computefarm.CannotWriteTaskException;
import org.tiling.computefarm.ComputeSpace;
import org.tiling.computefarm.Job;

public class SquaresJob implements Job {

    private static final Logger logger =
        Logger.getLogger(SquaresJob.class.getName());

    private final int n;
    private int sum;

    public SquaresJob(int n) {
        this.n = n;
    }

    public void generateTasks(ComputeSpace space) {
        for (int i = 1; i <= n; i++) {
            try {
                space.write(new SquaresTask(i));
            } catch (CannotWriteTaskException e) {
                logger.log(Level.INFO, "Problem writing task " + i
                        + " to space.", e);
                break;
            } catch (CancelledException e) {
                logger
                        .info("Cancelled after writing " + i
                                + " tasks to space.");
                break;
            }
        }
    }

    public void collectResults(ComputeSpace space) {
        sum = 0;
        for (int i = 1; i <= n; i++) {
            try {
                Integer result = (Integer) space.take();
                sum += result.intValue();
            } catch (CannotTakeResultException e) {
                logger.log(Level.INFO, "Problem taking result " + i
                        + " from space.", e);
                break;
            } catch (CancelledException e) {
                logger.info("Cancelled after retrieving " + i
                        + " results from space.");
                sum = 0;
                break;
            }
        }
    }

    public int getSumOfSquares() {
        return sum;
    }

}
