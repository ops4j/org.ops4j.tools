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
package org.ops4j.tools.jira2github;

import java.io.FileReader;
import java.io.IOException;
import java.util.List;
import java.util.Properties;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.kohsuke.github.GHIssue;
import org.kohsuke.github.GHIssueState;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GitHub;
import org.kohsuke.github.GitHubBuilder;

public class ConnectTest {

    private static Properties props;

    @BeforeAll
    public static void init() throws Exception {
        props = new Properties();
        try (FileReader fr = new FileReader("etc/application.properties")) {
            props.load(fr);
        }
    }

    @Test
    public void connect() throws Exception {
        GitHub github = new GitHubBuilder().withOAuthToken(props.getProperty("github.token"), props.getProperty("github.organization")).build();
        System.out.println(github.getMyself().getEmail());

        GHRepository repo = github.getRepository("ops4j/org.ops4j.pax.logging");
        repo.createIssue("Automatic issue 1").body("<b>asd</b>").create();
    }

    @Test
    public void issues() throws Exception {
        GitHub github = new GitHubBuilder().withOAuthToken(props.getProperty("github.token"), props.getProperty("github.organization")).build();
        GHRepository repo = github.getRepository(props.getProperty("github.organization") + "/test2github");

        List<GHIssue> issues = repo.getIssues(GHIssueState.ALL);
        issues.forEach(i -> {
            System.out.println(i.getId() + " : " + i.getBody());
            try {
                i.comment("comment 1 for " + i.getId());
                i.comment("comment 2 for " + i.getId());
            } catch (IOException e) {
                throw new RuntimeException();
            }
        });
    }

}
