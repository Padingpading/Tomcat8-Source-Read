/*
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.apache.coyote.http11;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import javax.servlet.http.HttpUpgradeHandler;

import org.apache.coyote.AbstractProtocol;
import org.apache.coyote.CompressionConfig;
import org.apache.coyote.Processor;
import org.apache.coyote.Request;
import org.apache.coyote.Response;
import org.apache.coyote.UpgradeProtocol;
import org.apache.coyote.UpgradeToken;
import org.apache.coyote.http11.upgrade.InternalHttpUpgradeHandler;
import org.apache.coyote.http11.upgrade.UpgradeProcessorExternal;
import org.apache.coyote.http11.upgrade.UpgradeProcessorInternal;
import org.apache.tomcat.util.buf.StringUtils;
import org.apache.tomcat.util.net.AbstractEndpoint;
import org.apache.tomcat.util.net.SSLHostConfig;
import org.apache.tomcat.util.net.SocketWrapperBase;
import org.apache.tomcat.util.res.StringManager;

/**
 *
 * 协议处理的抽象类。定义对应公有方法。
 *
 * @translator chenchen6(chenmudu@gmail.com/chenchen6@tuhu.cn)
 */
public abstract class AbstractHttp11Protocol<S> extends AbstractProtocol<S> {

    protected static final StringManager sm =
            StringManager.getManager(AbstractHttp11Protocol.class);

    //http请求的压缩配置。
    private final CompressionConfig compressionConfig = new CompressionConfig();


    public AbstractHttp11Protocol(AbstractEndpoint<S> endpoint) {
        super(endpoint);
        //链接超时时间60s
        setConnectionTimeout(Constants.DEFAULT_CONNECTION_TIMEOUT);
        //链接处理器
        ConnectionHandler<S> cHandler = new ConnectionHandler<>(this);
        setHandler(cHandler);
        //设置连接处理器
        getEndpoint().setHandler(cHandler);
    }

    /**
     * 这个init方法没用看懂。
     * @throws Exception
     */
    @Override
    public void init() throws Exception {
        for (UpgradeProtocol upgradeProtocol : upgradeProtocols) {
            //协议的升级
            configureUpgradeProtocol(upgradeProtocol);
        }
        /**
         * {@link AbstractProtocol#init()}
         */
        super.init();
    }


    @Override
    protected String getProtocolName() {
        return "Http";
    }


    /**
     * {@inheritDoc}
     * <p>
     * Over-ridden here to make the method visible to nested classes.
     */
    @Override
    protected AbstractEndpoint<S> getEndpoint() {
        return super.getEndpoint();
    }


    // ------------------------------------------------ HTTP specific properties
    // ------------------------------------------ managed in the ProtocolHandler

    private boolean useKeepAliveResponseHeader = true;
    public boolean getUseKeepAliveResponseHeader() {
        return useKeepAliveResponseHeader;
    }
    public void setUseKeepAliveResponseHeader(boolean useKeepAliveResponseHeader) {
        this.useKeepAliveResponseHeader = useKeepAliveResponseHeader;
    }


    private String relaxedPathChars = null;
    public String getRelaxedPathChars() {
        return relaxedPathChars;
    }
    public void setRelaxedPathChars(String relaxedPathChars) {
        this.relaxedPathChars = relaxedPathChars;
    }


    private String relaxedQueryChars = null;
    public String getRelaxedQueryChars() {
        return relaxedQueryChars;
    }
    public void setRelaxedQueryChars(String relaxedQueryChars) {
        this.relaxedQueryChars = relaxedQueryChars;
    }


    private boolean allowHostHeaderMismatch = true;
    /**
     * Will Tomcat accept an HTTP 1.1 request where the host header does not
     * agree with the host specified (if any) in the request line?
     *
     * @return {@code true} if Tomcat will allow such requests, otherwise
     *         {@code false}
     */
    public boolean getAllowHostHeaderMismatch() {
        return allowHostHeaderMismatch;
    }
    /**
     * Will Tomcat accept an HTTP 1.1 request where the host header does not
     * agree with the host specified (if any) in the request line?
     *
     * @param allowHostHeaderMismatch {@code true} to allow such requests,
     *                                {@code false} to reject them with a 400
     */
    public void setAllowHostHeaderMismatch(boolean allowHostHeaderMismatch) {
        this.allowHostHeaderMismatch = allowHostHeaderMismatch;
    }


