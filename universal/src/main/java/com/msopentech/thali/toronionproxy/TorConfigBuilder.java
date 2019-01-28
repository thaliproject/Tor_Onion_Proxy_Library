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

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

public final class TorConfigBuilder {

    private final TorSettings settings;
    private final OnionProxyContext context;

    private StringBuffer buffer = new StringBuffer();

    public TorConfigBuilder(OnionProxyContext context) {
        this.settings = context.getSettings();
        this.context = context;
    }

    public TorConfigBuilder updateTorConfig() throws
            IOException {
        TorConfig config = context.getConfig();
        controlPortWriteToFile(config.getControlPortFile().getCanonicalPath())
                .socksPortFromSettings().safeSocksFromSettings().testSocksFromSettings()
                .proxyOnAllInterfacesFromSettings().httpTunnelPortFromSettings()
                .connectionPaddingFromSettings()
                .reducedConnectionPaddingFromSettings().transPortFromSettings()
                .dnsPortFromSettings()
                .virtualAddressNetworkFromSettings().automapHostsOnResolveFromSettings()
                .disableNetworkFromSettings()
                .debugLogsFromSettings().useBridgesFromSettings().proxySocks5FromSettings()
                .proxyWithAuthenticationFromSettings().configurePluggableTransportsFromSettings
                (null).setGeoIpFiles(config)
                .nodesFromSettings()
                .strictNodesFromSettings().reachableAddressesFromSettings()
                .nonExitRelayFromSettings(context).torrcCustomFromSettings();


        return this;
    }

    private static boolean isNullOrEmpty(String value) {
        return value == null || value.isEmpty();
    }

