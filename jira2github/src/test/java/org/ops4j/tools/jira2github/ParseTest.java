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
package org.ops4j.tools.jira2github;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.Unmarshaller;
import javax.xml.transform.stream.StreamSource;

import org.commonmark.node.Node;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;
import org.junit.jupiter.api.Test;
import org.ops4j.tools.jira2github.model.Item;
import org.ops4j.tools.jira2github.model.Rss;
import org.ops4j.tools.jira2github.support.HtmlToMd;

/**
 * This test's methods should be run before importing to visually check if everything is fine - markdown and fields
 * (like missing components, users, statuses, etc.)
 */
public class ParseTest {

    /**
     * This "test" copies all issue descriptions and all comments into single HTML to verify the original markup
     * exported from Jira.
     * @throws Exception
     */
    @Test
    public void parseHtml() throws Exception {
        JAXBContext jaxb = JAXBContext.newInstance(Rss.class.getPackage().getName());
        Unmarshaller u = jaxb.createUnmarshaller();

        Properties props = new Properties();
        try (FileReader fr = new FileReader("etc/application.properties")) {
            props.load(fr);
        }

        String project = props.getProperty("jira.project");
        String date = props.getProperty("jira.export.date");

        try (FileReader reader = new FileReader("data/" + project + "-" + date + ".xml");
                FileWriter writer = new FileWriter("target/" + project + "-" + date + "-nop.html")) {
            writer.write("<html>\n");
            writer.write("<head>\n");
            writer.write("  <meta charset=\"utf-8\" />\n");
            writer.write("  <link rel=\"stylesheet\" href=\"../src/test/resources/jira.css\" />\n");
            writer.write("</head>\n");
            writer.write("<body>\n");
            Rss rss = u.unmarshal(new StreamSource(reader), Rss.class).getValue();
            rss.sort();

            for (Item item : rss.channel.items) {
                writer.write("<div class=\"j2g-issue\">\n");
                writer.write("<div class=\"j2g-issue-info\">\n");
                writer.write(String.format("<a href=\"https://ops4j1.jira.com/browse/%s\">%s: %s</a>%n",
                        item.key.value, item.key.value, item.summary));
                writer.write("</div>\n");
                writer.write("<div class=\"j2g-issue-content\">\n");
                writer.write(HtmlToMd.pretty(item.htmlDescription));

                for (Item.Comment comment : item.comments) {
                    writer.write("<div class=\"j2g-comment\">\n");
                    writer.write("<div class=\"j2g-comment-info\">\n");
                    writer.write(String.format("Comment %s%n", comment.id));
                    writer.write("</div>\n");
                    writer.write("<div class=\"j2g-comment-content\">\n");
                    writer.write(HtmlToMd.pretty(comment.html));
                    writer.write("</div>\n");
                    writer.write("</div>\n");
                }
                writer.write("</div>\n");
                writer.write("</div>\n");
            }
            writer.write("</body>\n");
            writer.write("</html>\n");
        }
    }

