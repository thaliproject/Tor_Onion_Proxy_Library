/*
Copyright (C) 2011-2014 Sublime Software Ltd

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
 */

/*
Copyright (c) Microsoft Open Technologies, Inc.
All Rights Reserved
Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the
License. You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0

THIS CODE IS PROVIDED ON AN *AS IS* BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, EITHER EXPRESS OR IMPLIED,
INCLUDING WITHOUT LIMITATION ANY IMPLIED WARRANTIES OR CONDITIONS OF TITLE, FITNESS FOR A PARTICULAR PURPOSE,
MERCHANTABLITY OR NON-INFRINGEMENT.

See the Apache 2 License for the specific language governing permissions and limitations under the License.
*/

package com.msopentech.thali.java.toronionproxy;

import com.msopentech.thali.toronionproxy.OnionProxyContext;
import com.msopentech.thali.toronionproxy.OnionProxyManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sun.misc.IOUtils;

import java.io.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class JavaOnionProxyManager extends OnionProxyManager {
    private static final Logger LOG = LoggerFactory.getLogger(JavaOnionProxyManager.class);

    public JavaOnionProxyManager(OnionProxyContext onionProxyContext) {
        super(onionProxyContext);
    }

    @Override
    protected File installBinary() {
        try {
            File directoryToHoldTorFiles = new File(torDirectory, "torFiles");

            // We always re-install the binary just in case it has been updated so we first delete the
            // binary directory
            if (directoryToHoldTorFiles.exists()) {
                OnionProxyManager.recursiveFileDelete(directoryToHoldTorFiles);
            }

            if (directoryToHoldTorFiles.exists() == false && directoryToHoldTorFiles.mkdirs() == false) {
                throw new RuntimeException("Could not create local directory to hold extracted tor files");
            }

            extractContentFromZip(directoryToHoldTorFiles, onionProxyContext.getTorExecutableZip());

            String osName = System.getProperty("os.name");
            String executableName;
            if (osName.contains("Windows")) {
                executableName = "tor.exe";
            } else {
                throw new RuntimeException("We don't support Tor on this OS yet");
            }
            File executableFile = new File(directoryToHoldTorFiles, executableName);

            if (executableFile.exists() == false) {
                throw new RuntimeException("Expected executable for this platform does seem to exist! Expected file was "
                        + executableFile);
            }

            // Make the Tor binary executable
            if(!setExecutable(executableFile)) {
                throw new RuntimeException("Could not make Tor executable");
            }
            return executableFile;
        } catch(IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * This has to exist somewhere! Why isn't it a part of the standard Java library?
     * @param destinationDirectory Directory files are to be extracted to
     * @param zipFileInputStream Stream to unzip
     */
    private void extractContentFromZip(File destinationDirectory, InputStream zipFileInputStream) throws IOException {
        ZipInputStream zipInputStream = null;
        try {
            zipInputStream = new ZipInputStream(zipFileInputStream);
            ZipEntry zipEntry;
            while ((zipEntry = zipInputStream.getNextEntry()) != null) {
                File file = new File(destinationDirectory, zipEntry.getName());
                if (zipEntry.isDirectory()) {
                    if (file.mkdirs() == false) {
                        throw new RuntimeException("Could not create directory " + file);
                    }
                } else {
                    if (file.exists()) {
                        throw new RuntimeException("You aren't supposed to have the same file twice in a zip - " + file);
                    }

                    if (file.createNewFile() == false) {
                        throw new RuntimeException("Could not create file " + file);
                    }

                    OutputStream fileOutputStream = null;
                    try {
                        fileOutputStream = new FileOutputStream(file);

                        copyDontCloseInput(zipInputStream, fileOutputStream);
                    } finally {
                        if (fileOutputStream != null) {
                            fileOutputStream.close();
                        }
                    }
                }
            }
        } finally {
            if (zipFileInputStream != null) {
                zipFileInputStream.close();
            }
        }
    }

    @Override
    protected boolean setExecutable(File f) {
        return f.setExecutable(true);
    }
}
