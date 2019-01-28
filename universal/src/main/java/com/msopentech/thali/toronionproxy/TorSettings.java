package com.msopentech.thali.toronionproxy;

import java.io.InputStream;

public interface TorSettings {
    boolean disableNetwork();

    String dnsPort();

    InputStream getBridges();

    String getCustomTorrc();

    String getEntryNodes();

    String getExcludeNodes();

    String getExitNodes();

    int getHttpTunnelPort();

    String getListOfSupportedBridges();

    String getProxyHost();

    String getProxyPassword();

    String getProxyPort();

    String getProxySocks5Host();

    String getProxySocks5ServerPort();

    String getProxyType();

    String getProxyUser();

    String getReachableAddressPorts();

    String getRelayNickname();

    int getRelayPort();

    String getSocksPort();

    String getVirtualAddressNetwork();

    boolean hasBridges();

    boolean hasConnectionPadding();

    boolean hasDebugLogs();

    boolean hasIsolationAddressFlagForTunnel();

    boolean hasOpenProxyOnAllInterfaces();

    boolean hasReachableAddress();

    boolean hasReducedConnectionPadding();

    boolean hasSafeSocks();

    boolean hasStrictNodes();

    boolean hasTestSocks();

    boolean isAutomapHostsOnResolve();

    boolean isRelay();

    String transPort();

    boolean useSocks5();
}