    /**
     * This test produces a HTML which should match the above, but this time, the Jira HTML from issues/comments
     * is translated (by me) to Markdown, which is then rendered to HTML using {@link HtmlRenderer}.
     * @throws Exception
     */
    @Test
    public void parseMarkdown() throws Exception {
        JAXBContext jaxb = JAXBContext.newInstance(Rss.class.getPackage().getName());
        Unmarshaller u = jaxb.createUnmarshaller();

        Properties props = new Properties();
        try (FileReader fr = new FileReader("etc/application.properties")) {
            props.load(fr);
        }

        String project = props.getProperty("jira.project");
        String date = props.getProperty("jira.export.date");

        try (FileReader reader = new FileReader("data/" + project + "-" + date + ".xml");
                FileWriter writer = new FileWriter("target/" + project + "-" + date + "-gfm.html")) {
            writer.write("<html>\n");
            writer.write("<head>\n");
            writer.write("  <meta charset=\"utf-8\" />\n");
            writer.write("  <link rel=\"stylesheet\" href=\"../src/test/resources/jira.css\" />\n");
            writer.write("</head>\n");
            writer.write("<body>\n");
            Rss rss = u.unmarshal(new StreamSource(reader), Rss.class).getValue();
            rss.sort();

            Parser parser = Parser.builder().build();
            HtmlRenderer renderer = HtmlRenderer.builder().build();

            for (Item item : rss.channel.items) {
                writer.write("<div class=\"j2g-issue\">\n");
                writer.write("<div class=\"j2g-issue-info\">\n");
                writer.write(String.format("<a href=\"https://ops4j1.jira.com/browse/%s\">%s: %s</a>%n",
                        item.key.value, item.key.value, item.summary));
                writer.write("</div>\n");
                writer.write("<div class=\"j2g-issue-content\">\n");
                Node document = parser.parse(HtmlToMd.markdown(item.htmlDescription));
                writer.write(renderer.render(document));

                for (Item.Comment comment : item.comments) {
                    writer.write("<div class=\"j2g-comment\">\n");
                    writer.write("<div class=\"j2g-comment-info\">\n");
                    writer.write(String.format("Comment %s%n", comment.id));
                    writer.write("</div>\n");
                    writer.write("<div class=\"j2g-comment-content\">\n");
                    document = parser.parse(HtmlToMd.markdown(comment.html));
                    writer.write(renderer.render(document));
                    writer.write("</div>\n");
                    writer.write("</div>\n");
                }
                writer.write("</div>\n");
                writer.write("</div>\n");
            }
            writer.write("</body>\n");
            writer.write("</html>\n");
        }
    }

    /**
     * This test produces a HTML containing both original markup from Jira and an HTML markup rendered from markdown.
     * @throws Exception
     */
    @Test
    public void parseMarkdownAndOriginalMarkup() throws Exception {
        JAXBContext jaxb = JAXBContext.newInstance(Rss.class.getPackage().getName());
        Unmarshaller u = jaxb.createUnmarshaller();

        Properties props = new Properties();
        try (FileReader fr = new FileReader("etc/application.properties")) {
            props.load(fr);
        }

        String project = props.getProperty("jira.project");
        String date = props.getProperty("jira.export.date");

        try (FileReader reader = new FileReader("data/" + project + "-" + date + ".xml");
                FileWriter writer = new FileWriter("target/" + project + "-" + date + "-combined.html")) {
            writer.write("<html>\n");
            writer.write("<head>\n");
            writer.write("  <meta charset=\"utf-8\" />\n");
            writer.write("  <link rel=\"stylesheet\" href=\"../src/test/resources/jira.css\" />\n");
            writer.write("</head>\n");
            writer.write("<body>\n");
            Rss rss = u.unmarshal(new StreamSource(reader), Rss.class).getValue();
            rss.sort();

            Parser parser = Parser.builder().build();
            HtmlRenderer renderer = HtmlRenderer.builder().build();

            writer.write("<table>\n");
            for (Item item : rss.channel.items) {
                writer.write("<tr>\n");
                writer.write("<td>\n");
                // original markup
                writer.write("<div class=\"j2g-issue\">\n");
                writer.write("<div class=\"j2g-issue-info\">\n");
                writer.write(String.format("<a href=\"https://ops4j1.jira.com/browse/%s\">%s: %s</a>%n",
                        item.key.value, item.key.value, item.summary));
                writer.write("</div>\n");
                writer.write("<div class=\"j2g-issue-content\">\n");
                writer.write(HtmlToMd.pretty(item.htmlDescription));

                for (Item.Comment comment : item.comments) {
                    writer.write("<div class=\"j2g-comment\">\n");
                    writer.write("<div class=\"j2g-comment-info\">\n");
                    writer.write(String.format("Comment %s%n", comment.id));
                    writer.write("</div>\n");
                    writer.write("<div class=\"j2g-comment-content\">\n");
                    writer.write(HtmlToMd.pretty(comment.html));
                    writer.write("</div>\n");
                    writer.write("</div>\n");
                }
                writer.write("</div>\n");
                writer.write("</div>\n");
                writer.write("</td>\n");
                writer.write("<td>\n");
                // MD markup
                writer.write("<div class=\"j2g-issue\">\n");
                writer.write("<div class=\"j2g-issue-info\">\n");
                writer.write(String.format("<a href=\"https://ops4j1.jira.com/browse/%s\">%s: %s</a>%n",
                        item.key.value, item.key.value, item.summary));
                writer.write("</div>\n");
                writer.write("<div class=\"j2g-issue-content\">\n");
                Node document = parser.parse(HtmlToMd.markdown(item.htmlDescription));
                writer.write(renderer.render(document));

                for (Item.Comment comment : item.comments) {
                    writer.write("<div class=\"j2g-comment\">\n");
                    writer.write("<div class=\"j2g-comment-info\">\n");
                    writer.write(String.format("Comment %s%n", comment.id));
                    writer.write("</div>\n");
                    writer.write("<div class=\"j2g-comment-content\">\n");
                    document = parser.parse(HtmlToMd.markdown(comment.html));
                    writer.write(renderer.render(document));
                    writer.write("</div>\n");
                    writer.write("</div>\n");
                }
                writer.write("</div>\n");
                writer.write("</div>\n");
                writer.write("</td>\n");
                writer.write("</tr>\n");
            }
            writer.write("</table>\n");
            writer.write("</body>\n");
            writer.write("</html>\n");
        }
    }

