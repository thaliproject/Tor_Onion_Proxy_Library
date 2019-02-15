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
package com.msopentech.thali.toronionproxy;

/**
 * Default EventBroadcaster. broadcastBandwidth, broadcastLogMessage, broadcastStatus are all
 * no operations. If you need to implement these broadcast methods, then create a new class
 * that extends BaseEventBroadcaster.
 */
public final class DefaultEventBroadcaster extends BaseEventBroadcaster {

    public DefaultEventBroadcaster() {
        super(null);
    }

    public DefaultEventBroadcaster(TorSettings settings) {
        super(settings);
    }

    @Override
    public void broadcastBandwidth(long upload, long download, long written, long read) {

    }

    @Override
    public void broadcastLogMessage(String logMessage) {

    }

    @Override
    public void broadcastStatus() {

    }
}
