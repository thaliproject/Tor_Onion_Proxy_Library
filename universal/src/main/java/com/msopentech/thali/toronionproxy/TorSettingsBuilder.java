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

import java.io.*;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

public final class TorSettingsBuilder {

    private final TorSettings settings;
    private final OnionProxyContext context;

    private StringBuffer buffer = new StringBuffer();

    public TorSettingsBuilder(OnionProxyContext context) {
        this.settings = context.getSettings();
        this.context = context;
    }

    /**
     * Updates the tor settings for all methods annotated with SettingsConfig
     */
    public TorSettingsBuilder updateTorSettings() throws Exception {
        for(Method method : getClass().getMethods()) {
            for (Annotation annotation : method.getAnnotations()) {
                if (annotation instanceof SettingsConfig) {
                    method.invoke(this);
                    break;
                }
            }
        }
        return this;
    }

    private static boolean isNullOrEmpty(String value) {
        return value == null || value.isEmpty();
    }

    private static boolean isLocalPortOpen(int port) {
        Socket socket = null;
        try {
            socket = new Socket();
            socket.connect(new InetSocketAddress("127.0.0.1", port), 500);
            return true;
        } catch (Exception e) {
            return false;
        } finally {
            if (socket  != null) {
                try {
                    socket.close();
                } catch (Exception ee) {
                }
            }
        }
    }

    public String asString() {
        return buffer.toString();
    }

    public TorSettingsBuilder automapHostsOnResolve() {
        buffer.append("AutomapHostsOnResolve 1").append('\n');
        return this;
    }

    @SettingsConfig
    public TorSettingsBuilder automapHostsOnResolveFromSettings() {
        return settings.isAutomapHostsOnResolve() ? automapHostsOnResolve() : this;
    }

    public TorSettingsBuilder bridge(String type, String config) {
        if (!isNullOrEmpty(type) && !isNullOrEmpty(config)) {
            buffer.append("Bridge ").append(type).append(' ').append(config).append('\n');
        }
        return this;
    }

    public TorSettingsBuilder bridgeCustom(String config) {
        if (!isNullOrEmpty(config)) {
            buffer.append("Bridge ").append(config).append('\n');
        }
        return this;
    }