    /**
     * This test produces the Markdown (manually)
     * @throws Exception
     */
    @Test
    public void printMarkdown() throws Exception {
        JAXBContext jaxb = JAXBContext.newInstance(Rss.class.getPackage().getName());
        Unmarshaller u = jaxb.createUnmarshaller();

        String project = "PAXWEB";

        try (FileReader reader = new FileReader("data/" + project + "-20210224-1.xml");
                FileWriter writer = new FileWriter("target/" + project + "-20210224-1.md")) {
            Rss rss = u.unmarshal(new StreamSource(reader), Rss.class).getValue();
            rss.sort();

            Parser parser = Parser.builder().build();

            for (Item item : rss.channel.items) {
                writer.write(String.format("%n# %s: %s%n", item.key.value, item.summary));
                writer.write(HtmlToMd.markdown(item.htmlDescription));

                for (Item.Comment comment : item.comments) {
                    writer.write(String.format("%n## Comment %s%n", comment.id));
                    writer.write(HtmlToMd.markdown(comment.html));
                }
            }
        }
    }

    /**
     * This test produces the Markdown (manually)
     * @throws Exception
     */
    @Test
    public void printMarkdownForReferences() throws Exception {
        JAXBContext jaxb = JAXBContext.newInstance(Rss.class.getPackage().getName());
        Unmarshaller u = jaxb.createUnmarshaller();

        Properties props = new Properties();
        try (FileReader fr = new FileReader("etc/application.properties")) {
            props.load(fr);
        }
        Properties links = new Properties();
        try (FileReader fr = new FileReader("etc/links.properties")) {
            links.load(fr);
        }
        Properties issues = new Properties();
        try (FileReader fr = new FileReader("etc/issues.properties")) {
            issues.load(fr);
        }
        Properties projects = new Properties();
        try (FileReader fr = new FileReader("etc/projects.properties")) {
            projects.load(fr);
        }

        Set<String> hadLinks = new LinkedHashSet<>();

        File[] xmls = new File("data").listFiles((dir, name) -> name.endsWith(".xml"));
        if (xmls == null) {
            return;
        }

        for (File xml : xmls) {
            String target = "target/" + xml.getName().replaceFirst("\\.xml$", "-links.md");
            String project = xml.getName().substring(0, xml.getName().indexOf('-'));
            try (FileReader reader = new FileReader(xml); FileWriter writer = new FileWriter(target)) {
                Rss rss = u.unmarshal(new StreamSource(reader), Rss.class).getValue();
                rss.sort();

                Parser parser = Parser.builder().build();

                for (Item item : rss.channel.items) {
                    String linksMd = HtmlToMd.markdownForLinks(project, item, links, issues, projects);
                    if (linksMd != null) {
                        writer.write("\n\n\n##########################################################################");
                        writer.write(String.format("%n# %s: %s%n", item.key.value, item.summary));
                        writer.write(linksMd);
                        hadLinks.add(item.key.value);
                    }
                }
            }
        }

        System.out.println("Issues with links: " + hadLinks.size());
    }

