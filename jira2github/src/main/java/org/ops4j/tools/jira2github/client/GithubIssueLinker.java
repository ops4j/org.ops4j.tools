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
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.Unmarshaller;
import javax.xml.transform.stream.StreamSource;

import org.kohsuke.github.GHIssue;
import org.kohsuke.github.GHIssueState;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GitHub;
import org.kohsuke.github.GitHubBuilder;
import org.kohsuke.github.PagedIterable;
import org.ops4j.tools.jira2github.model.Item;
import org.ops4j.tools.jira2github.model.Rss;
import org.ops4j.tools.jira2github.support.HtmlToMd;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * After Jira issues were imported into Github, we have to do 2nd pass:<ul>
 *     <li>add final comment to relevant Jira issue</li>
 *     <li>establish issue links between issues at Github</li>
 * </ul>
 * This tool produces mapping of Jira issue keys to Github issue ids - to produce versions.properties used later.
 */
public class GithubIssueLinker {

    public static final Logger LOG = LoggerFactory.getLogger(GithubIssueLinker.class);

    public static void main(String[] args) throws Exception {
        Properties props = new Properties();
        try (FileReader fr = new FileReader("etc/application.properties")) {
            props.load(fr);
        }
        Properties jiraIssues = new Properties();
        try (FileReader fr = new FileReader("etc/issues.properties")) {
            jiraIssues.load(fr);
        }
        Properties links = new Properties();
        try (FileReader fr = new FileReader("etc/links.properties")) {
            links.load(fr);
        }
        Properties projects = new Properties();
        try (FileReader fr = new FileReader("etc/projects.properties")) {
            projects.load(fr);
        }

        GitHub github = new GitHubBuilder().withOAuthToken(props.getProperty("github.token"), props.getProperty("github.organization")).build();

        String project = props.getProperty("jira.project");
        String exportDate = props.getProperty("jira.export.date");

        GHRepository repo = github.getRepository(props.getProperty("github.organization") + "/" + props.getProperty("github.repository"));

        Pattern title = Pattern.compile("^.*\\[(" + project + "-[0-9]+)]");

        PagedIterable<GHIssue> issues = repo.listIssues(GHIssueState.ALL);
        Map<String, Integer> jira2gh = new TreeMap<>(new JiraKeyComparator());

        JAXBContext jaxb = JAXBContext.newInstance(Rss.class.getPackage().getName());
        Unmarshaller u = jaxb.createUnmarshaller();

        Map<Integer, String> linksMarkdownSections = new HashMap<>();

        try (FileReader reader = new FileReader("data/" + project + "-" + exportDate + ".xml")) {
            Rss rss = u.unmarshal(new StreamSource(reader), Rss.class).getValue();
            rss.sort();

            for (Item item : rss.channel.items) {
                if (!jiraIssues.containsKey(item.key.value)) {
                    continue;
                }
                String linksMd = HtmlToMd.markdownForLinks(project, item, links, jiraIssues, projects);
                if (linksMd != null) {
                    linksMarkdownSections.put(Integer.parseInt(jiraIssues.getProperty(item.key.value)), linksMd);
                }
            }
        }

        if (linksMarkdownSections.isEmpty()) {
            return;
        }

        for (GHIssue i : issues) {
            if (i.getPullRequest() == null && linksMarkdownSections.containsKey(i.getNumber())) {
                // it's a normal issue
                Matcher m = title.matcher(i.getTitle());
                if (m.matches()) {
                    // it contains Jira reference in the summary
                    LOG.info("Updating body of {}", i.getHtmlUrl());
                    LOG.debug(linksMarkdownSections.get(i.getNumber()));
                    i.setBody(i.getBody() + linksMarkdownSections.get(i.getNumber()));
                    Thread.sleep(200);
                }
            }
        }
    }

    public static class JiraKeyComparator implements Comparator<String> {
        @Override
        public int compare(String s1, String s2) {
            int k1 = Integer.parseInt(s1.substring(s1.indexOf('-') + 1));
            int k2 = Integer.parseInt(s2.substring(s2.indexOf('-') + 1));
            return k1 - k2;
        }
    }

}
