import java.nio.file.*;
import java.nio.file.attribute.*;
import java.io.*;
import java.util.*;
import java.util.logging.*;
import org.apache.commons.io.FilenameUtils;

public class RFile implements Runnable {
	
	private Path origDir; // The base folder of the original files
	private Path copyDir; // The base folder of the copy files
    private Path origFile; // The full path to the original file.
    private Path copyFile; // The full path to the copy file.
    private boolean isOrig;

    private static Logger logger = Logger.getGlobal();

	public RFile(Path origDir, Path copyDir, Path file) {
		this.origDir = origDir;
		this.copyDir = copyDir;

		if(file.startsWith(origDir)) {
	        Path newExt = Paths.get(file.toString() + ".rawsize.jpg");
			copyFile = copyDir.resolve(origDir.relativize(newExt));
			origFile = file;
			isOrig = true;
		}
		else if (file.startsWith(copyDir)) {
	        Path newExt = Paths.get(FilenameUtils.removeExtension(FilenameUtils.removeExtension(file.toString())));
	        origFile = origDir.resolve(copyDir.relativize(newExt));
			copyFile = file;
			isOrig = false;
		}
		else {
			// This is an error. The given file path does not match origDir or copyDir.
			logger.severe("The given file is neither an original file nor a copy.");
		}
	}

	public Path getOrigDir() {
		return origDir;
	}

	public Path getCopyDir() {
		return copyDir;
	}

	public Path getOrigFile() {
		return origFile;
	}

	public Path getCopyFile() {
		return copyFile;
	}

	public boolean isOrig() {
		return isOrig;
	}

	public void delete() {
		// TODO
        logger.info("Removing both original and copy files: " + origFile + " and " + copyFile + ".");
        try {
    	    Files.deleteIfExists(origFile); // TODO: Move the file to a safe location instead of deleting it.
        }
        catch (Exception ex) {
            logger.warning("Could not delete file. Exception: " + ex);
        }
        try {
        	Files.deleteIfExists(copyFile);
        }
        catch (Exception ex) {
            logger.warning("Could not delete file. Exception: " + ex);
        }
	}

	/**
	 * Converts an original image into a resized copy using
	 * a separate thread. Use the ExecutorService to create
	 * a thread pool and execute a new instance of the class
	 * in order to perform the image conversion.
	**/
	public void convert() {
		// Firstly make sure that the destination directory exists.
		boolean destinationDirExists = (new File(FilenameUtils.getPath(copyFile.toString()))).mkdirs();
     	if (!destinationDirExists) {
			// Directory creation failed
			logger.warning("Could not create destination directory.");
		}

		// Secondly, run the convert system command.
        Runtime r = Runtime.getRuntime();
        try{
        	// Obsolete: String cmd = "./bin/nconvert_mac -quiet -o " + copyFile + " -out jpeg -q 50 -overwrite -resize 1024 1024 -ratio -raw_autobalance " + origFile;
            // Obsolete: String cmd = "convert -resize 1024x1024 -format jpg -quality 75 " + origFile + " " + copyFile;
            String cmd = "bin/rawtherapee_linux64/rawtherapee -Y -j80 -d -p resize_1024x1024.pp3 -o " + copyFile + " -c " + origFile;
            // Command line options to rawtherapee:
            //  -Y      Overwrite output file if it exists
            //  -j80    Output in JPEG quality 80
            //  -d      Load default rawtherapee settings
            //  -p <>   Overwrite the default setting with custom ones, in this case resize settings.
            //  -o <>   Output file name
            //  -c <>   The file to be converted

            logger.info("Executing system command: " + cmd);
            Process p = r.exec(cmd);
            p.waitFor();

            int e = p.exitValue();
            if(e != 0)
                logger.warning("Process ended with exit value " + e + ", indicating an error.");
        }
        catch (Exception ex) {
            logger.warning("Caught exception: " + ex);
        }
	}

    @Override
    public void run() {
    	convert();
    }

}