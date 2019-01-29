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
package org.ops4j.tools.maven.osgireport;

import java.util.HashSet;
import java.util.Set;

import org.apache.maven.AbstractMavenLifecycleParticipant;
import org.apache.maven.MavenExecutionException;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.Plugin;
import org.apache.maven.model.PluginExecution;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.component.annotations.Component;
import org.codehaus.plexus.component.annotations.Requirement;
import org.slf4j.Logger;

@Component(role = AbstractMavenLifecycleParticipant.class)
public class ReactorSorter extends AbstractMavenLifecycleParticipant {

    @Requirement
    private Logger logger;

    @Override
    public void afterProjectsRead(MavenSession session) throws MavenExecutionException {
        // we have to:
        // 1. find a project that declared org.ops4j.tool.maven:osgi-report-maven-plugin plugin
        //    and manifest-summary goal execution
        // 2. add dependencies to all other projects within reactor except #1 and reactor's parent.

        MavenProject reportProject = null;

        for (MavenProject project : session.getProjects()) {
            Plugin plugin = project.getPlugin("org.ops4j.tools.maven:osgi-report-maven-plugin");
            if (plugin != null) {
                for (PluginExecution execution : plugin.getExecutions()) {
                    if (execution.getGoals().stream().anyMatch("manifest-summary"::equals)) {
                        reportProject = project;
                        break;
                    }
                }
                if (reportProject != null) {
                    break;
                }
            }
        }

        if (reportProject == null) {
            logger.warn("Can't find project with org.ops4j.tools.maven:osgi-report-maven-plugin configured");
            return;
        }

        Set<MavenProject> skipped = new HashSet<>();
        skipped.add(reportProject);
        MavenProject parent = reportProject.getParent();
        while (parent != null) {
            skipped.add(parent);
            parent = parent.getParent();
        }

        logger.info("Adding dependencies to " + reportProject);

        for (MavenProject project : session.getProjects()) {
            if (!skipped.contains(project)) {
                Dependency dependency = new Dependency();
                dependency.setGroupId(project.getGroupId());
                dependency.setArtifactId(project.getArtifactId());
                dependency.setVersion(project.getVersion());
                switch (project.getPackaging()) {
                    case "pom":
                        dependency.setType("pom");
                        break;
                    case "bundle":
                        dependency.setType("jar");
                        break;
                    default:
                        dependency.setType(project.getPackaging());
                }
                logger.debug(" - " + dependency);
                reportProject.getDependencies().add(dependency);
            }
        }
    }

}
