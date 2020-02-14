package com.msopentech.thali.toronionproxy;

import java.util.ArrayList;
import java.util.List;

/**
 * Provides some reasonable default settings. Override this class or create a new implementation to
 * make changes.
 */
public class DefaultSettings implements TorSettings {
    @Override
    public boolean disableNetwork() {
        return true;
    }

    @Override
    public String dnsPort() {
        return "5400";
    }

    @Override
    public String getCustomTorrc() {
        return null;
    }

    @Override
    public String getEntryNodes() {
        return null;
    }

    @Override
    public String getExcludeNodes() {
        return null;
    }

    @Override
    public String getExitNodes() {
        return null;
    }

    @Override
    public int getHttpTunnelPort() {
        return 8118;
    }

    @Override
    public List<String> getListOfSupportedBridges() {
        return new ArrayList<>();
    }

    @Override
    public String getProxyHost() {
        return null;
    }

    @Override
    public String getProxyPassword() {
        return null;
    }

    @Override
    public String getProxyPort() {
        return null;
    }

    @Override
    public String getProxySocks5Host() {
        return null;
    }

    @Override
    public String getProxySocks5ServerPort() {
        return null;
    }

    @Override
    public String getProxyType() {
        return null;
    }

    @Override
    public String getProxyUser() {
        return null;
    }

    @Override
    public String getReachableAddressPorts() {
        return "*:80,*:443";
    }

    @Override
    public String getRelayNickname() {
        return null;
    }

    @Override
    public int getRelayPort() {
        return 9001;
    }

    @Override
    public String getSocksPort() {
        return "9050";
    }

    @Override
    public String getVirtualAddressNetwork() {
        return null;
    }

    @Override
    public boolean hasBridges() {
        return false;
    }

    @Override
    public boolean hasConnectionPadding() {
        return false;
    }

    @Override
    public boolean hasCookieAuthentication() {
        return true;
    }

    @Override
    public boolean hasDebugLogs() {
        return false;
    }

    @Override
    public boolean hasDormantCanceledByStartup() {
        return false;
    }

    @Override
    public boolean hasIsolationAddressFlagForTunnel() {
        return false;
    }

    @Override
    public boolean hasOpenProxyOnAllInterfaces() {
        return false;
    }

    @Override
    public boolean hasReachableAddress() {
        return false;
    }

    @Override
    public boolean hasReducedConnectionPadding() {
        return true;
    }

    @Override
    public boolean hasSafeSocks() {
        return false;
    }

    @Override
    public boolean hasStrictNodes() {
        return false;
    }

    @Override
    public boolean hasTestSocks() {
        return false;
    }

    @Override
    public boolean isAutomapHostsOnResolve() {
        return true;
    }

    @Override
    public boolean isRelay() {
        return false;
    }

    @Override
    public boolean runAsDaemon() {
        return true;
    }

    @Override
    public String transPort() {
        return "9040";
    }

    @Override
    public boolean useSocks5() {
        return false;
    }
}
