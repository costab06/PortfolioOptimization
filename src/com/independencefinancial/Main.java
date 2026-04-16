package com.bcfinancial;

import com.bcfinancial.api.ApiHandler;
import com.bcfinancial.api.AppService;
import com.bcfinancial.api.StaticHandler;
import com.sun.net.httpserver.HttpServer;

import java.net.InetSocketAddress;
import java.util.concurrent.Executors;
import java.util.logging.Logger;

/**
 * Entry point for the Portfolio Optimization server.
 *
 * <p>Starts an embedded HTTP server (JDK built-in, no external container) and
 * registers the REST API handler at {@code /api/*}.  The database is H2
 * (file-based, zero-install) by default; switch to MariaDB by editing
 * {@code application.properties}.
 *
 * <h2>Usage</h2>
 * <pre>
 *   java -jar portfoliooptimization-standalone.jar [--port=8080]
 * </pre>
 *
 * <h2>Quick-start</h2>
 * <pre>
 *   # 1. Load 5 years of equity data
 *   java -cp portfoliooptimization-standalone.jar \
 *        com.bcfinancial.data.EquityDataLoader --full SPY AAPL MSFT
 *
 *   # 2. Start the server
 *   java -jar portfoliooptimization-standalone.jar
 *
 *   # 3. Register, create a portfolio, and optimize via the REST API
 *   curl -X POST http://localhost:8080/api/users/register \
 *        -H 'Content-Type: application/json' \
 *        -d '{"username":"alice","password":"secret","email":"a@example.com"}'
 * </pre>
 */
public class Main {

    private static final Logger logger = Logger.getLogger(Main.class.getName());

    public static void main(String[] args) throws Exception {
        int port = 8080;
        for (String arg : args) {
            if (arg.startsWith("--port=")) {
                port = Integer.parseInt(arg.substring("--port=".length()));
            }
        }

        AppService service = new AppService();

        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/api", new ApiHandler(service));
        server.createContext("/",    new StaticHandler());
        server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
        server.start();

        logger.info("=================================================");
        logger.info("Portfolio Optimization Server  port=" + port);
        logger.info("UI:  http://localhost:" + port + "/");
        logger.info("API: http://localhost:" + port + "/api/health");
        logger.info("Press Ctrl-C to stop.");
        logger.info("=================================================");

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            logger.info("Shutdown requested — stopping server...");
            server.stop(5);
            service.close();
        }, "shutdown-hook"));
    }
}
