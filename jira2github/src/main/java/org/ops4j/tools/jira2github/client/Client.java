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
package org.ops4j.tools.jira2github.client;

import java.io.FileReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import javax.net.ssl.SSLContext;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.Unmarshaller;
import javax.xml.transform.stream.StreamSource;

import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.client5.http.ssl.SSLConnectionSocketFactoryBuilder;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.io.SocketConfig;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.support.ClassicRequestBuilder;
import org.apache.hc.core5.http.ssl.TLS;
import org.apache.hc.core5.pool.PoolConcurrencyPolicy;
import org.apache.hc.core5.pool.PoolReusePolicy;
import org.apache.hc.core5.ssl.SSLContexts;
import org.apache.hc.core5.util.TimeValue;
import org.apache.hc.core5.util.Timeout;
import org.ops4j.tools.jira2github.model.Item;
import org.ops4j.tools.jira2github.model.Rss;
import org.ops4j.tools.jira2github.support.DateAdapter;
import org.ops4j.tools.jira2github.support.HtmlToMd;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Client {

    // see https://spring.io/blog/2019/01/15/spring-framework-s-migration-from-jira-to-github-issues
    // see https://gist.github.com/jonmagic/5282384165e0f86ef105

    public static final Logger LOG = LoggerFactory.getLogger(Client.class);

    private static Properties users = new Properties();

    public static void main(String[] args) throws Exception {
        LOG.info("Starting");

        Properties props = new Properties();
        try (FileReader fr = new FileReader("etc/application.properties")) {
            props.load(fr);
        }
        try (FileReader fr = new FileReader("etc/users.properties")) {
            users.load(fr);
        }
        String org = props.getProperty("github.organization");
        String repo = props.getProperty("github.repository");
        String token = props.getProperty("github.token");

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

        JAXBContext jaxb = JAXBContext.newInstance(Rss.class.getPackage().getName());
        Unmarshaller u = jaxb.createUnmarshaller();

        try (CloseableHttpClient client = clientBuilder.build()) {
            try (FileReader reader = new FileReader("data/pax-transx-20210129.xml")) {
                Rss rss = u.unmarshal(new StreamSource(reader), Rss.class).getValue();

                for (Item item : rss.channel.items) {
                    send(item, client, org, repo, token);
                }
            }
        } catch (IOException e) {
            LOG.error(e.getMessage(), e);
        }
    }

    private static void send(Item item, CloseableHttpClient client, String org, String repo, String token) throws Exception {
        ClassicRequestBuilder pb = ClassicRequestBuilder.post(String.format("https://api.github.com/repos/%s/%s/import/issues", org, repo));
        pb.addHeader("Accept", "application/vnd.github.golden-comet-preview+json");
        pb.addHeader("Authorization", "token " + token);

        StringBuilder entity = new StringBuilder();
        entity.append("{\n  \"issue\": {\n");
        entity.append("    \"title\": \"").append(q(item.summary)).append(" [").append(item.key.value).append("]\",\n");
        entity.append("    \"body\": \"").append(q(body(item))).append("\",\n");
        entity.append("    \"created_at\": \"").append(DateAdapter.GH_FORMAT.format(item.created)).append("\"");
        if (item.status.value != null) {
            if (item.status.value.equalsIgnoreCase("closed") || item.status.value.equalsIgnoreCase("resolved")) {
                entity.append(",\n");
                entity.append("    \"closed_at\": \"").append(DateAdapter.GH_FORMAT.format(item.resolved)).append("\",\n");
                entity.append("    \"closed\": true");
            }
        }
        if (item.labels != null && item.labels.size() > 0) {
            entity.append(",\n");
            entity.append("    \"labels\": [");
            int c = 0;
            for (String label : item.labels) {
                if (c > 0) {
                    entity.append(",");
                }
                entity.append("      \"").append(label).append("\"");
                c++;
            }
            entity.append("]");
        }
        entity.append("\n  }");
        if (item.comments != null && item.comments.size() > 0) {
            entity.append(",\n  \"comments\":[\n");
            int c = 0;
            for (Item.Comment comment : item.comments) {
                if (c > 0) {
                    entity.append(",\n");
                }
                entity.append("    {\n");
                entity.append("      \"created_at\": \"").append(DateAdapter.GH_FORMAT.format(comment.created)).append("\",\n");
                entity.append("      \"body\": \"").append(q(body(comment))).append("\"\n");
                entity.append("    }");
                c++;
            }
            entity.append("\n  ]");
        }
        entity.append("\n}");

//        System.out.printf("======= Issue %s =======%n", item.key.value);
//        System.out.printf(" - %s%n", item.summary);
//        System.out.println(HtmlToMd.markdown(item.htmlDescription));
//
//        for (Item.Comment comment : item.comments) {
//            System.out.printf("======= Comment %s =======%n", comment.id);
//            System.out.println(HtmlToMd.markdown(comment.html));
//        }

        pb.setEntity(entity.toString().getBytes(StandardCharsets.UTF_8), ContentType.APPLICATION_JSON);
        System.out.println(entity);

        createIssue(client, pb);
    }

    private static void createIssue(CloseableHttpClient client, ClassicRequestBuilder pb) throws Exception {
        ClassicHttpRequest post = pb.build();

        final ResultWrapper resultWrapper = new ResultWrapper();
        CloseableHttpResponse response = client.execute(post);

        if (response.getEntity() != null) {
            resultWrapper.content = EntityUtils.toString(response.getEntity());
            String ct = response.getEntity().getContentType();
            if (ct != null) {
                resultWrapper.contentType = ContentType.parse(ct).getMimeType();
            }
        }
        resultWrapper.httpStatus = response.getCode();
        resultWrapper.headers = new LinkedHashMap<>();
        Arrays.stream(response.getHeaders())
                .forEach(h -> resultWrapper.headers.put(h.getName(), h.getValue()));

        logResponse(response, resultWrapper, post);
    }

    private static String q(String v) {
        return v.replaceAll("\"", "\\\\\"").replaceAll("\n", "\\\\n");
    }

    private static String body(Item issue) {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("**[%s](%s)** created **[%s](%s)** %n%n",
                users.get(issue.reporter.accountid), "https://ops4j1.jira.com/secure/ViewProfile.jspa?accountId=" + issue.reporter.accountid,
                issue.key.value, "https://ops4j1.jira.com/browse/" + issue.key.value
        ));
        sb.append(HtmlToMd.markdown(issue.htmlDescription));

        sb.append("\n\n---\n\n");

        if (issue.version.size() > 0) {
            sb.append(String.format("**Affects:** %s%n", String.join(", ", issue.version)));
        }
        if (issue.fixVersion.size() > 0) {
            sb.append(String.format("**Fixed in:** %s%n", String.join(", ", issue.fixVersion)));
        }

        if (issue.attachments.size() > 0) {
            sb.append("**Attachments:**\n");
            for (Item.Attachment a : issue.attachments) {
                sb.append(String.format("* [%s](%s)\n", a.name, "https://ops4j1.jira.com/secure/attachment/" + a.id + "/" + a.name));
            }
            sb.append("\n");
        }

        // TODO: issue references

        sb.append(String.format("Votes: %d, Watches: %d%n", issue.votes, issue.watches));

        return sb.toString();
    }

    private static String body(Item.Comment comment) {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("**[%s](%s)** commented%n%n",
                users.get(comment.author), "https://ops4j1.jira.com/secure/ViewProfile.jspa?accountId=" + comment.author
        ));
        sb.append(HtmlToMd.markdown(comment.html));
        return sb.toString();
    }

    private static void logResponse(CloseableHttpResponse response, ResultWrapper resultWrapper, ClassicHttpRequest request) throws Exception {
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

    private static final class ResultWrapper {

        private int httpStatus;
        private String content;
        private String contentType;
        private Map<String, String> headers;
    }

}