    private boolean rejectIllegalHeaderName = false;
    /**
     * If an HTTP request is received that contains an illegal header name (i.e.
     * the header name is not a token) will the request be rejected (with a 400
     * response) or will the illegal header be ignored.
     *
     * @return {@code true} if the request will be rejected or {@code false} if
     *         the header will be ignored
     */
    public boolean getRejectIllegalHeaderName() { return rejectIllegalHeaderName; }
    /**
     * If an HTTP request is received that contains an illegal header name (i.e.
     * the header name is not a token) should the request be rejected (with a
     * 400 response) or should the illegal header be ignored.
     *
     * @param rejectIllegalHeaderName   {@code true} to reject requests with
     *                                  illegal header names, {@code false} to
     *                                  ignore the header
     */
    public void setRejectIllegalHeaderName(boolean rejectIllegalHeaderName) {
        this.rejectIllegalHeaderName = rejectIllegalHeaderName;
    }


    /**
     * Maximum size of the post which will be saved when processing certain
     * requests, such as a POST.
     * 处理某些请求(如某个post)时将保存的post的最大大小。
     */
    private int maxSavePostSize = 4 * 1024;
    public int getMaxSavePostSize() { return maxSavePostSize; }
    public void setMaxSavePostSize(int valueI) { maxSavePostSize = valueI; }


    /**
     * Maximum size of the HTTP message header.
     */
    private int maxHttpHeaderSize = 8 * 1024;
    public int getMaxHttpHeaderSize() { return maxHttpHeaderSize; }
    public void setMaxHttpHeaderSize(int valueI) { maxHttpHeaderSize = valueI; }


    /**
     * Specifies a different (usually  longer) connection timeout during data
     * upload.
     */
    private int connectionUploadTimeout = 300000;
    public int getConnectionUploadTimeout() { return connectionUploadTimeout; }
    public void setConnectionUploadTimeout(int i) {
        connectionUploadTimeout = i;
    }


    /**
     * If true, the connectionUploadTimeout will be ignored and the regular
     * socket timeout will be used for the full duration of the connection.
     *
     * 如果为真，则忽略connectionUploadTimeout，并在整个连接期间使用常规的套接字超时。
     */
    private boolean disableUploadTimeout = true;
    public boolean getDisableUploadTimeout() { return disableUploadTimeout; }
    public void setDisableUploadTimeout(boolean isDisabled) {
        disableUploadTimeout = isDisabled;
    }


    public String getCompression() {
        return compressionConfig.getCompression();
    }
    public void setCompression(String valueS) {
        compressionConfig.setCompression(valueS);
    }


    public String getNoCompressionUserAgents() {
        return compressionConfig.getNoCompressionUserAgents();
    }
    public void setNoCompressionUserAgents(String valueS) {
        compressionConfig.setNoCompressionUserAgents(valueS);
    }


    /**
     * @return See {@link #getCompressibleMimeType()}
     * @deprecated Use {@link #getCompressibleMimeType()}
     */
    @Deprecated
    public String getCompressableMimeType() {
        return getCompressibleMimeType();
    }
    /**
     * @param valueS See {@link #setCompressibleMimeType(String)}
     * @deprecated Use {@link #setCompressibleMimeType(String)}
     */
    @Deprecated
    public void setCompressableMimeType(String valueS) {
        setCompressibleMimeType(valueS);
    }
    /**
     * @return See {@link #getCompressibleMimeTypes()}
     * @deprecated Use {@link #getCompressibleMimeTypes()}
     */
    @Deprecated
    public String[] getCompressableMimeTypes() {
        return getCompressibleMimeTypes();
    }


    public String getCompressibleMimeType() {
        return compressionConfig.getCompressibleMimeType();
    }
    public void setCompressibleMimeType(String valueS) {
        compressionConfig.setCompressibleMimeType(valueS);
    }
    public String[] getCompressibleMimeTypes() {
        return compressionConfig.getCompressibleMimeTypes();
    }


