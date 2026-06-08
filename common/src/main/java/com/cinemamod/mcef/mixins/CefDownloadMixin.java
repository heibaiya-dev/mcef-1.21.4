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

package com.cinemamod.mcef.mixins;

import com.cinemamod.mcef.MCEF;
import com.cinemamod.mcef.MCEFDownloader;
import com.cinemamod.mcef.MCEFPlatform;
import com.cinemamod.mcef.MCEFSettings;
import com.cinemamod.mcef.internal.MCEFDownloadListener;
import com.cinemamod.mcef.internal.NativeExtractor;
import net.minecraft.client.resources.ClientPackSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.io.File;
import java.io.IOException;

/**
 * <p>
 * mcef.libraries.path is where MCEF will store any required binaries. By default,
 * /path/to/.minecraft/mods/mcef-libraries.
 * <p>
 * jcef.path is the location of the standard java-cef bundle. By default,
 * /path/to/mcef-libraries/<normalized platform name> where normalized platform name comes from
 * {@link MCEFPlatform#getNormalizedName()}. This is what java-cef uses internally to find the
 * installation. Also see {@link org.cef.CefApp}.
 *
 * <p>
 * Native binaries are bundled inside the mod JAR under {@code natives/<platform>/}.
 * At startup, they are extracted to the mcef-libraries directory. If bundled natives
 * are not available, the mod falls back to downloading them from the remote server.
 */
@Mixin(ClientPackSource.class)
public class CefDownloadMixin {
    @Unique
    private static void setupLibraryPath() throws IOException {
        final File mcefLibrariesDir;

        // Check for development environment
        // TODO: handle eclipse/others
        // i.e. mcef-repo/neoforge/build
        File buildDir = new File("../build");
        if (buildDir.exists() && buildDir.isDirectory()) {
            mcefLibrariesDir = new File(buildDir, "mcef-libraries/");
        } else {
            mcefLibrariesDir = new File("mods/mcef-libraries/");
        }

        mcefLibrariesDir.mkdirs();

        System.setProperty("mcef.libraries.path", mcefLibrariesDir.getCanonicalPath());
        System.setProperty("jcef.path", new File(mcefLibrariesDir, MCEFPlatform.getPlatform().getNormalizedName()).getCanonicalPath());
    }

    @Inject(at = @At("HEAD"), method = "<clinit>")
    private static void sinit(CallbackInfo callbackInfo) {
        try {
            setupLibraryPath();
        } catch (IOException e) {
            MCEF.getLogger().error("Failed to setup MCEF library path.", e);
            MCEFDownloadListener.INSTANCE.setFailed(true);
            return;
        }

        Thread initThread = new Thread(() -> {
            File mcefLibrariesDir = new File(System.getProperty("mcef.libraries.path"));
            String platformName = MCEFPlatform.getPlatform().getNormalizedName();
            File platformDir = new File(mcefLibrariesDir, platformName);

            // Step 1: Check if natives are already extracted
            boolean nativesReady = platformDir.exists() && platformDir.list() != null && platformDir.list().length > 0;

            // Step 2: If not, try to extract from bundled JAR resources
            if (!nativesReady && NativeExtractor.hasBundledNatives()) {
                MCEF.getLogger().info("Found bundled native binaries. Extracting...");
                MCEFDownloadListener.INSTANCE.setTask("Extracting bundled natives");
                MCEFDownloadListener.INSTANCE.setProgress(0.1f);

                if (NativeExtractor.extractNatives(mcefLibrariesDir)) {
                    nativesReady = true;
                    MCEFDownloadListener.INSTANCE.setProgress(1.0f);
                    MCEF.getLogger().info("Successfully extracted bundled natives.");
                } else {
                    MCEF.getLogger().warn("Failed to extract bundled natives, falling back to download.");
                }
            }

            // Step 3: If still not ready and download not skipped, download from remote
            if (!nativesReady && !MCEF.getSettings().isSkipDownload()) {
                MCEF.getLogger().info("Bundled natives not available. Downloading from remote...");

                String javaCefCommit;
                try {
                    javaCefCommit = MCEF.getJavaCefCommit();
                } catch (IOException e) {
                    MCEF.getLogger().error("Failed to get java-cef commit.", e);
                    MCEFDownloadListener.INSTANCE.setFailed(true);
                    return;
                }

                MCEF.getLogger().info("java-cef commit: " + javaCefCommit);

                MCEFSettings settings = MCEF.getSettings();
                MCEFDownloader downloader = new MCEFDownloader(settings.getDownloadMirror(), javaCefCommit, MCEFPlatform.getPlatform());

                boolean downloadJcefBuild;

                try {
                    downloadJcefBuild = !downloader.downloadJavaCefChecksum();
                } catch (IOException e) {
                    MCEF.getLogger().error("Failed to download JCEF checksum.", e);
                    MCEFDownloadListener.INSTANCE.setFailed(true);
                    return;
                }

                downloadJcefBuild |= !platformDir.exists();

                if (downloadJcefBuild) {
                    try {
                        downloader.downloadJavaCefBuild();
                        if (!downloader.validateJavaCefBuild()) {
                            MCEF.getLogger().error("Downloaded JCEF archive checksum does not match.");
                            MCEFDownloadListener.INSTANCE.setFailed(true);
                            return;
                        }
                    } catch (IOException e) {
                        MCEF.getLogger().error("Failed to download JCEF.", e);
                        MCEFDownloadListener.INSTANCE.setFailed(true);
                        return;
                    }

                    downloader.extractJavaCefBuild(true);
                }
            } else if (!nativesReady && MCEF.getSettings().isSkipDownload()) {
                MCEF.getLogger().error("No native binaries found and download is disabled. MCEF will not work.");
                MCEFDownloadListener.INSTANCE.setFailed(true);
                return;
            }

            MCEFDownloadListener.INSTANCE.setDone(true);
        });
        initThread.start();
    }
}
