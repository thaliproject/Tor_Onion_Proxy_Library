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

import com.msopentech.thali.toronionproxy.WriteObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.util.concurrent.TimeUnit;

public class JavaWatchObserver implements WriteObserver {
    WatchService watchService;
    WatchKey key;
    private static final Logger LOG = LoggerFactory.getLogger(WriteObserver.class);

    public JavaWatchObserver(File file) throws IOException {
        Path path = file.toPath();
        watchService = FileSystems.getDefault().newWatchService();
        key = path.register(watchService, StandardWatchEventKinds.ENTRY_CREATE, StandardWatchEventKinds.ENTRY_DELETE,
                StandardWatchEventKinds.ENTRY_MODIFY);
    }

    @Override
    public boolean poll(long timeout, TimeUnit unit) {
        boolean result = false;
        try {
            WatchKey receivedKey = watchService.poll(timeout, unit);

            if (receivedKey == null) {
                return false;
            }

            if (receivedKey != key) {
                throw new RuntimeException("This really shouldn't have happened. EEK!" + receivedKey.toString());
            }

            result = true;
            return result;
        } catch (InterruptedException e) {
            throw new RuntimeException("Internal error has caused JavaWatchObserver to not be reliable.", e);
        } finally {
            if (result) {
                try {
                    watchService.close();
                } catch (IOException e) {
                    LOG.debug("Attempt to close watchService failed.", e);
                }
            }
        }
    }
}
