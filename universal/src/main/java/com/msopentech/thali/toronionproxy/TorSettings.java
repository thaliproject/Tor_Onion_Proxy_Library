package com.msopentech.thali.toronionproxy;

import java.util.List;

public interface TorSettings {
    boolean disableNetwork();

    String dnsPort();

    String getCustomTorrc();

    String getEntryNodes();

    String getExcludeNodes();

    String getExitNodes();

    int getHttpTunnelPort();

    /**
     * Returns a list of supported bridges. The string value will include the name: meek_lite, obfs4
     */
    List<String> getListOfSupportedBridges();

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

    boolean hasCookieAuthentication();

    boolean hasDebugLogs();

    boolean hasDormantCanceledByStartup();

    boolean hasIsolationAddressFlagForTunnel();

    boolean hasOpenProxyOnAllInterfaces();

    boolean hasReachableAddress();

    boolean hasReducedConnectionPadding();

    boolean hasSafeSocks();

    boolean hasStrictNodes();

    boolean hasTestSocks();

    boolean isAutomapHostsOnResolve();

    boolean isRelay();

    boolean runAsDaemon();

    String transPort();

    boolean useSocks5();
}
