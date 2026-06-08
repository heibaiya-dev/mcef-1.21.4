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

package com.cinemamod.mcef;

import com.cinemamod.mcef.internal.MCEFDownloadListener;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.apache.commons.io.FileUtils;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * A downloader and extraction tool for java-cef builds.
 * <p>
 * Downloads for <a href="https://github.com/CinemaMod/java-cef">CinemaMod java-cef</a> are provided by the CinemaMod Group unless changed
 * in the MCEFSettings properties file; see {@link MCEFSettings}.
 * Email ds58@mailbox.org for any questions or concerns regarding the file hosting.
 */
public class MCEFDownloader {
    private static final String JAVA_CEF_DOWNLOAD_URL = "${host}/java-cef-builds/${java-cef-commit}/${platform}.tar.gz";
    private static final String JAVA_CEF_CHECKSUM_DOWNLOAD_URL = "${host}/java-cef-builds/${java-cef-commit}/${platform}.tar.gz.sha256";

    private final String host;
    private final String javaCefCommitHash;
    private final MCEFPlatform platform;

    public MCEFDownloader(String host, String javaCefCommitHash, MCEFPlatform platform) {
        this.host = host;
        this.javaCefCommitHash = javaCefCommitHash;
        this.platform = platform;
    }

    public String getHost() {
        return host;
    }

    public String getJavaCefDownloadUrl() {
        return formatURL(JAVA_CEF_DOWNLOAD_URL);
    }

    public String getJavaCefChecksumDownloadUrl() {
        return formatURL(JAVA_CEF_CHECKSUM_DOWNLOAD_URL);
    }

    private String formatURL(String url) {
        return url
                .replace("${host}", host)
                .replace("${java-cef-commit}", javaCefCommitHash)
                .replace("${platform}", platform.getNormalizedName());
    }

    public void downloadJavaCefBuild() throws IOException {
        File mcefLibrariesPath = new File(System.getProperty("mcef.libraries.path"));
        MCEFDownloadListener.INSTANCE.setTask("Downloading Chromium Embedded Framework");
        downloadFile(getJavaCefDownloadUrl(), new File(mcefLibrariesPath, platform.getNormalizedName() + ".tar.gz"));
    }

    /**
     * Validates the downloaded tar.gz file against the expected SHA256 checksum.
     * @return true if the file hash matches the expected hash, false otherwise
     * @throws IOException if an I/O error occurs
     */
    public boolean validateJavaCefBuild() throws IOException {
        File mcefLibrariesPath = new File(System.getProperty("mcef.libraries.path"));
        File tarGzArchive = new File(mcefLibrariesPath, platform.getNormalizedName() + ".tar.gz");
        File checksumFile = new File(mcefLibrariesPath, platform.getNormalizedName() + ".tar.gz.sha256");

        if (!tarGzArchive.exists() || !checksumFile.exists()) {
            return false;
        }

        String expectedHash = FileUtils.readFileToString(checksumFile, StandardCharsets.UTF_8).trim().split("\\s+")[0];
        String actualHash = sha256(tarGzArchive);

        return expectedHash.equalsIgnoreCase(actualHash);
    }

    private static String sha256(File file) throws IOException {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }

