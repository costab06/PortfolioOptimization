package com.independencefinancial.computeFarmTasksJobs.squares;

import org.tiling.computefarm.JobRunner;
import org.tiling.computefarm.JobRunnerFactory;
import org.tiling.computefarm.impl.javaspaces.util.ClasspathServer;

public class SquaresClient {

    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            System.err.println(
                "Usage: java " + SquaresClient.class.getName() + " <n>");
            System.exit(1);
        }

        ClasspathServer server = new ClasspathServer();
        server.start();

        int n = Integer.parseInt(args[0]);
        SquaresJob job = new SquaresJob(n);
        JobRunnerFactory factory = JobRunnerFactory.newInstance();
        JobRunner jobRunner = factory.newJobRunner(job);
        jobRunner.run();

        System.out.println("n = " + n);
        System.out.println("Sum of squares = " + job.getSumOfSquares());
        System.out.println("n * (n + 1) * (2 * n + 1) / 6 = "
                + (n * (n + 1) * (2 * n + 1) / 6));

    }

}
