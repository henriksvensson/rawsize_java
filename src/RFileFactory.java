import java.nio.file.*;
import java.nio.file.attribute.*;
import java.io.*;
import java.util.*;
import java.util.logging.*;
import java.util.concurrent.*;

public class RFileFactory {
	
	private Path orig;
	private Path copy;

	public RFileFactory(Path orig, Path copy) {
		this.orig = orig;
		this.copy = copy;
	}

	public RFile getRFile(Path file) {
		return new RFile(orig, copy, file);
	}
}