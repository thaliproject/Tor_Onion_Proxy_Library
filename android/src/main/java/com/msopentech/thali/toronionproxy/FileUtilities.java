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

/*
This code took the Socks4a logic from SocksProxyClientConnOperator in NetCipher which we then modified
to meet our needs. That original code was licensed as:

This file contains the license for Orlib, a free software project to
provide anonymity on the Internet from a Google Android smartphone.

For more information about Orlib, see https://guardianproject.info/

If you got this file as a part of a larger bundle, there may be other
license terms that you should be aware of.
===============================================================================
Orlib is distributed under this license (aka the 3-clause BSD license)

Copyright (c) 2009-2010, Nathan Freitas, The Guardian Project

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are
met:

    * Redistributions of source code must retain the above copyright
notice, this list of conditions and the following disclaimer.

    * Redistributions in binary form must reproduce the above
copyright notice, this list of conditions and the following disclaimer
in the documentation and/or other materials provided with the
distribution.

    * Neither the names of the copyright owners nor the names of its
contributors may be used to endorse or promote products derived from
this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
"AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
(INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.

*****
Orlib contains a binary distribution of the JSocks library:
http://code.google.com/p/jsocks-mirror/
which is licensed under the GNU Lesser General Public License:
http://www.gnu.org/licenses/lgpl.html

*****

 */

package com.msopentech.thali.toronionproxy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class FileUtilities {
    private static final Logger LOG = LoggerFactory.getLogger(FileUtilities.class);

    /**
     * Closes both input and output streams when done.
     * @param in
     * @param out
     * @throws java.io.IOException
     */
    public static void copy(InputStream in, OutputStream out) throws IOException {
        try {
            copyDontCloseInput(in, out);
        } finally {
            in.close();
        }
    }

    /**
     * Won't close the input stream when it's done, needed to handle ZipInputStreams
     * @param in Won't be closed
     * @param out Will be closed
     * @throws IOException
     */
    public static void copyDontCloseInput(InputStream in, OutputStream out) throws IOException {
        try {
            byte[] buf = new byte[4096];
            while(true) {
                int read = in.read(buf);
                if(read == -1) break;
                out.write(buf, 0, read);
            }
        } finally {
            out.close();
        }
    }

    public static void tryToClose(Closeable closeable) {
        try {
            if(closeable != null) closeable.close();
        } catch(IOException e) {
            LOG.warn(e.toString(), e);
        }
    }

    public static void listFiles(File f) {
        if(f.isDirectory()) for(File child : f.listFiles()) listFiles(child);
        else LOG.info(f.getAbsolutePath());
    }

    public static byte[] read(File f) throws IOException {
        byte[] b = new byte[(int) f.length()];
        FileInputStream in = new FileInputStream(f);
        try {
            int offset = 0;
            while(offset < b.length) {
                int read = in.read(b, offset, b.length - offset);
                if(read == -1) throw new EOFException();
                offset += read;
            }
            return b;
        } finally {
            in.close();
        }
    }

    /**
     * Reads the input stream, deletes fileToWriteTo if it exists
     * and over writes it with the stream.
     * @param readFrom
     * @param fileToWriteTo
     * @throws IOException
     */
    public static void cleanInstallOneFile(InputStream readFrom, File fileToWriteTo) throws IOException {
        if (fileToWriteTo.exists() && fileToWriteTo.delete() == false) {
            throw new RuntimeException("Could not remove existing file " + fileToWriteTo.getName());
        }
        OutputStream out = new FileOutputStream(fileToWriteTo);
        FileUtilities.copy(readFrom, out);
    }

    public static void recursiveFileDelete(File fileOrDirectory) {
        if (fileOrDirectory.isDirectory()) {
            for (File child : fileOrDirectory.listFiles()) {
                recursiveFileDelete(child);
            }
        }

        fileOrDirectory.delete();
    }

    /**
     * This has to exist somewhere! Why isn't it a part of the standard Java library?
     * @param destinationDirectory Directory files are to be extracted to
     * @param zipFileInputStream Stream to unzip
     */
    public static void extractContentFromZip(File destinationDirectory, InputStream zipFileInputStream) throws IOException {
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
                    if (file.exists() && file.delete() == false) {
                        throw new RuntimeException(
                                "Could not delete file in preparation for overwriting it. File - " +
                                        file.getAbsolutePath());
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
}
