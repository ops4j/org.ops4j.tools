/*
 * Copyright 2021 OPS4J.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.ops4j.tools.jira2github.support;

import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import javax.net.ssl.SSLContext;

import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.client5.http.ssl.SSLConnectionSocketFactoryBuilder;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.io.SocketConfig;
import org.apache.hc.core5.http.ssl.TLS;
import org.apache.hc.core5.pool.PoolConcurrencyPolicy;
import org.apache.hc.core5.pool.PoolReusePolicy;
import org.apache.hc.core5.ssl.SSLContexts;
import org.apache.hc.core5.util.TimeValue;
import org.apache.hc.core5.util.Timeout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Clients {

    public static final Logger LOG = LoggerFactory.getLogger(Clients.class);

    public static HttpClientBuilder prepareClientBuilder() throws KeyManagementException, NoSuchAlgorithmException {
        SSLContext sslcontext = SSLContexts.custom()
//                .loadTrustMaterial(keystoreLocationURL, keystorePassword.toCharArray(), new TrustSelfSignedStrategy())
//                .loadKeyMaterial(keystoreLocationURL, keystorePassword.toCharArray(), keyManagerPassword.toCharArray())
                .build();

        PoolingHttpClientConnectionManager connectionManager = PoolingHttpClientConnectionManagerBuilder.create()
                .setSSLSocketFactory(SSLConnectionSocketFactoryBuilder.create()
                        .setSslContext(sslcontext)
                        .setTlsVersions(TLS.V_1_2)
                        .build())
                .setDefaultSocketConfig(SocketConfig.custom()
                        .setSoTimeout(Timeout.ofSeconds(5))
                        .build())
                .setPoolConcurrencyPolicy(PoolConcurrencyPolicy.STRICT)
                .setConnPoolPolicy(PoolReusePolicy.LIFO)
                .setConnectionTimeToLive(TimeValue.ofMinutes(1L))
                .setMaxConnTotal(1)
                .setMaxConnPerRoute(1)
                .build();

        HttpClientBuilder clientBuilder = HttpClients.custom();
        clientBuilder.setConnectionManager(connectionManager);

        RequestConfig requestConfig = RequestConfig.custom()
                .setResponseTimeout(Timeout.ofSeconds(120))
                .setConnectTimeout(Timeout.ofSeconds(10))
                .setConnectionRequestTimeout(Timeout.ofSeconds(120))
                .build();

        clientBuilder.setDefaultRequestConfig(requestConfig);

        return clientBuilder;
    }

    public static void logResponse(CloseableHttpResponse response, ResultWrapper resultWrapper, ClassicHttpRequest request) throws Exception {
        StringBuilder sb = new StringBuilder();
        sb.append(response.getVersion()).append(" ")
                .append(response.getCode()).append(" ")
                .append(response.getReasonPhrase())
                .append("\r\n");
        for (Header header : response.getHeaders()) {
            sb.append(header.getName()).append(": ").append(header.getValue()).append("\r\n");
        }
        sb.append("\r\n");
        LOG.info("---------------- Response headers received from '{}' ----------------\n" +
                        "---------------- START Response-Headers ----------------\n" +
                        "{}\n" +
                        "---------------- END Response-Headers ----------------",
                request.getUri(), sb);
        if (resultWrapper.contentType != null && !resultWrapper.content.trim().isEmpty()) {
            LOG.info("---------------- Response content received from '{}' ----------------\n" +
                            "---------------- START Response-Body ----------------\n" +
                            "{}\n" +
                            "---------------- END Response-Body ----------------",
                    request.getUri(), resultWrapper.content);
        }
    }

}