    private static boolean isLocalPortOpen(int port) {
        try {
            Socket socket = new Socket();
            socket.connect(new InetSocketAddress("127.0.0.1", port), 500);
            socket.close();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Add bridges from bridges.txt file.
     */
    public TorConfigBuilder addBridges(InputStream input, String bridgeType, int maxBridges) {
        if (input == null || isNullOrEmpty(bridgeType) || maxBridges < 1) {
            return this;
        }
        List<Bridge> bridges = new ArrayList<>();
        try {
            BufferedReader br = new BufferedReader(new InputStreamReader(input, "UTF-8"));
            for (String line = br.readLine(); line != null; line = br.readLine()) {
                String[] tokens = line.split(" ", 1);
                if (tokens.length != 2) {
                    continue;//bad entry
                }
                bridges.add(new Bridge(tokens[0], tokens[1]));
            }
            br.close();
        } catch (Exception e) {
            e.printStackTrace();
        }

        Collections.shuffle(bridges, new Random(System.nanoTime()));
        int bridgeCount = 0;
        for (Bridge b : bridges) {
            if (b.type.equals(bridgeType)) {
                bridge(b.type, b.config);
                if (++bridgeCount > maxBridges)
                    break;
            }
        }
        return this;
    }

    public TorConfigBuilder addBridgesFromSettings(String type, int maxBridges) {
        return addBridges(settings.getBridges(), type, maxBridges);
    }

    public String asString() {
        return buffer.toString();
    }

    public TorConfigBuilder automapHostsOnResolve() {
        buffer.append("AutomapHostsOnResolve 1").append('\n');
        return this;
    }

    public TorConfigBuilder automapHostsOnResolveFromSettings() {
        return settings.isAutomapHostsOnResolve() ? automapHostsOnResolve() : this;
    }

    public TorConfigBuilder bridge(String type, String config) {
        if (!isNullOrEmpty(type) && !isNullOrEmpty(config)) {
            buffer.append("Bridge ").append(type).append(' ').append(config).append('\n');
        }
        return this;
    }

    public TorConfigBuilder configurePluggableTransportsFromSettings(File pluggableTransportClient) throws IOException {
        if (pluggableTransportClient == null || !settings.hasBridges()) {
            return this;
        }
        if (pluggableTransportClient.exists() && pluggableTransportClient.canExecute()) {
            useBridges();

            String bridges = settings.getListOfSupportedBridges();
            if (bridges.contains("obfs3") || bridges.contains("obfs4")) {
                transportPluginObfs(pluggableTransportClient.getCanonicalPath());
            }
            if (bridges.contains("meek")) {
                transportPluginMeek(pluggableTransportClient.getCanonicalPath());
            }

            if (bridges.length() > 5) {
                for (String bridge : bridges.split("\\r?\\n")) {
                    line("Bridge " + bridge);
                }
            } else {
                String type = bridges.contains("meek") ? "meek_lite" : "obfs4";
                addBridgesFromSettings(type, 2);
            }
        } else {
            throw new IOException("Bridge binary does not exist: " + pluggableTransportClient
                    .getCanonicalPath());
        }

        return this;
    }

    public TorConfigBuilder connectionPadding() {
        buffer.append("ConnectionPadding 1").append('\n');
        return this;
    }

    public TorConfigBuilder connectionPaddingFromSettings() {
        return settings.hasConnectionPadding() ? connectionPadding() : this;
    }

    public TorConfigBuilder controlPortWriteToFile(String controlPortFile) {
        buffer.append("ControlPortWriteToFile ").append(controlPortFile).append('\n');
        return this;
    }

    public TorConfigBuilder debugLogs() {
        buffer.append("Log debug syslog").append('\n');
        buffer.append("Log info syslog").append('\n');
        buffer.append("SafeLogging 0").append('\n');
        return this;
    }

    public TorConfigBuilder debugLogsFromSettings() {
        return settings.hasDebugLogs() ? debugLogs() : this;
    }

    public TorConfigBuilder disableNetwork() {
        buffer.append("DisableNetwork 0").append('\n');
        return this;
    }

    public TorConfigBuilder disableNetworkFromSettings() {
        return settings.disableNetwork() ? disableNetwork() : this;
    }

    public TorConfigBuilder dnsPort(String dnsPort) {
        if (!isNullOrEmpty(dnsPort)) buffer.append("DNSPort ").append(dnsPort).append('\n');
        return this;
    }

    public TorConfigBuilder dnsPortFromSettings() {
        return dnsPort(settings.dnsPort());
    }

    public TorConfigBuilder dontUseBridges() {
        buffer.append("UseBridges 0").append('\n');
        return this;
    }

    public TorConfigBuilder entryNodes(String entryNodes) {
        if (!isNullOrEmpty(entryNodes))
            buffer.append("EntryNodes ").append(entryNodes).append('\n');
        return this;
    }

    public TorConfigBuilder excludeNodes(String excludeNodes) {
        if (!isNullOrEmpty(excludeNodes))
            buffer.append("ExcludeNodes ").append(excludeNodes).append('\n');
        return this;
    }

    public TorConfigBuilder exitNodes(String exitNodes) {
        if (!isNullOrEmpty(exitNodes))
            buffer.append("ExitNodes ").append(exitNodes).append('\n');
        return this;
    }

    public TorConfigBuilder geoIpFile(String path) {
        if (!isNullOrEmpty(path)) buffer.append("GeoIPFile ").append(path).append('\n');
        return this;
    }

    public TorConfigBuilder geoIpV6File(String path) {
        if (!isNullOrEmpty(path)) buffer.append("GeoIPv6File ").append(path).append('\n');
        return this;
    }

    public TorConfigBuilder httpTunnelPort(int port, String isolationFlags) {
        buffer.append("HTTPTunnelPort ").append(port);
        if (!isNullOrEmpty(isolationFlags)) {
            buffer.append(" ").append(isolationFlags);
        }
        buffer.append('\n');
        return this;
    }

    public TorConfigBuilder httpTunnelPortFromSettings() {
        return httpTunnelPort(settings.getHttpTunnelPort(),
                settings.hasIsolationAddressFlagForTunnel() ? "IsolateDestAddr" : null);
    }

    public TorConfigBuilder line(String value) {
        if (!isNullOrEmpty(value)) buffer.append(value).append("\n");
        return this;
    }

    public TorConfigBuilder makeNonExitRelay(String dnsFile, int orPort, String nickname) {
        buffer.append("ServerDNSResolvConfFile ").append(dnsFile).append('\n');
        buffer.append("ORPort ").append(orPort).append('\n');
        buffer.append("Nickname ").append(nickname).append('\n');
        buffer.append("ExitPolicy reject *:*").append('\n');
        return this;
    }

    /**
     * Sets the entry/exit/exclude nodes
     */
    public TorConfigBuilder nodesFromSettings() {
        entryNodes(settings.getEntryNodes()).exitNodes(settings.getExitNodes())
                .excludeNodes(settings.getExcludeNodes());
        return this;
    }

    public TorConfigBuilder nonExitRelayFromSettings(OnionProxyContext context) {
        if (!settings.hasReachableAddress() && !settings.hasBridges() && settings.isRelay()) {
            try {
                File resolv = context.createGoogleNameserverFile();
                makeNonExitRelay(resolv.getCanonicalPath(), settings.getRelayPort(), settings
                        .getRelayNickname());
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return this;
    }

    public TorConfigBuilder proxyOnAllInterfaces() {
        buffer.append("SocksListenAddress 0.0.0.0").append('\n');
        return this;
    }

    public TorConfigBuilder proxyOnAllInterfacesFromSettings() {
        return settings.hasOpenProxyOnAllInterfaces() ? proxyOnAllInterfaces() : this;
    }

    /**
     * Set socks5 proxy with no authentication. This can be set if yo uare using a VPN.
     */
    public TorConfigBuilder proxySocks5(String host, String port) {
        buffer.append("socks5Proxy ").append(host).append(':').append(port).append('\n');
        return this;
    }

    public TorConfigBuilder proxySocks5FromSettings() {
        return (settings.useSocks5() && !settings.hasBridges()) ? proxySocks5(settings
                        .getProxySocks5Host(),
                settings.getProxySocks5ServerPort()) : this;
    }

    /**
     * Sets proxyWithAuthentication information. If proxyType, proxyHost or proxyPort is empty,
     * then this method does nothing.
     */
    public TorConfigBuilder proxyWithAuthentication(String proxyType, String proxyHost, String
            proxyPort, String proxyUser, String proxyPass) {
        if (!isNullOrEmpty(proxyType) && !isNullOrEmpty(proxyHost) && !isNullOrEmpty(proxyPort)) {
            buffer.append(proxyType).append("Proxy ").append(proxyHost).append(':').append
                    (proxyPort).append('\n');

            if (proxyUser != null && proxyPass != null) {
                if (proxyType.equalsIgnoreCase("socks5")) {
                    buffer.append("Socks5ProxyUsername ").append(proxyUser).append('\n');
                    buffer.append("Socks5ProxyPassword ").append(proxyPass).append('\n');
                } else {
                    buffer.append(proxyType).append("ProxyAuthenticator ").append(proxyUser)
                            .append(':').append(proxyPort).append('\n');
                }
            } else if (proxyPass != null) {
                buffer.append(proxyType).append("ProxyAuthenticator ").append(proxyUser)
                        .append(':').append(proxyPort).append('\n');
            }
        }
        return this;
    }

    public TorConfigBuilder proxyWithAuthenticationFromSettings() {
        return (!settings.useSocks5() && !settings.hasBridges()) ? proxyWithAuthentication
                (settings.getProxyType(), settings.getProxyHost(),
                        settings.getProxyPort(), settings.getProxyUser(), settings
                                .getProxyPassword()) :
                this;
    }

    public TorConfigBuilder reachableAddressPorts(String reachableAddressesPorts) {
        if (!isNullOrEmpty(reachableAddressesPorts))
            buffer.append("ReachableAddresses ").append(reachableAddressesPorts).append('\n');
        return this;
    }

    public TorConfigBuilder reachableAddressesFromSettings() {
        return settings.hasReachableAddress() ? reachableAddressPorts(settings
                .getReachableAddressPorts()) : this;

    }

    public TorConfigBuilder reducedConnectionPadding() {
        buffer.append("ReducedConnectionPadding 1").append('\n');
        return this;
    }

    public TorConfigBuilder reducedConnectionPaddingFromSettings() {
        return settings.hasReducedConnectionPadding() ? reducedConnectionPadding() : this;
    }

    public void reset() {
        buffer = new StringBuffer();
    }

    public TorConfigBuilder safeSocksDisable() {
        buffer.append("SafeSocks 0").append('\n');
        return this;
    }

    public TorConfigBuilder safeSocksEnable() {
        buffer.append("SafeSocks 1").append('\n');
        return this;
    }

    public TorConfigBuilder safeSocksFromSettings() {
        return !settings.hasSafeSocks() ? safeSocksDisable() : this;
    }

    public TorConfigBuilder setGeoIpFiles(TorConfig torConfig) throws IOException {
        if (torConfig.getGeoIpFile().exists()) {
            geoIpFile(torConfig.getGeoIpFile().getCanonicalPath())
                    .geoIpV6File(torConfig.getGeoIpv6File().getCanonicalPath());
        }
        return this;
    }

    public TorConfigBuilder socksPort(String socksPort, String isolationFlag) {
        if (isNullOrEmpty(socksPort)) {
            return this;
        }
        buffer.append("SOCKSPort ").append(socksPort);
        if (!isNullOrEmpty(isolationFlag)) {
            buffer.append(" ").append(isolationFlag);
        }
        buffer.append('\n');
        return this;
    }

    public TorConfigBuilder socksPortFromSettings() {
        String socksPort = settings.getSocksPort();
        if (socksPort.indexOf(':') != -1) {
            socksPort = socksPort.split(":")[1];
        }

        if (!socksPort.equalsIgnoreCase("auto") && isLocalPortOpen(Integer.parseInt(socksPort))) {
            socksPort = "auto";
        }
        return socksPort(socksPort, settings.hasIsolationAddressFlagForTunnel() ?
                "IsolateDestAddr" : null);
    }

    public TorConfigBuilder strictNodesDisable() {
        buffer.append("StrictNodes 0\n");
        return this;
    }

    public TorConfigBuilder strictNodesEnable() {
        buffer.append("StrictNodes 1\n");
        return this;
    }

    public TorConfigBuilder strictNodesFromSettings() {
        return settings.hasStrictNodes() ? strictNodesEnable() : strictNodesDisable();
    }

    public TorConfigBuilder testSocksDisable() {
        buffer.append("TestSocks 0").append('\n');
        return this;
    }

    public TorConfigBuilder testSocksEnable() {
        buffer.append("TestSocks 0").append('\n');
        return this;
    }

    public TorConfigBuilder testSocksFromSettings() {
        return !settings.hasTestSocks() ? testSocksDisable() : this;
    }

    public TorConfigBuilder torrcCustomFromSettings() throws UnsupportedEncodingException {
        return line(new String(settings.getCustomTorrc().getBytes("US-ASCII")));
    }

    public TorConfigBuilder transPort(String transPort) {
        if (!isNullOrEmpty(transPort))
            buffer.append("TransPort ").append(transPort).append('\n');
        return this;
    }

    public TorConfigBuilder transPortFromSettings() {
        return transPort(settings.transPort());
    }

    public TorConfigBuilder transportPluginMeek(String clientPath) {
        buffer.append("ClientTransportPlugin meek_lite exec ").append(clientPath).append('\n');
        return this;
    }

    public TorConfigBuilder transportPluginObfs(String clientPath) {
        buffer.append("ClientTransportPlugin obfs3 exec ").append(clientPath).append('\n');
        buffer.append("ClientTransportPlugin obfs4 exec ").append(clientPath).append('\n');
        return this;
    }

    public TorConfigBuilder useBridges() {
        buffer.append("UseBridges 1").append('\n');
        return this;
    }

    public TorConfigBuilder useBridgesFromSettings() {
        return !settings.hasBridges() ? dontUseBridges() : this;
    }

    public TorConfigBuilder virtualAddressNetwork(String address) {
        if (!isNullOrEmpty(address))
            buffer.append("VirtualAddrNetwork ").append(address).append('\n');
        return this;
    }

    public TorConfigBuilder virtualAddressNetworkFromSettings() {
        return virtualAddressNetwork(settings.getVirtualAddressNetwork());
    }

    private static class Bridge {
        final String type;
        final String config;

        public Bridge(String type, String config) {
            this.type = type;
            this.config = config;
        }
    }
}