    public int getCompressionMinSize() {
        return compressionConfig.getCompressionMinSize();
    }
    public void setCompressionMinSize(int valueI) {
        compressionConfig.setCompressionMinSize(valueI);
    }


    public boolean useCompression(Request request, Response response) {
        return compressionConfig.useCompression(request, response);
    }


    /**
     * Regular expression that defines the User agents which should be
     * restricted to HTTP/1.0 support.
     */
    private String restrictedUserAgents = null;
    public String getRestrictedUserAgents() { return restrictedUserAgents; }
    public void setRestrictedUserAgents(String valueS) {
        restrictedUserAgents = valueS;
    }


    /**
     * Server header.
     */
    private String server;
    public String getServer() { return server; }
    public void setServer( String server ) {
        this.server = server;
    }


    private boolean serverRemoveAppProvidedValues = false;
    public boolean getServerRemoveAppProvidedValues() { return serverRemoveAppProvidedValues; }
    public void setServerRemoveAppProvidedValues(boolean serverRemoveAppProvidedValues) {
        this.serverRemoveAppProvidedValues = serverRemoveAppProvidedValues;
    }


    /**
     * Maximum size of trailing headers in bytes
     */
    private int maxTrailerSize = 8192;
    public int getMaxTrailerSize() { return maxTrailerSize; }
    public void setMaxTrailerSize(int maxTrailerSize) {
        this.maxTrailerSize = maxTrailerSize;
    }


    /**
     * Maximum size of extension information in chunked encoding
     */
    private int maxExtensionSize = 8192;
    public int getMaxExtensionSize() { return maxExtensionSize; }
    public void setMaxExtensionSize(int maxExtensionSize) {
        this.maxExtensionSize = maxExtensionSize;
    }


    /**
     * Maximum amount of request body to swallow.
     */
    private int maxSwallowSize = 2 * 1024 * 1024;
    public int getMaxSwallowSize() { return maxSwallowSize; }
    public void setMaxSwallowSize(int maxSwallowSize) {
        this.maxSwallowSize = maxSwallowSize;
    }


    /**
     * This field indicates if the protocol is treated as if it is secure. This
     * normally means https is being used but can be used to fake https e.g
     * behind a reverse proxy.
     */
    private boolean secure;
    public boolean getSecure() { return secure; }
    public void setSecure(boolean b) {
        secure = b;
    }


    /**
     * The names of headers that are allowed to be sent via a trailer when using
     * chunked encoding. They are stored in lower case.
     */
    private Set<String> allowedTrailerHeaders =
            Collections.newSetFromMap(new ConcurrentHashMap<String, Boolean>());
    public void setAllowedTrailerHeaders(String commaSeparatedHeaders) {
        // Jump through some hoops so we don't end up with an empty set while
        // doing updates.
        Set<String> toRemove = new HashSet<>();
        toRemove.addAll(allowedTrailerHeaders);
        if (commaSeparatedHeaders != null) {
            String[] headers = commaSeparatedHeaders.split(",");
            for (String header : headers) {
                String trimmedHeader = header.trim().toLowerCase(Locale.ENGLISH);
                if (toRemove.contains(trimmedHeader)) {
                    toRemove.remove(trimmedHeader);
                } else {
                    allowedTrailerHeaders.add(trimmedHeader);
                }
            }
            allowedTrailerHeaders.removeAll(toRemove);
        }
    }
    protected Set<String> getAllowedTrailerHeadersInternal() {
        return allowedTrailerHeaders;
    }
    public String getAllowedTrailerHeaders() {
        // Chances of a size change between these lines are small enough that a
        // sync is unnecessary.
        List<String> copy = new ArrayList<>(allowedTrailerHeaders.size());
        copy.addAll(allowedTrailerHeaders);
        return StringUtils.join(copy);
    }
    public void addAllowedTrailerHeader(String header) {
        if (header != null) {
            allowedTrailerHeaders.add(header.trim().toLowerCase(Locale.ENGLISH));
        }
    }
    public void removeAllowedTrailerHeader(String header) {
        if (header != null) {
            allowedTrailerHeaders.remove(header.trim().toLowerCase(Locale.ENGLISH));
        }
    }


