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

import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GitHub;
import org.kohsuke.github.GitHubBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GithubLabelCreator {

    public static final Logger LOG = LoggerFactory.getLogger(GithubLabelCreator.class);

    public static void main(String[] args) throws Exception {
        Properties props = new Properties();
        try (FileReader fr = new FileReader("etc/application.properties")) {
            props.load(fr);
        }

        GitHub github = new GitHubBuilder().withOAuthToken(props.getProperty("github.token"), props.getProperty("github.organization")).build();
        System.out.println(github.getMyself().getEmail());

        String project = props.getProperty("jira.project");
        GHRepository repo = github.getRepository(props.getProperty("github.organization") + "/" + props.getProperty("github.repository"));

        Properties types = new Properties();
        Properties resolutions = new Properties();
        Properties components = new Properties();
        try (FileReader fr = new FileReader("etc/types.properties")) {
            types.load(fr);
        }
        try (FileReader fr = new FileReader("etc/resolutions.properties")) {
            resolutions.load(fr);
        }
        try (FileReader fr = new FileReader("etc/components.properties")) {
            components.load(fr);
        }

        for (Object o : types.keySet()) {
            String key = (String) o;
            if (key.endsWith(".label")) {
                continue;
            }
            String dkey = key;
            String lkey = key + ".label";
            String description = types.getProperty(dkey);
            String label = types.getProperty(lkey);

            LOG.info("Creating label \"{}\" / {}", label, description);
//            // color from default colors - light blue normal
            repo.createLabel(label, "1D76DB", description);
        }

        for (Object o : resolutions.keySet()) {
            String key = (String) o;
            if (key.endsWith(".label")) {
                continue;
            }
            String dkey = key;
            String lkey = key + ".label";
            String description = resolutions.getProperty(dkey);
            String label = resolutions.getProperty(lkey);

            LOG.info("Creating label \"{}\" / {}", label, description);
//            // color from default colors - green normal
            repo.createLabel(label, "0E8A16", description);
        }

        for (Object o : components.keySet()) {
            String key = (String) o;
            if (key.endsWith(".label") || !key.startsWith(project)) {
                continue;
            }
            String dkey = key;
            String lkey = key + ".label";
            String description = components.getProperty(dkey);
            String label = components.getProperty(lkey);

            LOG.info("Creating label \"{}\" / {}", label, description);
//            // color from default colors - yellow normal
            repo.createLabel(label, "FBCA04", description);
        }
    }

}
