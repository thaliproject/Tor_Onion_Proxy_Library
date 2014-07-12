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
import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * Watches to see if a particular file is changed
 */
public class JavaWatchObserver implements WriteObserver {
    private WatchService watchService;
    private WatchKey key;
    private File fileToWatch;
    private static final Logger LOG = LoggerFactory.getLogger(WriteObserver.class);

    public JavaWatchObserver(File fileToWatch) throws IOException {
        this.fileToWatch = fileToWatch;

        watchService = FileSystems.getDefault().newWatchService();
        // Note that poll depends on us only registering events that are of type path
        key = fileToWatch.getParentFile().toPath().register(watchService, StandardWatchEventKinds.ENTRY_CREATE, StandardWatchEventKinds.ENTRY_DELETE,
                StandardWatchEventKinds.ENTRY_MODIFY);
    }

    @Override
    public boolean poll(long timeout, TimeUnit unit) {
        boolean result = false;
        try {
            long remainingTimeoutInNanos = unit.toNanos(timeout);
            while (remainingTimeoutInNanos > 0) {
                long startTimeInNanos = System.nanoTime();
                WatchKey receivedKey = watchService.poll(remainingTimeoutInNanos, TimeUnit.NANOSECONDS);
                long timeWaitedInNanos = System.nanoTime() - startTimeInNanos;

                if (receivedKey != null) {
                    if (receivedKey != key) {
                        throw new RuntimeException("This really shouldn't have happened. EEK!" + receivedKey.toString());
                    }

                    for (WatchEvent<?> event : receivedKey.pollEvents()) {
                        WatchEvent.Kind<?> kind = event.kind();

                        if (kind == StandardWatchEventKinds.OVERFLOW ) {
                            LOG.error("We got an overflow, there shouldn't have been enough activity to make that happen.");
                        }


                        Path changedEntry = (Path) event.context();
                        if (fileToWatch.toPath().endsWith(changedEntry)) {
                            result = true;
                            return result;
                        }
                    }

                    // In case we haven't yet gotten the event we are looking for we have to reset in order to
                    // receive any further notifications.
                    if (key.reset() == false) {
                        LOG.error("The key became invalid which should not have happened.");
                    }
                }

                if (timeWaitedInNanos >= remainingTimeoutInNanos) {
                    result = false;
                    return result;
                }

                remainingTimeoutInNanos -= timeWaitedInNanos;
            }

            result = false;
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