    /**
     * The upgrade protocol instances configured.
     */
    private final List<UpgradeProtocol> upgradeProtocols = new ArrayList<>();
    @Override
    public void addUpgradeProtocol(UpgradeProtocol upgradeProtocol) {
        upgradeProtocols.add(upgradeProtocol);
    }
    @Override
    public UpgradeProtocol[] findUpgradeProtocols() {
        return upgradeProtocols.toArray(new UpgradeProtocol[0]);
    }

    /**
     * The protocols that are available via internal Tomcat support for access
     * via HTTP upgrade.
     */
    private final Map<String,UpgradeProtocol> httpUpgradeProtocols = new HashMap<>();
    /**
     * The protocols that are available via internal Tomcat support for access
     * via ALPN negotiation.
     */
    private final Map<String,UpgradeProtocol> negotiatedProtocols = new HashMap<>();
    private void configureUpgradeProtocol(UpgradeProtocol upgradeProtocol) {
        // HTTP Upgrade
        String httpUpgradeName = upgradeProtocol.getHttpUpgradeName(getEndpoint().isSSLEnabled());
        boolean httpUpgradeConfigured = false;
        if (httpUpgradeName != null && httpUpgradeName.length() > 0) {
            httpUpgradeProtocols.put(httpUpgradeName, upgradeProtocol);
            httpUpgradeConfigured = true;
            getLog().info(sm.getString("abstractHttp11Protocol.httpUpgradeConfigured",
                    getName(), httpUpgradeName));
        }


        // ALPN
        String alpnName = upgradeProtocol.getAlpnName();
        if (alpnName != null && alpnName.length() > 0) {
            if (getEndpoint().isAlpnSupported()) {
                negotiatedProtocols.put(alpnName, upgradeProtocol);
                getEndpoint().addNegotiatedProtocol(alpnName);
                getLog().info(sm.getString("abstractHttp11Protocol.alpnConfigured",
                        getName(), alpnName));
            } else {
                if (!httpUpgradeConfigured) {
                    // ALPN is not supported by this connector and the upgrade
                    // protocol implementation does not support standard HTTP
                    // upgrade so there is no way available to enable support
                    // for this protocol.
                    getLog().error(sm.getString("abstractHttp11Protocol.alpnWithNoAlpn",
                            upgradeProtocol.getClass().getName(), alpnName, getName()));
                }
            }
        }
    }
    @Override
    public UpgradeProtocol getNegotiatedProtocol(String negotiatedName) {
        return negotiatedProtocols.get(negotiatedName);
    }
    @Override
    public UpgradeProtocol getUpgradeProtocol(String upgradedName) {
        return httpUpgradeProtocols.get(upgradedName);
    }


    // ------------------------------------------------ HTTP specific properties
    // ------------------------------------------ passed through to the EndPoint

    public boolean isSSLEnabled() { return getEndpoint().isSSLEnabled();}
    public void setSSLEnabled(boolean SSLEnabled) {
        getEndpoint().setSSLEnabled(SSLEnabled);
    }


    public boolean getUseSendfile() { return getEndpoint().getUseSendfile(); }
    public void setUseSendfile(boolean useSendfile) { getEndpoint().setUseSendfile(useSendfile); }


    /**
     * @return The maximum number of requests which can be performed over a
     *         keep-alive connection. The default is the same as for Apache HTTP
     *         Server (100).
     */
    public int getMaxKeepAliveRequests() {
        return getEndpoint().getMaxKeepAliveRequests();
    }
    public void setMaxKeepAliveRequests(int mkar) {
        getEndpoint().setMaxKeepAliveRequests(mkar);
    }


    // ----------------------------------------------- HTTPS specific properties
    // ------------------------------------------ passed through to the EndPoint