    @Test
    public void parseJiraFields() throws Exception {
        Properties users = new Properties();
        try (FileReader fr = new FileReader("etc/users.properties")) {
            users.load(fr);
        }
        Map<String, String> newUsers = new LinkedHashMap<>();
        Set<String> unknownUsers = new HashSet<>();

        Properties resolutions = new Properties();
        try (FileReader fr = new FileReader("etc/resolutions.properties")) {
            resolutions.load(fr);
        }
        Map<String, String> newResolutions = new LinkedHashMap<>();

        Properties types = new Properties();
        try (FileReader fr = new FileReader("etc/types.properties")) {
            types.load(fr);
        }
        Map<String, String> newTypes = new LinkedHashMap<>();

        JAXBContext jaxb = JAXBContext.newInstance(Rss.class.getPackage().getName());
        Unmarshaller u = jaxb.createUnmarshaller();

        File[] xmls = new File("data").listFiles((dir, name) -> name.endsWith(".xml"));
        if (xmls == null) {
            return;
        }

        for (File xml : xmls) {
            try (FileReader reader = new FileReader(xml)) {
                Rss rss = u.unmarshal(new StreamSource(reader), Rss.class).getValue();
                rss.sort();

                for (Item issue : rss.channel.items) {
                    String user = user(users, unknownUsers, issue.reporter.accountid);
                    if (user == null && issue.reporter.value != null) {
                        newUsers.put(issue.reporter.accountid, issue.reporter.value);
                    }

                    if (issue.resolution != null) {
                        String key = String.format("r.%d", issue.resolution.id);
                        if (!resolutions.containsKey(key)) {
                            newResolutions.put(key, issue.resolution.value);
                        } else if (!resolutions.get(key).equals(issue.resolution.value)) {
                            throw new IllegalStateException("Resolution " + key + " already had value "
                                    + resolutions.get(key) + " now it has " + issue.resolution.value);
                        }
                    }

                    if (issue.type != null) {
                        String key = String.format("t.%d", issue.type.id);
                        if (!types.containsKey(key)) {
                            newTypes.put(key, issue.type.value);
                        } else if (!types.get(key).equals(issue.type.value)) {
                            throw new IllegalStateException("Type " + key + " already had value "
                                    + types.get(key) + " now it has " + issue.type.value);
                        }
                    }

                    // Jira workflow status and category, but we're not that interested in them
//                    if (issue.status != null) {
//                        String key = String.format("s.%d", issue.status.id);
//                        if (!resolutions.containsKey(key)) {
//                            newResolutions.put(key, issue.status.value);
//                        } else if (!resolutions.get(key).equals(issue.status.value)) {
//                            throw new IllegalStateException("Status " + key + " already had value "
//                                    + resolutions.get(key) + " now it has " + issue.status.value);
//                        }
//                    }
//                    if (issue.statusCategory != null) {
//                        String key = String.format("sc.%d", issue.statusCategory.id);
//                        if (!resolutions.containsKey(key)) {
//                            newResolutions.put(key, issue.statusCategory.key);
//                        } else if (!resolutions.get(key).equals(issue.statusCategory.key)) {
//                            throw new IllegalStateException("Status Category " + key + " already had value "
//                                    + resolutions.get(key) + " now it has " + issue.statusCategory.key);
//                        }
//                    }

                    for (Item.Comment comment : issue.comments) {
                        user = user(users, unknownUsers, comment.author);
                        if (user == null) {
                            unknownUsers.add(comment.author);
                        }
                    }
                }
            }
        }

        System.out.println("Unknown users:");
        for (String user : unknownUsers) {
            if (!newUsers.containsKey(user)) {
                System.out.println(" - " + user);
            }
        }
        System.out.println("Detected users:");
        newUsers.forEach((id, user) -> {
            System.out.printf("%s = %s%n", id.replace("557058:", "557058\\:"), user);
        });
        System.out.println("Detected resolutions:");
        newResolutions.forEach((id, r) -> {
            if (id.equals("r.1") || id.equals("r.6") || id.equals("r.-1")) {
                return;
            }
            System.out.printf("%s = %s%n", id, r);
        });
        System.out.println("Detected issue types:");
        newTypes.forEach((id, t) -> {
            System.out.printf("%s = %s%n", id, t);
        });
    }