        try (InputStream inputStream = Files.newInputStream(file.toPath())) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = inputStream.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
        }

        byte[] hash = digest.digest();

        StringBuilder hexString = new StringBuilder();
        for (byte b : hash) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) hexString.append('0');
            hexString.append(hex);
        }
        return hexString.toString();
    }

    /**
     * @return true if the jcef build checksum file matches the remote checksum file (for the {@link MCEFDownloader#javaCefCommitHash}),
     * false if the jcef build checksum file did not exist or did not match; this means we should redownload JCEF
     * @throws IOException
     */
    public boolean downloadJavaCefChecksum() throws IOException {
        File mcefLibrariesPath = new File(System.getProperty("mcef.libraries.path"));
        File jcefBuildHashFileTemp = new File(mcefLibrariesPath, platform.getNormalizedName() + ".tar.gz.sha256.temp");
        File jcefBuildHashFile = new File(mcefLibrariesPath, platform.getNormalizedName() + ".tar.gz.sha256");

        MCEFDownloadListener.INSTANCE.setTask("Downloading Checksum");
        downloadFile(getJavaCefChecksumDownloadUrl(), jcefBuildHashFileTemp);

        if (jcefBuildHashFile.exists()) {
            boolean sameContent = FileUtils.contentEquals(jcefBuildHashFile, jcefBuildHashFileTemp);
            if (sameContent) {
                jcefBuildHashFileTemp.delete();
                return true;
            } else {
                MCEF.getLogger().warn("JCEF Hash does not match.");
            }
        } else {
            MCEF.getLogger().warn("Failed to download JCEF hash.");
        }

        jcefBuildHashFileTemp.renameTo(jcefBuildHashFile);

        return false;
    }

    public void extractJavaCefBuild(boolean delete) {
        File mcefLibrariesPath = new File(System.getProperty("mcef.libraries.path"));
        File tarGzArchive = new File(mcefLibrariesPath, platform.getNormalizedName() + ".tar.gz");
        extractTarGz(tarGzArchive, mcefLibrariesPath);
        if (delete && tarGzArchive.exists()) {
            tarGzArchive.delete();
        }
    }

    private static void downloadFile(String urlString, File outputFile) throws IOException {
        MCEF.getLogger().info(urlString + " -> " + outputFile.getCanonicalPath());

        URL url = new URL(urlString);
        HttpURLConnection urlConnection = (HttpURLConnection) url.openConnection();
        try {
            urlConnection.setConnectTimeout(30000);
            urlConnection.setReadTimeout(30000);
            urlConnection.setInstanceFollowRedirects(true);
            urlConnection.setRequestProperty("User-Agent", "MCEF");

            if (urlConnection.getResponseCode() != 200) {
                throw new IOException("HTTP " + urlConnection.getResponseCode() + " for URL: " + urlString);
            }

            int fileSize = urlConnection.getContentLength();

            try (BufferedInputStream inputStream = new BufferedInputStream(urlConnection.getInputStream());
                 FileOutputStream outputStream = new FileOutputStream(outputFile)) {

                byte[] buffer = new byte[2048];
                int count;
                int readBytes = 0;
                while ((count = inputStream.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, count);
                    readBytes += count;
                    float percentComplete = fileSize > 0 ? (float) readBytes / fileSize : 0f;
                    MCEFDownloadListener.INSTANCE.setProgress(percentComplete);
                }
            }
        } catch (IOException e) {
            // Clean up partial download on failure
            if (outputFile.exists()) {
                outputFile.delete();
            }
            throw new IOException("Failed to download " + urlString, e);
        } finally {
            urlConnection.disconnect();
        }
    }

    private static void extractTarGz(File tarGzFile, File outputDirectory) {
        MCEFDownloadListener.INSTANCE.setTask("Extracting");

        outputDirectory.mkdirs();

        long fileSize = tarGzFile.length();
        long totalBytesRead = 0;

        try (TarArchiveInputStream tarInput = new TarArchiveInputStream(new GzipCompressorInputStream(new FileInputStream(tarGzFile)))) {
            TarArchiveEntry entry;
            while ((entry = tarInput.getNextTarEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }

                if (entry.isSymbolicLink() || entry.getLinkName() != null && !entry.getLinkName().isEmpty()) {
                    MCEF.getLogger().warn("Skipping link tar entry: " + entry.getName());
                    continue;
                }

                String entryName = entry.getName().replace('\\', '/');
                Path outputPath = outputDirectory.toPath().toAbsolutePath().normalize();
                Path entryPath = outputPath.resolve(entryName).normalize();
                if (!entryPath.startsWith(outputPath)) {
                    MCEF.getLogger().warn("Skipping potentially malicious tar entry: " + entry.getName());
                    continue;
                }

                File outputFile = entryPath.toFile();
                outputFile.getParentFile().mkdirs();

                try (OutputStream outputStream = new FileOutputStream(outputFile)) {
                    byte[] buffer = new byte[4096];
                    int bytesRead;
                    while ((bytesRead = tarInput.read(buffer)) != -1) {
                        outputStream.write(buffer, 0, bytesRead);
                        totalBytesRead += bytesRead;
                        float percentComplete = fileSize > 0 ? (((float) totalBytesRead / fileSize) / 2.6158204f) : 0f; // Roughly the compression ratio
                        MCEFDownloadListener.INSTANCE.setProgress(percentComplete);
                    }
                }
            }
        } catch (IOException e) {
            MCEF.getLogger().error("Failed to extract gzip file to " + outputDirectory, e);
        }

        MCEFDownloadListener.INSTANCE.setProgress(1.0f);
    }
}