    public String getDefaultSSLHostConfigName() {
        return getEndpoint().getDefaultSSLHostConfigName();
    }
    public void setDefaultSSLHostConfigName(String defaultSSLHostConfigName) {
        getEndpoint().setDefaultSSLHostConfigName(defaultSSLHostConfigName);
        if (defaultSSLHostConfig != null) {
            defaultSSLHostConfig.setHostName(defaultSSLHostConfigName);
        }
    }


    @Override
    public void addSslHostConfig(SSLHostConfig sslHostConfig) {
        getEndpoint().addSslHostConfig(sslHostConfig);
    }


    @Override
    public SSLHostConfig[] findSslHostConfigs() {
        return getEndpoint().findSslHostConfigs();
    }


    public void reloadSslHostConfigs() {
        getEndpoint().reloadSslHostConfigs();
    }


    public void reloadSslHostConfig(String hostName) {
        getEndpoint().reloadSslHostConfig(hostName);
    }


    // ----------------------------------------------- HTTPS specific properties
    // -------------------------------------------- Handled via an SSLHostConfig

    private SSLHostConfig defaultSSLHostConfig = null;
    private void registerDefaultSSLHostConfig() {
        if (defaultSSLHostConfig == null) {
            for (SSLHostConfig sslHostConfig : findSslHostConfigs()) {
                if (getDefaultSSLHostConfigName().equals(sslHostConfig.getHostName())) {
                    defaultSSLHostConfig = sslHostConfig;
                    break;
                }
            }
            if (defaultSSLHostConfig == null) {
                defaultSSLHostConfig = new SSLHostConfig();
                defaultSSLHostConfig.setHostName(getDefaultSSLHostConfigName());
                getEndpoint().addSslHostConfig(defaultSSLHostConfig);
            }
        }
    }


    // TODO: All of these SSL getters and setters can be removed once it is no
    // longer necessary to support the old configuration attributes (Tomcat 10?)

    public String getSslEnabledProtocols() {
        registerDefaultSSLHostConfig();
        return StringUtils.join(defaultSSLHostConfig.getEnabledProtocols());
    }
    public void setSslEnabledProtocols(String enabledProtocols) {
        registerDefaultSSLHostConfig();
        defaultSSLHostConfig.setProtocols(enabledProtocols);
    }
    public String getSSLProtocol() {
        registerDefaultSSLHostConfig();
        return StringUtils.join(defaultSSLHostConfig.getEnabledProtocols());
    }
    public void setSSLProtocol(String sslProtocol) {
        registerDefaultSSLHostConfig();
        defaultSSLHostConfig.setProtocols(sslProtocol);
    }


    public String getKeystoreFile() {
        registerDefaultSSLHostConfig();
        return defaultSSLHostConfig.getCertificateKeystoreFile();
    }
    public void setKeystoreFile(String keystoreFile) {
        registerDefaultSSLHostConfig();
        defaultSSLHostConfig.setCertificateKeystoreFile(keystoreFile);
    }
    public String getSSLCertificateChainFile() {
        registerDefaultSSLHostConfig();
        return defaultSSLHostConfig.getCertificateChainFile();
    }
    public void setSSLCertificateChainFile(String certificateChainFile) {
        registerDefaultSSLHostConfig();
        defaultSSLHostConfig.setCertificateChainFile(certificateChainFile);
    }
    public String getSSLCertificateFile() {
        registerDefaultSSLHostConfig();
        return defaultSSLHostConfig.getCertificateFile();
    }
    public void setSSLCertificateFile(String certificateFile) {
        registerDefaultSSLHostConfig();
        defaultSSLHostConfig.setCertificateFile(certificateFile);
    }
    public String getSSLCertificateKeyFile() {
        registerDefaultSSLHostConfig();
        return defaultSSLHostConfig.getCertificateKeyFile();
    }
    public void setSSLCertificateKeyFile(String certificateKeyFile) {
        registerDefaultSSLHostConfig();
        defaultSSLHostConfig.setCertificateKeyFile(certificateKeyFile);
    }


    public String getAlgorithm() {
        registerDefaultSSLHostConfig();
        return defaultSSLHostConfig.getKeyManagerAlgorithm();
    }
    public void setAlgorithm(String keyManagerAlgorithm) {
        registerDefaultSSLHostConfig();
        defaultSSLHostConfig.setKeyManagerAlgorithm(keyManagerAlgorithm);
    }


