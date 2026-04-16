package com.independencefinancial.computeFarmTasksJobs.squares;

import java.io.Serializable;
import java.util.logging.Logger;

import org.tiling.computefarm.Task;

public class SquaresTask implements Serializable, Task {

    private static final Logger logger =
        Logger.getLogger(SquaresTask.class.getName());

    private final int k;

    public SquaresTask(int k) {
        this.k = k;
    }

    public Object execute() {
        logger.info("Version 0");
        logger.info("Computing square of " + k);

        return new Integer(k * k);
    }

}
