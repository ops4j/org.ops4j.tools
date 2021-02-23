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

import java.io.FileReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Properties;

import javax.xml.transform.stream.StreamSource;

import com.fasterxml.jackson.core.PrettyPrinter;
import com.fasterxml.jackson.core.TreeNode;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.support.ClassicRequestBuilder;
import org.kohsuke.github.GHMilestone;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GitHub;
import org.kohsuke.github.GitHubBuilder;
import org.ops4j.tools.jira2github.model.Item;
import org.ops4j.tools.jira2github.model.Rss;
import org.ops4j.tools.jira2github.support.Clients;
import org.ops4j.tools.jira2github.support.DateAdapter;
import org.ops4j.tools.jira2github.support.ResultWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class copies Jira versions to Github milestones
 */
public class JiraVersionExporter {

    // https://developer.atlassian.com/cloud/jira/platform/rest/v3/intro/

    public static final Logger LOG = LoggerFactory.getLogger(JiraVersionExporter.class);

    public static void main(String[] args) throws Exception {
        Properties props = new Properties();
        try (FileReader fr = new FileReader("etc/application.properties")) {
            props.load(fr);
        }
        Properties versions = new Properties();
        try (FileReader fr = new FileReader("etc/versions.properties")) {
            versions.load(fr);
        }

        String jproject = props.getProperty("jira.project");
        String ghproject = props.getProperty("github.repository");

        HttpClientBuilder clientBuilder = Clients.prepareClientBuilder();

        try (CloseableHttpClient client = clientBuilder.build()) {
            ClassicRequestBuilder pb = ClassicRequestBuilder.get(String.format("https://ops4j1.jira.com/rest/api/3/project/%s/versions", jproject));
            pb.addHeader("Accept", "application/json");
            pb.addHeader("Authorization", "Basic " + Base64.getEncoder()
                    .encodeToString(String.format("%s:%s", props.getProperty("jira.user"), props.getProperty("jira.token")).getBytes(StandardCharsets.UTF_8)));
            ClassicHttpRequest get = pb.build();

            final ResultWrapper resultWrapper = new ResultWrapper();
            CloseableHttpResponse response = client.execute(get);

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

            Clients.logResponse(response, resultWrapper, get);

            ObjectMapper mapper = new ObjectMapper();
            TreeNode tree = mapper.createParser(resultWrapper.content).readValueAsTree();
            mapper.createGenerator(System.out).setPrettyPrinter(new DefaultPrettyPrinter()).writeTree(tree);

            createVersion(props, versions, ghproject, tree);
        } catch (IOException e) {
            LOG.error(e.getMessage(), e);
        }
    }

    private static void createVersion(Properties props, Properties versions, String ghproject, TreeNode tree) throws IOException {
        GitHub github = new GitHubBuilder().withOAuthToken(props.getProperty("github.token"),
                props.getProperty("github.organization")).build();
        GHRepository repo = github.getRepository(props.getProperty("github.organization") + "/" + ghproject);

        ((ArrayNode)tree).forEach(el -> {
            try {
                String desc = el.get("description") == null ? null : el.get("description").asText();
                Date d = el.get("releaseDate") == null ? null : DateAdapter.YMD_FORMAT.parse(el.get("releaseDate").asText());
                String jiraVersion = el.get("name").asText();
                if (versions.containsKey(props.getProperty("jira.project") + "." + jiraVersion)) {
                    // it's already created
                    LOG.info("milestone {} (due date: {}) is already created: {}", jiraVersion, d, versions.get(props.getProperty("jira.project") + "." + jiraVersion));
                } else {
                    LOG.info("Creating milestone {} (due date: {})", jiraVersion, d);
                    GHMilestone ms = repo.createMilestone(jiraVersion, desc);
                    if (d != null) {
                        ms.setDueOn(d);
                    }
                    if (el.get("released") != null && el.get("released").asBoolean()) {
                        ms.close();
                    }
                }
            } catch (Exception e) {
                LOG.error(e.getMessage(), e);
            }
        });
    }

}