    public String getClientAuth() {
        registerDefaultSSLHostConfig();
        return defaultSSLHostConfig.getCertificateVerificationAsString();
    }
    public void setClientAuth(String certificateVerification) {
        registerDefaultSSLHostConfig();
        defaultSSLHostConfig.setCertificateVerification(certificateVerification);
    }


    public String getSSLVerifyClient() {
        registerDefaultSSLHostConfig();
        return defaultSSLHostConfig.getCertificateVerificationAsString();
    }
    public void setSSLVerifyClient(String certificateVerification) {
        registerDefaultSSLHostConfig();
        defaultSSLHostConfig.setCertificateVerification(certificateVerification);
    }


    public int getTrustMaxCertLength(){
        registerDefaultSSLHostConfig();
        return defaultSSLHostConfig.getCertificateVerificationDepth();
    }
    public void setTrustMaxCertLength(int certificateVerificationDepth){
        registerDefaultSSLHostConfig();
        defaultSSLHostConfig.setCertificateVerificationDepth(certificateVerificationDepth);
    }
    public int getSSLVerifyDepth() {
        registerDefaultSSLHostConfig();
        return defaultSSLHostConfig.getCertificateVerificationDepth();
    }
    public void setSSLVerifyDepth(int certificateVerificationDepth) {
        registerDefaultSSLHostConfig();
        defaultSSLHostConfig.setCertificateVerificationDepth(certificateVerificationDepth);
    }


    public String getUseServerCipherSuitesOrder() {
        registerDefaultSSLHostConfig();
        return defaultSSLHostConfig.getHonorCipherOrder();
    }
    public void setUseServerCipherSuitesOrder(String honorCipherOrder) {
        registerDefaultSSLHostConfig();
        defaultSSLHostConfig.setHonorCipherOrder(honorCipherOrder);
    }
    public String getSSLHonorCipherOrder() {
        registerDefaultSSLHostConfig();
        return defaultSSLHostConfig.getHonorCipherOrder();
    }
    public void setSSLHonorCipherOrder(String honorCipherOrder) {
        registerDefaultSSLHostConfig();
        defaultSSLHostConfig.setHonorCipherOrder(honorCipherOrder);
    }


    public String getCiphers() {
        registerDefaultSSLHostConfig();
        return defaultSSLHostConfig.getCiphers();
    }
    public void setCiphers(String ciphers) {
        registerDefaultSSLHostConfig();
        defaultSSLHostConfig.setCiphers(ciphers);
    }
    public String getSSLCipherSuite() {
        registerDefaultSSLHostConfig();
        return defaultSSLHostConfig.getCiphers();
    }
    public void setSSLCipherSuite(String ciphers) {
        registerDefaultSSLHostConfig();
        defaultSSLHostConfig.setCiphers(ciphers);
    }


    public String getKeystorePass() {
        registerDefaultSSLHostConfig();
        return defaultSSLHostConfig.getCertificateKeystorePassword();
    }
    public void setKeystorePass(String certificateKeystorePassword) {
        registerDefaultSSLHostConfig();
        defaultSSLHostConfig.setCertificateKeystorePassword(certificateKeystorePassword);
    }


    public String getKeyPass() {
        registerDefaultSSLHostConfig();
        return defaultSSLHostConfig.getCertificateKeyPassword();
    }
    public void setKeyPass(String certificateKeyPassword) {
        registerDefaultSSLHostConfig();
        defaultSSLHostConfig.setCertificateKeyPassword(certificateKeyPassword);
    }
    public String getSSLPassword() {
        registerDefaultSSLHostConfig();
        return defaultSSLHostConfig.getCertificateKeyPassword();
    }
    public void setSSLPassword(String certificateKeyPassword) {
        registerDefaultSSLHostConfig();
        defaultSSLHostConfig.setCertificateKeyPassword(certificateKeyPassword);
    }


