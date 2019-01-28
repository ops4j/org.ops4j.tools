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

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.jar.JarInputStream;
import java.util.jar.Manifest;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.slf4j.Logger;

@Mojo(name = "manifest-summary", defaultPhase = LifecyclePhase.VERIFY, threadSafe = false)
public class Summary extends AbstractMojo {

    @Parameter(defaultValue = "${project}", readonly = true)
    private MavenProject project;

    @Parameter(defaultValue = "${session}", readonly = true)
    private MavenSession session;

    @Component
    private Logger logger;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        Set<Artifact> allArtifacts = new TreeSet<>(new ArtifactComparator());
        collect(allArtifacts, project.getArtifact(), project.getAttachedArtifacts());
        for (MavenProject p : session.getProjects()) {
            collect(allArtifacts, p.getArtifact(), p.getAttachedArtifacts());
        }

        for (Artifact artifact : allArtifacts) {
            logger.info("Processing {}", artifact);
        }
    }

    private void collect(Set<Artifact> collectedArtifacts, Artifact mainArtifact, List<Artifact> attachedArtifacts) {
        List<Artifact> artifacts = new LinkedList<>();
        if (mainArtifact != null) {
            artifacts.add(mainArtifact);
        }
        if (attachedArtifacts != null) {
            artifacts.addAll(attachedArtifacts);
        }
        for (Artifact artifact : artifacts) {
            if (artifact != null && artifact.getFile() != null) {
                File a = artifact.getFile();
                try (JarInputStream jis = new JarInputStream(new FileInputStream(a))) {
                    Manifest manifest = jis.getManifest();
                    if (manifest != null && manifest.getMainAttributes() != null
                            && manifest.getMainAttributes().getValue("Bundle-ManifestVersion") != null) {
                        collectedArtifacts.add(artifact);
                    }
                } catch (IOException e) {
                    logger.warn("Can't process {}: {}", artifact.toString(), e.getMessage());
                }
            }
        }
    }

    private class ArtifactComparator implements Comparator<Artifact> {

        @Override
        public int compare(Artifact a1, Artifact a2) {
            String s1 = artifactKey(a1);
            String s2 = artifactKey(a2);
            return s1.compareTo(s2);
        }

    }

    private String artifactKey(Artifact artifact) {
        return String.format("%s:%s:%s:%s:%s",
                artifact.getGroupId(), artifact.getArtifactId(),
                artifact.getVersion(),
                artifact.getType() == null || artifact.getType().equals("") ? "jar" : artifact.getType(),
                artifact.getClassifier() == null ? "" : artifact.getClassifier());
    }

}