    @Test
    public void parseComponents() throws Exception {
        Properties components = new Properties();
        try (FileReader fr = new FileReader("etc/components.properties")) {
            components.load(fr);
        }
        Map<String, Set<String>> projectsComponents = new HashMap<>();
        List<String> unknownComponents = new LinkedList<>();
        for (Object o : components.keySet()) {
            String key = (String) o;
            if (key.endsWith(".label")) {
                continue;
            }
            Set<String> comps = projectsComponents.computeIfAbsent(key.split("\\.")[0], k -> new HashSet<>());
            comps.add(components.getProperty(key));
        }

        JAXBContext jaxb = JAXBContext.newInstance(Rss.class.getPackage().getName());
        Unmarshaller u = jaxb.createUnmarshaller();

        File[] xmls = new File("data").listFiles((dir, name) -> name.endsWith(".xml"));
        if (xmls == null) {
            return;
        }

        for (File xml : xmls) {
            try (FileReader reader = new FileReader(xml)) {
                Rss rss = u.unmarshal(new StreamSource(reader), Rss.class).getValue();
                rss.sort();

                // e.g., PAXLOGGING
                String prefix = xml.getName().split("-")[0];
                Set<String> comps = projectsComponents.computeIfAbsent(prefix, p -> new HashSet<>());

                int counter = comps.size();

                for (Item issue : rss.channel.items) {
                    if (issue.components != null) {
                        for (String c : issue.components) {
                            if (!comps.contains(c)) {
                                unknownComponents.add(String.format("%s.%d = %s", prefix, counter, c));
                                unknownComponents.add(String.format("%s.%d.label = component: %s", prefix, counter, c.toLowerCase()));
                                counter++;
                                comps.add(c);
                            }
                        }
                    }
                }
            }
        }

        System.out.println("Unknown components:");
        for (String cmpn : unknownComponents) {
            System.out.println(" - " + cmpn);
        }
    }

    @Test
    public void parseVersions() throws Exception {
        Properties versions = new Properties();
        try (FileReader fr = new FileReader("etc/versions.properties")) {
            versions.load(fr);
        }
        Map<String, Set<String>> projectsVersions = new HashMap<>();
        Set<String> unknownVersions = new HashSet<>();
        for (Object o : versions.keySet()) {
            String key = (String) o;
            String project = key.split("\\.")[0];
            Set<String> projectVersions = projectsVersions.computeIfAbsent(project, k -> new HashSet<>());
            projectVersions.add(key.substring(key.indexOf(".") + 1));
        }

        JAXBContext jaxb = JAXBContext.newInstance(Rss.class.getPackage().getName());
        Unmarshaller u = jaxb.createUnmarshaller();

        File[] xmls = new File("data").listFiles((dir, name) -> name.endsWith(".xml"));
        if (xmls == null) {
            return;
        }

        for (File xml : xmls) {
            try (FileReader reader = new FileReader(xml)) {
                Rss rss = u.unmarshal(new StreamSource(reader), Rss.class).getValue();
                rss.sort();

                // e.g., PAXLOGGING
                String prefix = xml.getName().split("-")[0];
                Set<String> vs = projectsVersions.computeIfAbsent(prefix, p -> new HashSet<>());

                for (Item issue : rss.channel.items) {
                    if (issue.versions != null) {
                        for (String c : issue.versions) {
                            c = c.trim();
                            if (!vs.contains(c)) {
                                unknownVersions.add(String.format("%s.%s", prefix, c));
                                vs.add(c);
                            }
                        }
                    }
                    if (issue.fixVersions != null) {
                        for (String c : issue.fixVersions) {
                            c = c.trim();
                            if (!vs.contains(c)) {
                                unknownVersions.add(String.format("%s.%s", prefix, c));
                                vs.add(c);
                            }
                        }
                    }
                }
            }
        }

        System.out.println("Unknown versions:");
        for (String v : unknownVersions) {
            System.out.println(" - " + v);
        }
    }