    public String getCrlFile(){
        registerDefaultSSLHostConfig();
        return defaultSSLHostConfig.getCertificateRevocationListFile();
    }
    public void setCrlFile(String certificateRevocationListFile){
        registerDefaultSSLHostConfig();
        defaultSSLHostConfig.setCertificateRevocationListFile(certificateRevocationListFile);
    }
    public String getSSLCARevocationFile() {
        registerDefaultSSLHostConfig();
        return defaultSSLHostConfig.getCertificateRevocationListFile();
    }
    public void setSSLCARevocationFile(String certificateRevocationListFile) {
        registerDefaultSSLHostConfig();
        defaultSSLHostConfig.setCertificateRevocationListFile(certificateRevocationListFile);
    }
    public String getSSLCARevocationPath() {
        registerDefaultSSLHostConfig();
        return defaultSSLHostConfig.getCertificateRevocationListPath();
    }
    public void setSSLCARevocationPath(String certificateRevocationListPath) {
        registerDefaultSSLHostConfig();
        defaultSSLHostConfig.setCertificateRevocationListPath(certificateRevocationListPath);
    }


    public String getKeystoreType() {
        registerDefaultSSLHostConfig();
        return defaultSSLHostConfig.getCertificateKeystoreType();
    }
    public void setKeystoreType(String certificateKeystoreType) {
        registerDefaultSSLHostConfig();
        defaultSSLHostConfig.setCertificateKeystoreType(certificateKeystoreType);
    }


    public String getKeystoreProvider() {
        registerDefaultSSLHostConfig();
        return defaultSSLHostConfig.getCertificateKeystoreProvider();
    }
    public void setKeystoreProvider(String certificateKeystoreProvider) {
        registerDefaultSSLHostConfig();
        defaultSSLHostConfig.setCertificateKeystoreProvider(certificateKeystoreProvider);
    }


    public String getKeyAlias() {
        registerDefaultSSLHostConfig();
        return defaultSSLHostConfig.getCertificateKeyAlias();
    }
    public void setKeyAlias(String certificateKeyAlias) {
        registerDefaultSSLHostConfig();
        defaultSSLHostConfig.setCertificateKeyAlias(certificateKeyAlias);
    }


    public String getTruststoreAlgorithm(){
        registerDefaultSSLHostConfig();
        return defaultSSLHostConfig.getTruststoreAlgorithm();
    }
    public void setTruststoreAlgorithm(String truststoreAlgorithm){
        registerDefaultSSLHostConfig();
        defaultSSLHostConfig.setTruststoreAlgorithm(truststoreAlgorithm);
    }


    public String getTruststoreFile(){
        registerDefaultSSLHostConfig();
        return defaultSSLHostConfig.getTruststoreFile();
    }
    public void setTruststoreFile(String truststoreFile){
        registerDefaultSSLHostConfig();
        defaultSSLHostConfig.setTruststoreFile(truststoreFile);
    }


    public String getTruststorePass(){
        registerDefaultSSLHostConfig();
        return defaultSSLHostConfig.getTruststorePassword();
    }
    public void setTruststorePass(String truststorePassword){
        registerDefaultSSLHostConfig();
        defaultSSLHostConfig.setTruststorePassword(truststorePassword);
    }


    public String getTruststoreType(){
        registerDefaultSSLHostConfig();
        return defaultSSLHostConfig.getTruststoreType();
    }
    public void setTruststoreType(String truststoreType){
        registerDefaultSSLHostConfig();
        defaultSSLHostConfig.setTruststoreType(truststoreType);
    }


    public String getTruststoreProvider(){
        registerDefaultSSLHostConfig();
        return defaultSSLHostConfig.getTruststoreProvider();
    }
    public void setTruststoreProvider(String truststoreProvider){
        registerDefaultSSLHostConfig();
        defaultSSLHostConfig.setTruststoreProvider(truststoreProvider);
    }


    public String getSslProtocol() {
        registerDefaultSSLHostConfig();
        return defaultSSLHostConfig.getSslProtocol();
    }
    public void setSslProtocol(String sslProtocol) {
        registerDefaultSSLHostConfig();
        defaultSSLHostConfig.setSslProtocol(sslProtocol);
    }


