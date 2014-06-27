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
import com.msopentech.thali.toronionproxy.WriteObserver;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;

public class JavaOnionProxyContext implements OnionProxyContext {
    @Override
    public InputStream getTorrc() throws IOException {
        return null;
    }

    @Override
    public InputStream getTorExecutableZip() throws IOException {
        return null;
    }

    @Override
    public File getWorkingDirectory() {
        return null;
    }

    @Override
    public WriteObserver generateWriteObserver(File file) {
        return null;
    }
}