    @SettingsConfig
    public TorSettingsBuilder bridgesFromSettings() {
        try {
            addBridgesFromResources();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return this;
    }

    public TorSettingsBuilder configurePluggableTransportsFromSettings(File pluggableTransportClient) throws IOException {
        if (pluggableTransportClient == null) {
            return this;
        }

        if (!pluggableTransportClient.exists()) {
            throw new IOException("Bridge binary does not exist: " + pluggableTransportClient
                    .getCanonicalPath());
        }

        if (!pluggableTransportClient.canExecute()) {
            throw new IOException("Bridge binary is not executable: " + pluggableTransportClient
                    .getCanonicalPath());
        }

        transportPlugin(pluggableTransportClient.getCanonicalPath());
        return this;
    }

    public TorSettingsBuilder cookieAuthentication() {
        buffer.append("CookieAuthentication 1 ").append('\n');
        buffer.append("CookieAuthFile ").append(context.getConfig().getCookieAuthFile().getAbsolutePath()).append("\n");
        return this;
    }

    @SettingsConfig
    public TorSettingsBuilder cookieAuthenticationFromSettings() {
        return settings.hasCookieAuthentication() ? cookieAuthentication() : this;
    }
    
    public TorSettingsBuilder connectionPadding() {
        buffer.append("ConnectionPadding 1").append('\n');
        return this;
    }

    @SettingsConfig
    public TorSettingsBuilder connectionPaddingFromSettings() {
        return settings.hasConnectionPadding() ? connectionPadding() : this;
    }

    public TorSettingsBuilder controlPortWriteToFile(String controlPortFile) {
        buffer.append("ControlPortWriteToFile ").append(controlPortFile).append('\n');
        buffer.append("ControlPort auto\n");
        return this;
    }

    @SettingsConfig
    public TorSettingsBuilder controlPortWriteToFileFromConfig() {
        return controlPortWriteToFile(context.config.getControlPortFile().getAbsolutePath());
    }

    public TorSettingsBuilder debugLogs() {
        buffer.append("Log debug syslog").append('\n');
        buffer.append("Log info syslog").append('\n');
        buffer.append("SafeLogging 0").append('\n');
        return this;
    }

    @SettingsConfig
    public TorSettingsBuilder debugLogsFromSettings() {
        return settings.hasDebugLogs() ? debugLogs() : this;
    }

    public TorSettingsBuilder disableNetwork() {
        buffer.append("DisableNetwork 1").append('\n');
        return this;
    }

    @SettingsConfig
    public TorSettingsBuilder disableNetworkFromSettings() {
        return settings.disableNetwork() ? disableNetwork() : this;
    }

    public TorSettingsBuilder dnsPort(String dnsPort) {
        if (!isNullOrEmpty(dnsPort)) buffer.append("DNSPort ").append(dnsPort).append('\n');
        return this;
    }

    @SettingsConfig
    public TorSettingsBuilder dnsPortFromSettings() {
        return dnsPort(settings.dnsPort());
    }

    public TorSettingsBuilder dontUseBridges() {
        buffer.append("UseBridges 0").append('\n');
        return this;
    }

    public TorSettingsBuilder dormantCanceledByStartup() {
        buffer.append("DormantCanceledByStartup 1\n");
        return this;
    }

    @SettingsConfig
    public TorSettingsBuilder dormantCanceledByStartupFromSettings() {
        if (settings.hasDormantCanceledByStartup()) {
            dormantCanceledByStartup();
        }
        return this;
    }

    public TorSettingsBuilder entryNodes(String entryNodes) {
        if (!isNullOrEmpty(entryNodes))
            buffer.append("EntryNodes ").append(entryNodes).append('\n');
        return this;
    }

    public TorSettingsBuilder excludeNodes(String excludeNodes) {
        if (!isNullOrEmpty(excludeNodes))
            buffer.append("ExcludeNodes ").append(excludeNodes).append('\n');
        return this;
    }

    public TorSettingsBuilder exitNodes(String exitNodes) {
        if (!isNullOrEmpty(exitNodes))
            buffer.append("ExitNodes ").append(exitNodes).append('\n');
        return this;
    }

    public TorSettingsBuilder geoIpFile(String path) {
        if (!isNullOrEmpty(path)) buffer.append("GeoIPFile ").append(path).append('\n');
        return this;
    }

    public TorSettingsBuilder geoIpV6File(String path) {
        if (!isNullOrEmpty(path)) buffer.append("GeoIPv6File ").append(path).append('\n');
        return this;
    }

    public TorSettingsBuilder httpTunnelPort(int port, String isolationFlags) {
        buffer.append("HTTPTunnelPort ").append(port);
        if (!isNullOrEmpty(isolationFlags)) {
            buffer.append(" ").append(isolationFlags);
        }
        buffer.append('\n');
        return this;
    }

    @SettingsConfig
    public TorSettingsBuilder httpTunnelPortFromSettings() {
        return httpTunnelPort(settings.getHttpTunnelPort(),
                settings.hasIsolationAddressFlagForTunnel() ? "IsolateDestAddr" : null);
    }

    public TorSettingsBuilder line(String value) {
        if (!isNullOrEmpty(value)) buffer.append(value).append("\n");
        return this;
    }

    public TorSettingsBuilder makeNonExitRelay(String dnsFile, int orPort, String nickname) {
        buffer.append("ServerDNSResolvConfFile ").append(dnsFile).append('\n');
        buffer.append("ORPort ").append(orPort).append('\n');
        buffer.append("Nickname ").append(nickname).append('\n');
        buffer.append("ExitPolicy reject *:*").append('\n');
        return this;
    }

    /**
     * Sets the entry/exit/exclude nodes
     */
    @SettingsConfig
    public TorSettingsBuilder nodesFromSettings() {
        entryNodes(settings.getEntryNodes()).exitNodes(settings.getExitNodes())
                .excludeNodes(settings.getExcludeNodes());
        return this;
    }

    /**
     * Adds non exit relay to builder. This method uses a default google nameserver.
     */
    @SettingsConfig
    public TorSettingsBuilder nonExitRelayFromSettings() {
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

    public TorSettingsBuilder proxyOnAllInterfaces() {
        buffer.append("SocksListenAddress 0.0.0.0").append('\n');
        return this;
    }

    @SettingsConfig
    public TorSettingsBuilder proxyOnAllInterfacesFromSettings() {
        return settings.hasOpenProxyOnAllInterfaces() ? proxyOnAllInterfaces() : this;
    }

    /**
     * Set socks5 proxy with no authentication. This can be set if yo uare using a VPN.
     */
    public TorSettingsBuilder proxySocks5(String host, String port) {
        buffer.append("socks5Proxy ").append(host).append(':').append(port).append('\n');
        return this;
    }

    @SettingsConfig
    public TorSettingsBuilder proxySocks5FromSettings() {
        return (settings.useSocks5() && !settings.hasBridges()) ? proxySocks5(settings
                        .getProxySocks5Host(),
                settings.getProxySocks5ServerPort()) : this;
    }

    /**
     * Sets proxyWithAuthentication information. If proxyType, proxyHost or proxyPort is empty,
     * then this method does nothing.
     */
    public TorSettingsBuilder proxyWithAuthentication(String proxyType, String proxyHost, String
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

    @SettingsConfig
    public TorSettingsBuilder proxyWithAuthenticationFromSettings() {
        return (!settings.useSocks5() && !settings.hasBridges()) ? proxyWithAuthentication
                (settings.getProxyType(), settings.getProxyHost(),
                        settings.getProxyPort(), settings.getProxyUser(), settings
                                .getProxyPassword()) :
                this;
    }

    public TorSettingsBuilder reachableAddressPorts(String reachableAddressesPorts) {
        if (!isNullOrEmpty(reachableAddressesPorts))
            buffer.append("ReachableAddresses ").append(reachableAddressesPorts).append('\n');
        return this;
    }

    @SettingsConfig
    public TorSettingsBuilder reachableAddressesFromSettings() {
        return settings.hasReachableAddress() ? reachableAddressPorts(settings
                .getReachableAddressPorts()) : this;

    }

    public TorSettingsBuilder reducedConnectionPadding() {
        buffer.append("ReducedConnectionPadding 1").append('\n');
        return this;
    }

    @SettingsConfig
    public TorSettingsBuilder reducedConnectionPaddingFromSettings() {
        return settings.hasReducedConnectionPadding() ? reducedConnectionPadding() : this;
    }

    public void reset() {
        buffer = new StringBuffer();
    }

    @SettingsConfig
    public TorSettingsBuilder runAsDaemonFromSettings() {
        return settings.runAsDaemon() ? runAsDaemon() : this;
    }

    public TorSettingsBuilder runAsDaemon() {
        buffer.append("RunAsDaemon 1").append('\n');
        return this;
    }

    public TorSettingsBuilder safeSocksDisable() {
        buffer.append("SafeSocks 0").append('\n');
        return this;
    }

    public TorSettingsBuilder safeSocksEnable() {
        buffer.append("SafeSocks 1").append('\n');
        return this;
    }

    @SettingsConfig
    public TorSettingsBuilder safeSocksFromSettings() {
        return !settings.hasSafeSocks() ? safeSocksDisable() : safeSocksEnable();
    }

    public TorSettingsBuilder setGeoIpFiles() throws IOException {
        TorConfig torConfig = context.getConfig();
        if (torConfig.getGeoIpFile().exists()) {
            geoIpFile(torConfig.getGeoIpFile().getCanonicalPath())
                    .geoIpV6File(torConfig.getGeoIpv6File().getCanonicalPath());
        }
        return this;
    }

    public TorSettingsBuilder socksPort(String socksPort, String isolationFlag) {
        if (isNullOrEmpty(socksPort)) {
            return this;
        }
        buffer.append("SOCKSPort ").append(socksPort);
        if (!isNullOrEmpty(isolationFlag)) {
            buffer.append(" ").append(isolationFlag);
        }
        buffer.append(" KeepAliveIsolateSOCKSAuth");
        buffer.append(" IPv6Traffic");
        buffer.append(" PreferIPv6");

        buffer.append('\n');
        return this;
    }

    @SettingsConfig
    public TorSettingsBuilder socksPortFromSettings() {
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

    public TorSettingsBuilder strictNodesDisable() {
        buffer.append("StrictNodes 0\n");
        return this;
    }

    public TorSettingsBuilder strictNodesEnable() {
        buffer.append("StrictNodes 1\n");
        return this;
    }

    @SettingsConfig
    public TorSettingsBuilder strictNodesFromSettings() {
        return settings.hasStrictNodes() ? strictNodesEnable() : strictNodesDisable();
    }

    public TorSettingsBuilder testSocksDisable() {
        buffer.append("TestSocks 0").append('\n');
        return this;
    }

    public TorSettingsBuilder testSocksEnable() {
        buffer.append("TestSocks 0").append('\n');
        return this;
    }

    @SettingsConfig
    public TorSettingsBuilder testSocksFromSettings() {
        return !settings.hasTestSocks() ? testSocksDisable() : this;
    }

    @SettingsConfig
    public TorSettingsBuilder torrcCustomFromSettings() throws UnsupportedEncodingException {
        return settings.getCustomTorrc() != null ?
                line(new String(settings.getCustomTorrc().getBytes("US-ASCII"))) : this;
    }

    public TorSettingsBuilder transPort(String transPort) {
        if (!isNullOrEmpty(transPort))
            buffer.append("TransPort ").append(transPort).append('\n');
        return this;
    }

    @SettingsConfig
    public TorSettingsBuilder transPortFromSettings() {
        return transPort(settings.transPort());
    }

    public TorSettingsBuilder transportPlugin(String clientPath) {
        buffer.append("ClientTransportPlugin meek_lite,obfs3,obfs4 exec ").append(clientPath).append('\n');
        return this;
    }

    public TorSettingsBuilder useBridges() {
        buffer.append("UseBridges 1").append('\n');
        return this;
    }

    @SettingsConfig
    public TorSettingsBuilder useBridgesFromSettings() {
        return settings.hasBridges() ? useBridges() : this;
    }

    public TorSettingsBuilder virtualAddressNetwork(String address) {
        if (!isNullOrEmpty(address))
            buffer.append("VirtualAddrNetwork ").append(address).append('\n');
        return this;
    }

    @SettingsConfig
    public TorSettingsBuilder virtualAddressNetworkFromSettings() {
        return virtualAddressNetwork(settings.getVirtualAddressNetwork());
    }

    /**
     * Adds bridges from a resource stream. This relies on the TorInstaller to know how to obtain this stream.
     * These entries may be type-specified like:
     *
     * <code>
     *  obfs3 169.229.59.74:31493 AF9F66B7B04F8FF6F32D455F05135250A16543C9
     * </code>
     *
     * Or it may just be a custom entry like
     *
     * <code>
     *    69.163.45.129:443 9F090DE98CA6F67DEEB1F87EFE7C1BFD884E6E2F
     * </code>
     *
     */
    TorSettingsBuilder addBridgesFromResources() throws IOException {
        if(settings.hasBridges()) {
            InputStream bridgesStream = context.getInstaller().openBridgesStream();
            int formatType = bridgesStream.read();
            if (formatType == 0) {
                addBridges(bridgesStream);
            } else {
                addCustomBridges(bridgesStream);
            }
        }
        return this;
    }

    /**
     * Add bridges from bridges.txt file.
     */
    private void addBridges(InputStream input) {
        if (input == null) {
            return;
        }
        List<Bridge> bridges = readBridgesFromStream(input);
        for (Bridge b : bridges) {
            bridge(b.type, b.config);
        }
    }

    /**
     * Add custom bridges defined by the user. These will have a bridgeType of 'custom' as the first field.
     */
    private void addCustomBridges(InputStream input) {
        if (input == null) {
            return;
        }
        List<Bridge> bridges = readCustomBridgesFromStream(input);
        for (Bridge b : bridges) {
            if (b.type.equals("custom")) {
                bridgeCustom(b.config);
            }
        }
    }

    private static List<Bridge> readBridgesFromStream(InputStream input)  {
        List<Bridge> bridges = new ArrayList<>();
        try {
            BufferedReader br = new BufferedReader(new InputStreamReader(input, "UTF-8"));
            for (String line = br.readLine(); line != null; line = br.readLine()) {
                String[] tokens = line.split("\\s+", 2);
                if (tokens.length != 2) {
                    continue;//bad entry
                }
                bridges.add(new Bridge(tokens[0], tokens[1]));
            }
            br.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return bridges;
    }

    private static List<Bridge> readCustomBridgesFromStream(InputStream input)  {
        List<Bridge> bridges = new ArrayList<>();
        try {
            BufferedReader br = new BufferedReader(new InputStreamReader(input, "UTF-8"));
            for (String line = br.readLine(); line != null; line = br.readLine()) {
                if(line.isEmpty()) {
                    continue;
                }
                bridges.add(new Bridge("custom", line));
            }
            br.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return bridges;
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