    public int getSessionCacheSize(){
        registerDefaultSSLHostConfig();
        return defaultSSLHostConfig.getSessionCacheSize();
    }
    public void setSessionCacheSize(int sessionCacheSize){
        registerDefaultSSLHostConfig();
        defaultSSLHostConfig.setSessionCacheSize(sessionCacheSize);
    }


    public int getSessionTimeout(){
        registerDefaultSSLHostConfig();
        return defaultSSLHostConfig.getSessionTimeout();
    }
    public void setSessionTimeout(int sessionTimeout){
        registerDefaultSSLHostConfig();
        defaultSSLHostConfig.setSessionTimeout(sessionTimeout);
    }


    public String getSSLCACertificatePath() {
        registerDefaultSSLHostConfig();
        return defaultSSLHostConfig.getCaCertificatePath();
    }
    public void setSSLCACertificatePath(String caCertificatePath) {
        registerDefaultSSLHostConfig();
        defaultSSLHostConfig.setCaCertificatePath(caCertificatePath);
    }


    public String getSSLCACertificateFile() {
        registerDefaultSSLHostConfig();
        return defaultSSLHostConfig.getCaCertificateFile();
    }
    public void setSSLCACertificateFile(String caCertificateFile) {
        registerDefaultSSLHostConfig();
        defaultSSLHostConfig.setCaCertificateFile(caCertificateFile);
    }


    public boolean getSSLDisableCompression() {
        registerDefaultSSLHostConfig();
        return defaultSSLHostConfig.getDisableCompression();
    }
    public void setSSLDisableCompression(boolean disableCompression) {
        registerDefaultSSLHostConfig();
        defaultSSLHostConfig.setDisableCompression(disableCompression);
    }


    public boolean getSSLDisableSessionTickets() {
        registerDefaultSSLHostConfig();
        return defaultSSLHostConfig.getDisableSessionTickets();
    }
    public void setSSLDisableSessionTickets(boolean disableSessionTickets) {
        registerDefaultSSLHostConfig();
        defaultSSLHostConfig.setDisableSessionTickets(disableSessionTickets);
    }


    public String getTrustManagerClassName() {
        registerDefaultSSLHostConfig();
        return defaultSSLHostConfig.getTrustManagerClassName();
    }
    public void setTrustManagerClassName(String trustManagerClassName) {
        registerDefaultSSLHostConfig();
        defaultSSLHostConfig.setTrustManagerClassName(trustManagerClassName);
    }


    // ------------------------------------------------------------- Common code

    /**
     * 调用 Http11Processor的构造方法.
     * @return
     */
    @Override
    protected Processor createProcessor() {
        //1.创建Http11处理器。
        Http11Processor processor = new Http11Processor(this, getEndpoint());
        //2.设置各种属性。
        //2.1 设置适配器。
        processor.setAdapter(getAdapter());
        //2.1 KeepAlive情况下 每个Socket处理的最多个请求次数(默认100)。
        processor.setMaxKeepAliveRequests(getMaxKeepAliveRequests());
        //2.2 设置KeepAlive的超时时间为300000ms。 == 300S.
        processor.setConnectionUploadTimeout(getConnectionUploadTimeout());
        //2.3 设置上传requestBody单独的超时时间。
        processor.setDisableUploadTimeout(getDisableUploadTimeout());
        //2.4 忽略。
        processor.setRestrictedUserAgents(getRestrictedUserAgents());
        //2.5 设置SSL相关缓冲区保存的Post的大小。
        processor.setMaxSavePostSize(getMaxSavePostSize());
        return processor;
    }


    @Override
    protected Processor createUpgradeProcessor(
            SocketWrapperBase<?> socket,
            UpgradeToken upgradeToken) {
        HttpUpgradeHandler httpUpgradeHandler = upgradeToken.getHttpUpgradeHandler();
        if (httpUpgradeHandler instanceof InternalHttpUpgradeHandler) {
            return new UpgradeProcessorInternal(socket, upgradeToken);
        } else {
            return new UpgradeProcessorExternal(socket, upgradeToken);
        }
    }
}
