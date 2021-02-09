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
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.kohsuke.github.GHIssue;
import org.kohsuke.github.GHIssueState;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GitHub;
import org.kohsuke.github.GitHubBuilder;
import org.kohsuke.github.PagedIterable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * After Jira issues were imported into Github, we have to do 2nd pass:<ul>
 *     <li>add final comment to relevant Jira issue</li>
 *     <li>establish issue links between issues at Github</li>
 * </ul>
 * This tool produces mapping of Jira issue keys to Github issue ids - to produce versions.properties used later.
 */
public class GithubIssueExporter {

    public static final Logger LOG = LoggerFactory.getLogger(GithubIssueExporter.class);

    public static void main(String[] args) throws Exception {
        Properties props = new Properties();
        try (FileReader fr = new FileReader("etc/application.properties")) {
            props.load(fr);
        }

        GitHub github = new GitHubBuilder().withOAuthToken(props.getProperty("github.token"), props.getProperty("github.organization")).build();
        System.out.println(github.getMyself().getEmail());

        String project = props.getProperty("jira.project");
        GHRepository repo = github.getRepository(props.getProperty("github.organization") + "/" + props.getProperty("github.repository"));

        Pattern title = Pattern.compile("^.*\\[(" + project + "-[0-9]+)]");

        PagedIterable<GHIssue> issues = repo.listIssues(GHIssueState.ALL);
        Map<String, Integer> jira2gh = new TreeMap<>(new JiraKeyComparator());
        Map<String, String> descriptions = new TreeMap<>(new JiraKeyComparator());

        for (GHIssue i : issues) {
            if (i.getPullRequest() == null) {
                // it's a normal issue
                Matcher m = title.matcher(i.getTitle());
                if (m.matches()) {
                    // it contains Jira reference in the summary
                    String id = m.group(1);
                    jira2gh.put(id, i.getNumber());
                    descriptions.put(id, i.getTitle());
                }
            }
        }
        jira2gh.forEach((key, nr) -> {
            System.out.printf("%s = %d%n", key, nr);
            System.out.printf("%s.summary = %s%n", key, descriptions.get(key));
        });
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
