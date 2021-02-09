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
import java.util.Properties;

import org.kohsuke.github.GHIssueState;
import org.kohsuke.github.GHMilestone;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GitHub;
import org.kohsuke.github.GitHubBuilder;
import org.kohsuke.github.PagedIterable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * After Jira versions were copied to Github milestones, we need numbers of the milestones, so issues can
 * be properly assigned to the milestones.
 */
public class GithubMilestoneExporter {

    public static final Logger LOG = LoggerFactory.getLogger(GithubMilestoneExporter.class);

    public static void main(String[] args) throws Exception {
        Properties props = new Properties();
        try (FileReader fr = new FileReader("etc/application.properties")) {
            props.load(fr);
        }

        GitHub github = new GitHubBuilder().withOAuthToken(props.getProperty("github.token"), props.getProperty("github.organization")).build();
        System.out.println(github.getMyself().getEmail());

        String project = props.getProperty("jira.project");
        GHRepository repo = github.getRepository(props.getProperty("github.organization") + "/" + props.getProperty("github.repository"));

        PagedIterable<GHMilestone> ms = repo.listMilestones(GHIssueState.ALL);
        for (GHMilestone m : ms) {
            System.out.printf("%s.%s = %d%n", project, m.getTitle(), m.getNumber());
        }
    }

}
