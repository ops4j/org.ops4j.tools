/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.ops4j.tools.jira2github.client;

import java.io.ByteArrayOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;

import com.fasterxml.jackson.core.TreeNode;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.IntNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.io.entity.ByteArrayEntity;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.support.ClassicRequestBuilder;
import org.ops4j.tools.jira2github.support.Clients;
import org.ops4j.tools.jira2github.support.ResultWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This toos provides "last famous comments" for these Jira issues that were migrated to Github
 */
public class JiraCommentCreator {

    // https://developer.atlassian.com/cloud/jira/platform/rest/v3/intro/

    public static final Logger LOG = LoggerFactory.getLogger(JiraCommentCreator.class);

    public static void main(String[] args) throws Exception {
        Properties props = new Properties();
        try (FileReader fr = new FileReader("etc/application.properties")) {
            props.load(fr);
        }
        Properties issues = new Properties();
        try (FileReader fr = new FileReader("etc/issues.properties")) {
            issues.load(fr);
        }

        String ghOrg = props.getProperty("github.organization");
        String ghProject = props.getProperty("github.repository");
        String jiraProject = props.getProperty("jira.project");

        HttpClientBuilder clientBuilder = Clients.prepareClientBuilder();

        Map<String, Integer> result = new LinkedHashMap<>();

        try (CloseableHttpClient client = clientBuilder.build()) {
            for (String key : issues.stringPropertyNames()) {
                if (!key.startsWith(jiraProject) || key.endsWith(".summary")) {
                    continue;
                }

                ClassicRequestBuilder rb = ClassicRequestBuilder.post(String.format("https://ops4j1.jira.com/rest/api/3/issue/%s/comment", key));
                rb.addHeader("Accept", "application/json");
                rb.addHeader("Content-Type", "application/json");
                rb.addHeader("Authorization", "Basic " + Base64.getEncoder()
                        .encodeToString(String.format("%s:%s", props.getProperty("jira.user"), props.getProperty("jira.token")).getBytes(StandardCharsets.UTF_8)));
                ClassicHttpRequest post = rb.build();

                ObjectMapper mapper = new ObjectMapper();
                JsonNodeFactory nf = mapper.getNodeFactory();

                Map<String, JsonNode> data = new LinkedHashMap<>();
                Map<String, JsonNode> body = new LinkedHashMap<>();
                data.put("body", new ObjectNode(nf, body));
                body.put("type", new TextNode("doc"));
                body.put("version", new IntNode(1));
                Map<String, JsonNode> content = new LinkedHashMap<>();
                body.put("content", new ArrayNode(nf, Collections.singletonList(new ObjectNode(nf, content))));
                content.put("type", new TextNode("paragraph"));
                ArrayNode paragraphs = new ArrayNode(nf);
                content.put("content", paragraphs);

                Map<String, JsonNode> c1 = new LinkedHashMap<>();
                paragraphs.add(new ObjectNode(nf, c1));
                c1.put("type", new TextNode("text"));
                c1.put("text", new TextNode("The project is now managed using "));
                Map<String, JsonNode> c2 = new LinkedHashMap<>();
                paragraphs.add(new ObjectNode(nf, c2));
                c2.put("type", new TextNode("text"));
                c2.put("text", new TextNode("Github Issues"));
                Map<String, JsonNode> l2 = new LinkedHashMap<>();
                c2.put("marks", new ArrayNode(nf, Collections.singletonList(new ObjectNode(nf, l2))));
                l2.put("type", new TextNode("link"));
                Map<String, JsonNode> a2 = new LinkedHashMap<>();
                a2.put("href", new TextNode(String.format("https://github.com/ops4j/%s/issues", ghProject)));
                l2.put("attrs", new ObjectNode(nf, a2));
                Map<String, JsonNode> c3 = new LinkedHashMap<>();
                paragraphs.add(new ObjectNode(nf, c3));
                c3.put("type", new TextNode("text"));
                c3.put("text", new TextNode(". This issue can be viewed at "));
                Map<String, JsonNode> c4 = new LinkedHashMap<>();
                paragraphs.add(new ObjectNode(nf, c4));
                c4.put("type", new TextNode("inlineCard"));
                Map<String, JsonNode> a4 = new LinkedHashMap<>();
                a4.put("url", new TextNode(String.format("https://github.com/ops4j/%s/issues/%s", ghProject, issues.getProperty(key))));
                c4.put("attrs", new ObjectNode(nf, a4));
                Map<String, JsonNode> c5 = new LinkedHashMap<>();
                paragraphs.add(new ObjectNode(nf, c5));
                c5.put("type", new TextNode("text"));
                c5.put("text", new TextNode("."));

                System.out.println("\n=== Creating comment for " + key);
                mapper.createGenerator(System.out).setPrettyPrinter(new DefaultPrettyPrinter())
                        .writeTree(new ObjectNode(nf, data));
                ByteArrayOutputStream postData = new ByteArrayOutputStream();
                mapper.createGenerator(postData).writeTree(new ObjectNode(nf, data));

                byte[] bytes = postData.toByteArray();
                post.setEntity(new ByteArrayEntity(bytes, 0, bytes.length, ContentType.APPLICATION_JSON));

                final ResultWrapper resultWrapper = new ResultWrapper();
                CloseableHttpResponse response = client.execute(post);

                result.put(key, response.getCode());

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

                Clients.logResponse(response, resultWrapper, post);
                TreeNode tree = mapper.createParser(resultWrapper.content).readValueAsTree();
                mapper.createGenerator(System.out).setPrettyPrinter(new DefaultPrettyPrinter()).writeTree(tree);
            }
        } catch (IOException e) {
            LOG.error(e.getMessage(), e);
        }

        System.out.println();
        final int[] counts = new int[] { 0, 0 };
        result.forEach((key, code) -> {
            if (code < 400) {
                counts[0]++;
                LOG.info("{}: HTTP {}", key, code);
            }
        });
        result.forEach((key, code) -> {
            if (code >= 400) {
                LOG.warn("{}: HTTP {}", key, code);
                counts[1]++;
            }
        });

        LOG.info("Commented {} issues, failed commenting {} issues", counts[0], counts[1]);
    }

}