    @Test
    public void parseReferences() throws Exception {
        Properties issues = new Properties();
        try (FileReader fr = new FileReader("etc/issues.properties")) {
            issues.load(fr);
        }
        Properties links = new Properties();
        try (FileReader fr = new FileReader("etc/links.properties")) {
            links.load(fr);
        }
        Map<String, String> newLinks = new LinkedHashMap<>();

        JAXBContext jaxb = JAXBContext.newInstance(Rss.class.getPackage().getName());
        Unmarshaller u = jaxb.createUnmarshaller();

        File[] xmls = new File("data").listFiles((dir, name) -> name.endsWith(".xml"));
        if (xmls == null) {
            return;
        }

        for (File xml : xmls) {
            try (FileReader reader = new FileReader(xml)) {
                System.out.println("Parsing " + xml);
                Rss rss = u.unmarshal(new StreamSource(reader), Rss.class).getValue();
                rss.sort();

                // e.g., PAXLOGGING
                String jiraProject = xml.getName().split("-")[0];

                for (Item issue : rss.channel.items) {
                    if (issue.issuelinkTypes != null) {
                        for (Item.IssuelinkType ilt : issue.issuelinkTypes) {
                            String key = String.format("lt.%d", ilt.id);
                            if (!links.containsKey(key)) {
                                newLinks.put(key, ilt.name);
                            } else if (!links.get(key).equals(ilt.name)) {
                                throw new IllegalStateException("Link type " + key + " already had value "
                                        + links.get(key) + " now it has " + ilt.name);
                            }
                            if (ilt.inwardlinks != null) {
                                String ikey = String.format("%s.inward", key);
                                if (!links.containsKey(ikey)) {
                                    newLinks.put(ikey, ilt.inwardlinks.description);
                                } else if (!links.get(ikey).equals(ilt.inwardlinks.description)) {
                                    throw new IllegalStateException("Link type description " + ikey + " already had value "
                                            + links.get(ikey) + " now it has " + ilt.inwardlinks.description);
                                }
//                                for (Item.LinkKey lk : ilt.inwardlinks.links) {
//                                }
                            }
                            if (ilt.outwardlinks != null) {
                                String ikey = String.format("%s.outward", key);
                                if (!links.containsKey(ikey)) {
                                    newLinks.put(ikey, ilt.outwardlinks.description);
                                } else if (!links.get(ikey).equals(ilt.outwardlinks.description)) {
                                    throw new IllegalStateException("Link type description " + ikey + " already had value "
                                            + links.get(ikey) + " now it has " + ilt.outwardlinks.description);
                                }
//                                for (Item.LinkKey lk : ilt.outwardlinks.links) {
//                                }
                            }
                        }
                    }
                }
            }
        }

        System.out.println("Detected link types:");
        newLinks.forEach((id, t) -> {
            System.out.printf("%s = %s%n", id, t);
        });
    }

    private String user(Properties users, Set<String> unknownUsers, String author) {
        String user = users.getProperty(author);
        if (user == null) {
            unknownUsers.add(author);
        }
        return user;
    }

}
