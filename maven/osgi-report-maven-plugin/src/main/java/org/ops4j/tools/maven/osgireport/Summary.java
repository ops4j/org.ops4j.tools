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
import java.io.FileWriter;
import java.io.IOException;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.jar.Attributes;
import java.util.jar.JarInputStream;
import java.util.jar.Manifest;

import org.apache.felix.utils.manifest.Attribute;
import org.apache.felix.utils.manifest.Clause;
import org.apache.felix.utils.manifest.Directive;
import org.apache.felix.utils.manifest.Parser;
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
import org.apache.maven.project.MavenProjectHelper;
import org.slf4j.Logger;

/**
 * Pretty formatting similar to {@code org.apache.karaf.bundle.command.Headers#generateFormattedOutput()}.
 */
@Mojo(name = "manifest-summary", defaultPhase = LifecyclePhase.VERIFY, threadSafe = true, inheritByDefault = false, aggregator = true)
public class Summary extends AbstractMojo {

    @Parameter(defaultValue = "${project}", readonly = true)
    private MavenProject project;

    @Parameter(defaultValue = "${session}", readonly = true)
    private MavenSession session;

    @Parameter(defaultValue = "${project.build.directory}/manifest-summary.txt")
    private File report;

    @Parameter(defaultValue = "true")
    private boolean attach;

    @Component
    private Logger logger;

    @Component
    private MavenProjectHelper projectHelper;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {

        Set<Artifact> allArtifacts = new TreeSet<>(new ArtifactComparator());
        collect(allArtifacts, project.getArtifact(), project.getAttachedArtifacts());
        for (MavenProject p : session.getProjects()) {
            collect(allArtifacts, p.getArtifact(), p.getAttachedArtifacts());
        }

        report.getParentFile().mkdirs();

        try (FileWriter fw = new FileWriter(report)) {
            for (Artifact artifact : allArtifacts) {
                logger.info("Processing {}", artifact);
                fw.write("= " + artifact.toString() + "\n");
                process(artifact, fw);
                fw.write("\n\n");
            }
        } catch (IOException e) {
            throw new MojoExecutionException(e.getMessage(), e);
        }

        if (attach) {
            logger.info("Attaching " + report);
            projectHelper.attachArtifact(session.getCurrentProject(), "txt", "manifest-summary", report);
        }
    }

    /**
     * Process {@code META-INF/MANIFEST.MF} from single artifact
     * @param artifact
     * @param fw
     */
    private void process(Artifact artifact, FileWriter fw) throws IOException {
        // Bundle-*
        Map<String, Object> bundleAttributes = new TreeMap<>();
        // *-Package and Require-Bundle
        Map<String, Object> packageAttributes = new TreeMap<>();
        // *-Service
        Map<String, Object> serviceAttributes = new TreeMap<>();
        // *-Capability
        Map<String, Object> capAttributes = new TreeMap<>();
        // remaining
        Map<String, Object> otherAttributes = new TreeMap<>();

        File a = artifact.getFile();
        try (JarInputStream jis = new JarInputStream(new FileInputStream(a))) {
            Manifest manifest = jis.getManifest();
            Attributes attrs = manifest.getMainAttributes();
            for (Object header : attrs.keySet()) {
                String h = header.toString();
                if (h.startsWith("Bundle-")) {
                    bundleAttributes.put(h, attrs.getValue(h));
                } else if (h.endsWith("-Package") || h.equals("Require-Bundle")) {
                    packageAttributes.put(h, attrs.getValue(h));
                } else if (h.endsWith("-Service")) {
                    serviceAttributes.put(h, attrs.getValue(h));
                } else if (h.endsWith("-Capability")) {
                    capAttributes.put(h, attrs.getValue(h));
                } else {
                    otherAttributes.put(h, attrs.getValue(h));
                }
            }
        } catch (IOException e) {
            logger.warn("Can't process {}: {}", artifact.toString(), e.getMessage());
        }

        fw.write("\n== General attributes\n\n");
        for (String k : otherAttributes.keySet()) {
            String v = (String) otherAttributes.get(k);
            if (v != null) {
                fw.write(String.format("%s: %s\n", k, v));
            }
        }

        fw.write("\n== Bundle attributes\n\n");
        for (String k : bundleAttributes.keySet()) {
            String v = (String) bundleAttributes.get(k);
            if (v != null) {
                fw.write(String.format("%s: %s\n", k, v));
            }
        }

        fw.write("\n== Service attributes\n\n");
        for (String k : serviceAttributes.keySet()) {
            String v = (String) serviceAttributes.get(k);
            if (v != null) {
                fw.write("\n" + k + ":\n");
                printFormatted(fw, v);
            }
        }

        fw.write("\n== Capabilities attributes\n\n");
        for (String k : capAttributes.keySet()) {
            String v = (String) capAttributes.get(k);
            if (v != null) {
                fw.write("\n" + k + ":\n");
                printFormatted(fw, v);
            }
        }

        fw.write("\n== Package attributes\n");
        for (String k : packageAttributes.keySet()) {
            String v = (String) packageAttributes.get(k);
            if (v != null) {
                fw.write("\n" + k + ":\n");
                printFormatted(fw, v);
            }
        }
    }

    private void printFormatted(FileWriter fw, String value) throws IOException {
        Clause[] clauses = Parser.parseHeader(value);
        Arrays.sort(clauses, Comparator.comparing(Clause::getName));
        for (Clause c : clauses) {
            fw.write(String.format("    %s\n", c.getName()));
            Attribute[] atts = c.getAttributes();
            Directive[] dirs = c.getDirectives();
            Arrays.sort(atts, Comparator.comparing(Attribute::getName));
            Arrays.sort(dirs, Comparator.comparing(Directive::getName));
            for (Attribute at : atts) {
                fw.write(String.format("        %s = %s\n", at.getName(), at.getValue()));
            }
            for (Directive d : dirs) {
                if ("uses".equals(d.getName())) {
                    String[] pkgs = d.getValue().split("\\s*,\\s*");
                    Arrays.sort(pkgs);
                    fw.write("        uses :=\n");
                    for (String pkg : pkgs) {
                        fw.write("            " + pkg + "\n");
                    }
                } else {
                    fw.write(String.format("        %s := %s\n", d.getName(), d.getValue()));
                }
            }
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
