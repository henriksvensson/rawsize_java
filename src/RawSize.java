import java.nio.file.*;
import java.nio.file.attribute.*;
import java.io.*;
import java.util.*;
import java.util.logging.*;
import java.util.concurrent.*;

public class RawSize {

    private static Logger logger = Logger.getGlobal();

    static void usage() {
        System.err.println("Usage: WatchDir [orignal path] [copy path]");
        System.exit(-1);
    }

    public static void main(String[] args) throws IOException {

        // Set up the logger.
        logger.setLevel(Level.FINE); // Does not seem to have any effect.
        logger.info("Logger started successfully.");

        // Make sure we have the correct number of arguments.
        if(args.length != 2) {
                logger.severe("The application was not given the correct number of arguments.");
                usage();
            }

        // Set the paths given as command line arguments.
        Path orig = Paths.get(args[0]);
        Path copy = Paths.get(args[1]);

        // Create a thread pool to handle the image conversions
        ExecutorService executor = Executors.newFixedThreadPool(2); // TODO: How many threads should be allowed?

        // Create the main directory watcher service and start
        // monitoring for changes.
        executor.execute(new WatchDir(orig, copy, executor));

        // TODO: Do more interface stuff
        while (true) {
            try {
                logger.info("Currently active threads: " + ((ThreadPoolExecutor)executor).getActiveCount());
                logger.info(((ThreadPoolExecutor)executor).toString());
                Thread.sleep(500);
            }
            catch(Exception ex) {
                logger.warning("Wait loop exception caught. " + ex);
            }
        }
    }
}
