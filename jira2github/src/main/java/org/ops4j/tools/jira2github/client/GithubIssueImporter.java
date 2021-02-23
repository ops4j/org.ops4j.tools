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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.Unmarshaller;
import javax.xml.transform.stream.StreamSource;

import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.support.ClassicRequestBuilder;
import org.ops4j.tools.jira2github.model.Item;
import org.ops4j.tools.jira2github.model.Rss;
import org.ops4j.tools.jira2github.support.Clients;
import org.ops4j.tools.jira2github.support.DateAdapter;
import org.ops4j.tools.jira2github.support.HtmlToMd;
import org.ops4j.tools.jira2github.support.ResultWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GithubIssueImporter {

    // see https://spring.io/blog/2019/01/15/spring-framework-s-migration-from-jira-to-github-issues
    // see https://gist.github.com/jonmagic/5282384165e0f86ef105

    public static final Logger LOG = LoggerFactory.getLogger(GithubIssueImporter.class);

    private static final Properties users = new Properties();
    private static final Properties types = new Properties();
    private static final Properties issues = new Properties();
    private static final Properties resolutions = new Properties();
    private static final Properties components = new Properties();
    private static final Properties versions = new Properties();
    private static final Map<String, Map<String, String>> projectsComponents = new HashMap<>();

    private static String JIRA_PROJECT;

    public static void main(String[] args) throws Exception {
        LOG.info("Starting");

        Properties props = new Properties();
        try (FileReader fr = new FileReader("etc/application.properties")) {
            props.load(fr);
        }
        try (FileReader fr = new FileReader("etc/users.properties")) {
            users.load(fr);
        }
        try (FileReader fr = new FileReader("etc/types.properties")) {
            types.load(fr);
        }
        try (FileReader fr = new FileReader("etc/issues.properties")) {
            issues.load(fr);
        }
        try (FileReader fr = new FileReader("etc/resolutions.properties")) {
            resolutions.load(fr);
        }
        try (FileReader fr = new FileReader("etc/components.properties")) {
            components.load(fr);
        }
        try (FileReader fr = new FileReader("etc/versions.properties")) {
            versions.load(fr);
        }
        for (Object o : components.keySet()) {
            String key = (String) o;
            if (key.endsWith(".label")) {
                continue;
            }
            Map<String, String> comps = projectsComponents.computeIfAbsent(key.split("\\.")[0], k -> new HashMap<>());
            String jiraComponent = components.getProperty(key);
            String githubLabel = components.getProperty(key + ".label");
            comps.put(jiraComponent, githubLabel);
        }

        String org = props.getProperty("github.organization");
        String repo = props.getProperty("github.repository");
        String token = props.getProperty("github.token");
        JIRA_PROJECT = props.getProperty("jira.project");
        String exportDate = props.getProperty("jira.export.date");

        JAXBContext jaxb = JAXBContext.newInstance(Rss.class.getPackage().getName());
        Unmarshaller u = jaxb.createUnmarshaller();

        HttpClientBuilder clientBuilder = Clients.prepareClientBuilder();
        Map<String, Integer> result = new LinkedHashMap<>();

        try (CloseableHttpClient client = clientBuilder.build()) {
            try (FileReader reader = new FileReader("data/" + JIRA_PROJECT + "-" + exportDate + ".xml")) {
                Rss rss = u.unmarshal(new StreamSource(reader), Rss.class).getValue();
                rss.sort();

                for (Item item : rss.channel.items) {
                    if (issues.containsKey(item.key.value)) {
                        continue;
                    }
                    LOG.info("Importing {}", item.key.value);
                    send(item, client, org, repo, token, result);
                }
            }
        } catch (IOException e) {
            LOG.error(e.getMessage(), e);
        }

        System.out.println();
        result.forEach((key, code) -> {
            if (code < 400) {
                LOG.info("{}: HTTP {}", key, code);
            }
        });
        result.forEach((key, code) -> {
            if (code >= 400) {
                LOG.warn("{}: HTTP {}", key, code);
            }
        });
    }

    private static void send(Item item, CloseableHttpClient client, String org, String repo, String token, Map<String, Integer> result) throws Exception {
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
        if (item.fixVersions != null && item.fixVersions.size() > 0) {
            // if one there's no problem, of more, we have to select the latest and mark the issue as backported
                entity.append(",\n");
            if (item.fixVersions.size() == 1) {
                String version = item.fixVersions.get(0).trim();
                String v = versions.getProperty(JIRA_PROJECT + "." + version);
                if (v == null) {
                    throw new IllegalStateException("Can't find version number for \"" + version + "\"");
                }
                entity.append("    \"milestone\": ").append(v);
            } else {
                List<String> vs = new ArrayList<>(item.fixVersions);
                vs.sort(new VersionComparator());
                String version = vs.get(vs.size() - 1).trim();
                String v = versions.getProperty(JIRA_PROJECT + "." + version);
                if (v == null) {
                    throw new IllegalStateException("Can't find version number for \"" + version + "\"");
                }
                entity.append("    \"milestone\": ").append(v);
                item.labels.add("status: backported");
            }
        }
        if ((item.components != null && item.components.size() > 0)
                || (item.labels != null && item.labels.size() > 0)
                || (item.type != null && item.type.id != -1)
                || (item.resolution != null && item.resolution.id != -1 && item.resolution.id != 1 && item.resolution.id != 6)
        ) {
            entity.append(",\n");
            entity.append("    \"labels\": [");

            int c = 0;
            if (item.type != null && item.type.id != -1) {
                entity.append("\"").append(types.get("t." + item.type.id + ".label")).append("\"");
                c++;
            }
            if (item.resolution != null && item.resolution.id != -1 && item.resolution.id != 1 && item.resolution.id != 6) {
                if (c > 0) {
                    entity.append(",");
                }
                entity.append("\"").append(resolutions.get("r." + item.resolution.id + ".label")).append("\"");
                c++;
            }
            if (item.components != null && item.components.size() > 0) {
                for (String label : item.components) {
                    if (c > 0) {
                        entity.append(",");
                    }
                    // component to label
                    entity.append("\"").append(projectsComponents.get(JIRA_PROJECT).get(label)).append("\"");
                    c++;
                }
            }
            if (item.labels != null && item.labels.size() > 0) {
                for (String label : item.labels) {
                    if (c > 0) {
                        entity.append(",");
                    }
                    entity.append("\"").append(label).append("\"");
                    c++;
                }
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

        pb.setEntity(entity.toString().getBytes(StandardCharsets.UTF_8), ContentType.APPLICATION_JSON);
        LOG.info(entity.toString());

        createIssue(client, pb, item.key.value, result);
        Thread.sleep(200);
    }

    private static void createIssue(CloseableHttpClient client, ClassicRequestBuilder pb, String issueId, Map<String, Integer> result) throws Exception {
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
        result.put(issueId, resultWrapper.httpStatus);
        resultWrapper.headers = new LinkedHashMap<>();
        Arrays.stream(response.getHeaders())
                .forEach(h -> resultWrapper.headers.put(h.getName(), h.getValue()));

        Clients.logResponse(response, resultWrapper, post);
    }

    private static String q(String v) {
        return v.replaceAll("\\\\", "\\\\\\\\").replaceAll("\"", "\\\\\"").replaceAll("\n", "\\\\n").replaceAll("\t", "\\\\t");
    }

    private static String body(Item issue) {
        StringBuilder sb = new StringBuilder();
        String user = users.getProperty(issue.reporter.accountid);
        sb.append(String.format("**[%s](%s)** created **[%s](%s)** %n%n",
                user, "https://ops4j1.jira.com/secure/ViewProfile.jspa?accountId=" + issue.reporter.accountid,
                issue.key.value, "https://ops4j1.jira.com/browse/" + issue.key.value
        ));
        String desc = HtmlToMd.markdown(issue.htmlDescription);
        if (!desc.trim().equals("")) {
            sb.append(desc);
            sb.append("\n\n");
        }

        sb.append("---\n\n");

        if (issue.versions.size() > 0) {
            sb.append(String.format("**Affects:** %s%n", String.join(", ", issue.versions)));
        }
        if (issue.fixVersions.size() > 0) {
            sb.append(String.format("**Fixed in:** %s%n", String.join(", ", issue.fixVersions)));
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
        String user = users.getProperty(comment.author);
        sb.append(String.format("**[%s](%s)** commented%n%n",
                user, "https://ops4j1.jira.com/secure/ViewProfile.jspa?accountId=" + comment.author
        ));
        sb.append(HtmlToMd.markdown(comment.html));
        return sb.toString();
    }

    public static class VersionComparator implements Comparator<String> {
        @Override
        public int compare(String s1, String s2) {
            if (s1.equals(s2)) {
                return 0;
            }
            String[] t1 = s1.split("\\.");
            String[] t2 = s2.split("\\.");
            int l = t1.length;
            if (t1.length > t2.length) {
                l = t2.length;
            }
            for (int i = 0; i < l; i++) {
                int c = 0;
                try {
                    int v1 = Integer.parseInt(t1[i]);
                    int v2 = Integer.parseInt(t2[i]);
                    c = v1 - v2;
                } catch (NumberFormatException ignored) {
                    c = t1[i].compareTo(t2[i]);
                }
                if (c != 0) {
                    return c;
                }
            }
            // longer tab is "later" 2.0.0 is earlier than 2.0.0.1
            return t1.length - t2.length;
        }
    }

}
