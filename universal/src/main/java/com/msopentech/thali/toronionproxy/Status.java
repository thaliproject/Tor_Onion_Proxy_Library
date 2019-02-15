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
 * Current Status of Tor
 */
public final class Status {

    public static String STATUS_OFF = "OFF";
    public static String STATUS_ON = "ON";
    public static String STATUS_STARTING = "STARTING";
    public static String STATUS_STOPPING = "STOPPING";
    private final EventBroadcaster broadcaster;

    private String status;

    public Status(EventBroadcaster broadcaster) {
        this.broadcaster = broadcaster;
        this.status = STATUS_OFF;
    }

    public String getStatus() {
        return status;
    }

    public boolean isOff() {
        return STATUS_OFF.equals(status);
    }

    public boolean isOn() {
        return STATUS_ON.equals(status);
    }

    public boolean isStarting() {
        return STATUS_STARTING.equals(status);
    }

    public boolean isStopping() {
        return STATUS_STOPPING.equals(status);
    }

    public void off() {
        status = STATUS_OFF;
        broadcaster.broadcastStatus();
    }

    public void on() {
        status = STATUS_ON;
        broadcaster.broadcastStatus();
    }

    public void starting() {
        status = STATUS_STARTING;
        broadcaster.broadcastStatus();
    }

    public void stopping() {
        status = STATUS_STOPPING;
        broadcaster.broadcastStatus();
    }
}
