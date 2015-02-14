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

package com.msopentech.thali.local.toronionproxy;

import com.msopentech.thali.java.toronionproxy.JavaOnionProxyContext;
import com.msopentech.thali.java.toronionproxy.JavaOnionProxyManager;
import com.msopentech.thali.toronionproxy.OnionProxyManager;
import junit.framework.TestCase;

import java.io.IOException;
import java.nio.file.Files;

public class TorOnionProxyTestCase extends TestCase {
    public OnionProxyManager getOnionProxyManager(String workingSubDirectoryName) {
        try {
            return new JavaOnionProxyManager(
                    new JavaOnionProxyContext(
                            Files.createTempDirectory(workingSubDirectoryName).toFile()));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void testTorOnionProxyTestCaseSetupProperly() {
        // Avoid No tests found Assertion Failed Error.
    }
}
