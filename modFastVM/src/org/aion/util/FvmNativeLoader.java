package org.aion.util;

import java.io.File;
import java.io.IOException;
import java.util.Scanner;

public final class FvmNativeLoader {

    /**
     * Loads the Fvm native libraries.
     */
    public static void loadLibrary() {
        File dir = buildPath("native", getOS(), "fastvm");

        try (Scanner s = new Scanner(new File(dir, "file.list"))) {
            while (s.hasNextLine()) {
                String line = s.nextLine();

                if (line.startsWith("/") || line.startsWith(".")) { // for debug
                    // purpose
                    // mainly
                    System.load(line);
                } else {
                    System.load(new File(dir, line).getCanonicalPath());
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to load the fvm native libraries ", e);
        }
    }

    /**
     * Builds a file path given a list of folder names.
     *
     * @param args list of folder names
     * @return file object
     */
    private static File buildPath(String... args) {
        StringBuilder sb = new StringBuilder();
        for (String arg : args) {
            sb.append(File.separator);
            sb.append(arg);
        }

        return sb.length() > 0 ? new File(sb.substring(1)) : new File(".");
    }

    /**
     * Returns the current OS name.
     *
     * @return current system OS name.
     */
    private static String getOS() {
        String osName = System.getProperty("os.name").toLowerCase();
        if (osName.contains("win")) {
            return "win";
        } else if (osName.contains("linux")) {
            return "linux";
        } else if (osName.contains("mac")) {
            return "mac";
        } else {
            throw new RuntimeException("Unrecognized OS: " + osName);
        }
    }
}
