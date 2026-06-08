/*
 *     MCEF (Minecraft Chromium Embedded Framework)
 *     Copyright (C) 2023 CinemaMod Group
 *
 *     This library is free software; you can redistribute it and/or
 *     modify it under the terms of the GNU Lesser General Public
 *     License as published by the Free Software Foundation; either
 *     version 2.1 of the License, or (at your option) any later version.
 *
 *     This library is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 *     Lesser General Public License for more details.
 *
 *     You should have received a copy of the GNU Lesser General Public
 *     License along with this library; if not, write to the Free Software
 *     Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301
 *     USA
 */

package com.cinemamod.mcef.internal;

import com.cinemamod.mcef.MCEF;
import com.cinemamod.mcef.MCEFPlatform;

import java.io.*;
import java.nio.file.*;
import java.util.Enumeration;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * Extracts bundled native CEF binaries from the mod JAR at runtime.
 * Binaries are stored under {@code natives/<platform>/} in the JAR resources
 * and extracted to {@code mcef-libraries/<platform>/}.
 */
public class NativeExtractor {

    private static final String NATIVES_RESOURCE_PREFIX = "natives/";

    /**
     * Checks if native binaries are bundled in the JAR for the current platform.
     *
     * @return true if bundled natives exist, false otherwise
     */
    public static boolean hasBundledNatives() {
        String resourcePath = NATIVES_RESOURCE_PREFIX + MCEFPlatform.getPlatform().getNormalizedName();
        try (InputStream is = NativeExtractor.class.getResourceAsStream("/" + resourcePath)) {
            return is != null;
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * Extracts bundled native binaries from the JAR to the target directory.
     * Skips files that already exist and have the same size (idempotent).
     *
     * @param targetDir the directory to extract binaries to (mcef-libraries path)
     * @return true if extraction succeeded or binaries were already present
     */
    public static boolean extractNatives(File targetDir) {
        String platformName = MCEFPlatform.getPlatform().getNormalizedName();
        String resourcePrefix = NATIVES_RESOURCE_PREFIX + platformName + "/";

        File platformDir = new File(targetDir, platformName);
        platformDir.mkdirs();

        try {
            File jarFile = getModJarFile();
            if (jarFile == null) {
                MCEF.getLogger().error("Could not locate mod JAR file for native extraction.");
                return false;
            }

            MCEF.getLogger().info("Extracting bundled natives from " + jarFile.getName() + " for " + platformName);

            int extractedCount = 0;
            try (JarFile jar = new JarFile(jarFile)) {
                Enumeration<JarEntry> entries = jar.entries();
                while (entries.hasMoreElements()) {
                    JarEntry entry = entries.nextElement();
                    if (!entry.getName().startsWith(resourcePrefix) || entry.isDirectory()) {
                        continue;
                    }

                    String fileName = entry.getName().substring(resourcePrefix.length());
                    if (fileName.isEmpty()) continue;

                    File outputFile = new File(platformDir, fileName);

                    // Skip if file already exists with same size
                    if (outputFile.exists() && outputFile.length() == entry.getSize()) {
                        continue;
                    }

                    outputFile.getParentFile().mkdirs();

                    try (InputStream in = jar.getInputStream(entry);
                         OutputStream out = new FileOutputStream(outputFile)) {
                        byte[] buffer = new byte[8192];
                        int bytesRead;
                        while ((bytesRead = in.read(buffer)) != -1) {
                            out.write(buffer, 0, bytesRead);
                        }
                    }

                    extractedCount++;

                    // Set executable permission on Unix
                    if (MCEFPlatform.getPlatform().isLinux() || MCEFPlatform.getPlatform().isMacOS()) {
                        try {
                            java.nio.file.Files.setPosixFilePermissions(
                                    outputFile.toPath(),
                                    java.util.Set.of(
                                            java.nio.file.attribute.PosixFilePermission.OWNER_READ,
                                            java.nio.file.attribute.PosixFilePermission.OWNER_WRITE,
                                            java.nio.file.attribute.PosixFilePermission.OWNER_EXECUTE
                                    )
                            );
                        } catch (IOException | UnsupportedOperationException e) {
                            // Ignore on non-POSIX systems
                        }
                    }
                }
            }

            MCEF.getLogger().info("Extracted " + extractedCount + " native files for " + platformName);
            return true;

        } catch (IOException e) {
            MCEF.getLogger().error("Failed to extract bundled natives.", e);
            return false;
        }
    }

    /**
     * Returns the File object for the mod JAR containing this class.
     */
    private static File getModJarFile() {
        try {
            java.net.URL url = NativeExtractor.class.getProtectionDomain().getCodeSource().getLocation();
            if (url != null) {
                File file = new File(url.toURI());
                if (file.isFile()) {
                    return file;
                }
            }
        } catch (Exception e) {
            MCEF.getLogger().warn("Could not get code source location, trying alternative method.", e);
        }

        // Fallback: try to find the JAR via class resource path
        try {
            java.net.URL resource = NativeExtractor.class.getResource("/" + NATIVES_RESOURCE_PREFIX);
            if (resource != null) {
                String path = resource.getPath();
                // Remove "file:" prefix and "natives/" suffix
                if (path.startsWith("file:")) {
                    path = path.substring(5);
                }
                // If running from JAR, path will be like /path/to/mod.jar!/natives/
                int bangIndex = path.indexOf("!");
                if (bangIndex > 0) {
                    return new File(path.substring(0, bangIndex));
                }
                // If running from exploded classes, return the root
                return new File(path).getParentFile();
            }
        } catch (Exception e) {
            MCEF.getLogger().warn("Alternative method also failed.", e);
        }

        return null;
    }
}
