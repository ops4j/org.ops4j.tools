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
package org.ops4j.tools.jira2github.support;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;
import org.ops4j.tools.jira2github.model.Item;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HtmlToMd {

    public static final Logger LOG = LoggerFactory.getLogger(HtmlToMd.class);

    private static final Pattern point = Pattern.compile("^([0-9]+)([.)]) .*$");

    private HtmlToMd() {
    }

    private static int indent = 0;

    public static String markdown(String html) {
        indent = 0;
        StringBuilder sb = new StringBuilder();
        Document doc = Jsoup.parse(html);

        int pcount = 0;
        for (Node n : doc.body().childNodes()) {
            if (n instanceof Element) {
                Element child = (Element) n;
                switch (child.nodeName().toLowerCase()) {
                    case "p":
                        if (pcount > 0) {
                            sb.append("\n\n");
                        }
                        sb.append(p(child).trim());
                        pcount++;
                        break;
                    case "h3":
                        if (pcount > 0) {
                            sb.append("\n\n");
                        }
                        sb.append("### ").append(p(child).trim());
                        pcount++;
                        break;
                    case "h4":
                        if (pcount > 0) {
                            sb.append("\n\n");
                        }
                        sb.append("#### ").append(p(child).trim());
                        pcount++;
                        break;
                    case "h5":
                        if (pcount > 0) {
                            sb.append("\n\n");
                        }
                        sb.append("##### ").append(p(child).trim());
                        pcount++;
                        break;
                    case "div":
                        if (pcount > 0) {
                            sb.append("\n\n");
                        }
                        sb.append(div(child).trim());
                        pcount++;
                        break;
                    case "blockquote":
                        if (pcount > 0) {
                            sb.append("\n\n");
                        }
                        StringBuilder sb2 = new StringBuilder();
                        for (Element p : child.getElementsByTag("p")) {
                            sb2.append(p(p).trim()).append("\n\n");
                        }
                        try (BufferedReader r = new BufferedReader(new StringReader(sb2.toString().trim()))) {
                            String line = null;
                            while ((line = r.readLine()) != null) {
                                sb.append("> ").append(line.trim()).append("\n");
                            }
                        } catch (IOException ignored) {
                        }
                        pcount++;
                        break;
                    case "ul":
                        if (pcount > 0) {
                            sb.append("\n\n");
                        }
                        sb.append(ul(child).trim());
                        pcount++;
                        break;
                    case "ol":
                        if (pcount > 0) {
                            sb.append("\n\n");
                        }
                        sb.append(ol(child).trim());
                        pcount++;
                        break;
                    case "br":
                        if (pcount > 0) {
                            sb.append("\n\n<br />");
                        } else {
                            sb.append("<br />");
                        }
                        pcount++;
                        break;
                    case "hr":
                        if (pcount > 0) {
                            sb.append("\n\n");
                        }
                        sb.append("---");
                        pcount++;
                        break;
                    case "a":
                        if (pcount > 0) {
                            sb.append("\n\n");
                        }
                        sb.append(String.format("[%s](%s)", child.text(), child.attr("href")));
                        pcount++;
                        break;
                    case "span":
                        if (pcount > 0) {
                            sb.append("\n\n");
                        }
                        sb.append(q(child.text()));
                        pcount++;
                        break;
                    default:
                        throw new IllegalStateException("Unhandled element " + child);
                }
            } else if (n instanceof TextNode) {
                String text = ((TextNode) n).text();
                if (pcount > 0) {
                    sb.append("\n\n");
                }
                if (text.startsWith("> ")) {
                    sb.append("\\> ");
                    text = text.substring(2);
                } else if (text.trim().startsWith("- ")) {
                    sb.append("\\- ");
                    text = text.trim().substring(2);
                } else {
                    String t2 = text.trim();
                    Matcher m = point.matcher(t2);
                    if (m.matches()) {
                        sb.append(m.group(1)).append("\\").append(m.group(2)).append(" ").append(t2.substring(t2.indexOf(" ") + 1));
                        continue;
                    }
                }
                if (!text.trim().equals("")) {
                    sb.append(q(text));
                }
                pcount++;
            }
        }

        return sb.toString();
//        return doc.outputSettings(new Document.OutputSettings().prettyPrint(true)).body().html();
    }

    public static String markdownForLinks(String project, Item item, Properties links, Properties issues, Properties projects) {
        if (item.issuelinkTypes == null || item.issuelinkTypes.size() == 0) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        sb.append("\n---\n**Referenced issues**\n");

        for (Item.IssuelinkType ilt : item.issuelinkTypes) {
            String k1 = String.format("lt.%d", ilt.id);
            if (!links.containsKey(k1)) {
                throw new IllegalArgumentException("Unknown link type " + k1 + " (" + ilt.name + ")");
            }
            String ko = k1 + ".outward";
            String ki = k1 + ".inward";
            if (ilt.outwardlinks != null && ilt.outwardlinks.links != null && ilt.outwardlinks.links.size() > 0) {
                sb.append("\n**").append(links.getProperty(ko)).append(":**\n");
                for (Item.LinkKey link : ilt.outwardlinks.links) {
                    String target = link.issueKey.value;
                    if (!target.startsWith(project)) {
                        // link to another project - may already be imported from Jira to Github
                        if (issues.getProperty(target + ".summary") == null) {
                            LOG.warn("Unknown summary for " + target + " linked from " + item.key.value);
                        } else {
                            String jiraProject = target.split("-")[0];
                            String ghProject = projects.getProperty(jiraProject);
                            sb.append(String.format("* %s#%s - %s%n", ghProject, issues.getProperty(target), issues.getProperty(target + ".summary")));
                        }
                    } else {
                        // link to the same project
                        sb.append(String.format("* #%s - %s%n", issues.getProperty(target), issues.getProperty(target + ".summary")));
                    }
                }
            }
            if (ilt.inwardlinks != null && ilt.inwardlinks.links != null && ilt.inwardlinks.links.size() > 0) {
                sb.append("\n**").append(links.getProperty(ki)).append(":**\n");
                for (Item.LinkKey link : ilt.inwardlinks.links) {
                    String target = link.issueKey.value;
                    if (!target.startsWith(project)) {
                        // link to another project
                        if (issues.getProperty(target + ".summary") == null) {
                            LOG.warn("Unknown summary for " + target + " linked from " + item.key.value);
                        } else {
                            String jiraProject = target.split("-")[0];
                            String ghProject = projects.getProperty(jiraProject);
                            sb.append(String.format("* %s#%s - %s%n", ghProject, issues.getProperty(target), issues.getProperty(target + ".summary")));
                        }
                    } else {
                        // link to the same project
                        sb.append(String.format("* #%s - %s%n", issues.getProperty(target), issues.getProperty(target + ".summary")));
                    }
                }
            }
        }

        return sb.toString();
    }

    public static String pretty(String html) {
        StringBuilder sb = new StringBuilder();
        Document doc = Jsoup.parse(html);
        return doc.outputSettings(new Document.OutputSettings().prettyPrint(true)).body().html();
    }

    private static String p(Element p) {
        StringBuilder sb = new StringBuilder();
        boolean initial = true;
        boolean hadBr = false;
        for (Node c : p.childNodes()) {
            if (c instanceof TextNode) {
                String text = ((TextNode) c).text();
                if (initial || hadBr) {
                    if (text.startsWith("> ")) {
                        sb.append("\\> ");
                        text = text.substring(2);
                    } else if (text.trim().startsWith("- ")) {
                        sb.append("\\- ");
                        text = text.trim().substring(2);
                    } else if (text.trim().equals("```")) {
                        sb.append("\\```");
                        continue;
//                    } else {
//                        String t2 = text.trim();
//                        Matcher m = point.matcher(t2);
//                        if (initial && m.matches()) {
//                            sb.append(m.group(1)).append("\\").append(m.group(2)).append(" ").append(t2.substring(t2.indexOf(" ") + 1));
//                            continue;
//                        }
                    }
                }
                sb.append(q(text));
                if (text.endsWith("\\")) {
                    sb.append(" ");
                }
            } else if (c instanceof Element) {
                processContent((Element) c, sb);
                hadBr = c.nodeName().equals("br");
            }
            initial = false;
        }
        return sb.toString();
    }

    private static String ul(Element ul) {
        StringBuilder sb = new StringBuilder();
        for (Node c : ul.childNodes()) {
            if (c instanceof Element && "li".equalsIgnoreCase(c.nodeName())) {
                sb.append("\n");
                for (int i = 0; i < indent; i++) {
                    sb.append("   ");
                }
                sb.append("* ").append(li((Element) c));
            }
        }
        return sb.toString();
    }

    private static String ol(Element ul) {
        StringBuilder sb = new StringBuilder();
        for (Node c : ul.childNodes()) {
            if (c instanceof Element && "li".equalsIgnoreCase(c.nodeName())) {
                sb.append("\n");
                for (int i = 0; i < indent; i++) {
                    sb.append("   ");
                }
                sb.append("1. ").append(li((Element) c));
            }
        }
        return sb.toString();
    }

    private static String li(Element li) {
        indent++;
        StringBuilder sb = new StringBuilder();
        for (Node c : li.childNodes()) {
            if (c instanceof TextNode) {
                sb.append(q(((TextNode) c).text()));
            } else if (c instanceof Element) {
                if (c.nodeName().equals("p")) {
                    sb.append("\n");
                }
                processContent((Element) c, sb);
                if (c.nodeName().equals("p")) {
                    sb.append("\n");
                }
            }
        }
        indent--;
        return sb.toString();
    }

    private static void processContent(Element el, StringBuilder sb) {
        switch (el.nodeName().toLowerCase()) {
            case "tt":
            case "ins":
                sb.append("`").append(qtt(el.text())).append("`");
                break;
            case "b":
                sb.append("**").append(q(el.text())).append("**");
                break;
            case "i":
            case "em":
                sb.append("*").append(q(el.text())).append("*");
                break;
            case "span": {
                String cl = el.attr("class");
                if (cl != null && cl.contains("jira-issue-macro")) {
                    processContent(el.getElementsByTag("a").get(0), sb);
                    // Jira link to another issue
                    // <span class="jira-issue-macro resolved" data-jira-key="PAXLOGGING-9">
                    //   <a href="https://ops4j1.jira.com/browse/PAXLOGGING-9" class="jira-issue-macro-key issue-link" title="api not exporting package org.ops4j.pax.logging.slf4j, service not resolving">
                    //     <img class="icon" src="https://ops4j1.jira.com/secure/viewavatar?size=medium&amp;avatarId=11603&amp;avatarType=issuetype">
                    //     PAXLOGGING-9
                    //   </a>
                    //   <span class="aui-lozenge aui-lozenge-subtle aui-lozenge-success jira-macro-single-issue-export-pdf">Closed</span>
                    // </span>
                } else {
                    for (Node n : el.childNodes()) {
                        if (n instanceof TextNode) {
                            sb.append(q(((TextNode) n).text()));
                        } else if (n instanceof Element) {
                            if ("img".equals(n.nodeName())) {
                                if (cl != null && cl.contains("image-wrap")) {
                                    String href = n.attr("src");
                                    if (href != null && href.startsWith("/")) {
                                        String desc = href.substring(href.lastIndexOf('/') + 1);
                                        sb.append(String.format("![%s](https://ops4j1.jira.com%s)", desc, href));
                                    }
                                }
                                continue;
                            }
                            processContent((Element) n, sb);
                        }
                    }
                }
                break;
            }
            case "font":
                processContent(el.child(0), sb);
                break;
            case "a":
                sb.append(String.format("[%s](%s)", el.text(), el.attr("href")));
                break;
            case "ul":
                sb.append(ul(el));
                break;
            case "ol":
                sb.append(ol(el));
                break;
            case "br":
                sb.append("<br />");
                break;
            case "del":
                sb.append("~~").append(q(el.text())).append("~~");
                break;
            case "cite":
                sb.append("> ").append(q(el.text())).append("\n");
                break;
            case "p":
                sb.append(p(el).trim()).append("\n");
                break;
            case "img": {
                if ("emoticon".equals(el.attr("class"))) {
                    // https://github.com/ikatyang/emoji-cheat-sheet/blob/master/README.md
                    String src = el.attr("src");
                    if (src != null) {
                        if (src.endsWith("warning.png")) {
                            sb.append(":warning:");
                        } else if (src.endsWith("smile.png")) {
                            sb.append(":smile:");
                        } else if (src.endsWith("sad.png")) {
                            sb.append(":slightly_frowning_face:");
                        } else if (src.endsWith("wink.png")) {
                            sb.append(":wink:");
                        } else if (src.endsWith("help_16.png")) {
                            sb.append(":question:");
                        } else if (src.endsWith("biggrin.png")) {
                            sb.append(":grinning:");
                        } else if (src.endsWith("tongue.png")) {
                            sb.append(":stuck_out_tongue:");
                        } else if (src.endsWith("star_yellow.png")) {
                            sb.append(":star:");
                        } else {
                            throw new IllegalStateException("Unknown emoji " + src);
                        }
                    }
                } else {
                    throw new IllegalStateException("Unhandled <img> element " + el);
                }
                break;
            }
            case "div": {
                sb.append("\n").append(div(el).trim()).append("\n");
                break;
            }
            default:
                throw new IllegalStateException("Unhandled element " + el);
        }
    }

    private static String div(Element div) {
        StringBuilder sb = new StringBuilder();
        String c = div.attr("class");
        if ("code panel".equals(c)) {
            for (Element d1 : div.getElementsByTag("div")) {
                String c2 = d1.attr("class");
                if ("codeHeader panelHeader".equals(c2)) {
                    for (Element el : d1.children()) {
                        processContent(el, sb);
                    }
                } else if ("codeContent panelContent".equals(c2)) {
                    for (Element pre : d1.getElementsByTag("pre")) {
                        sb.append("\n```");
                        String c3 = pre.attr("class");
                        if (c3 != null && c3.startsWith("code-")) {
                            sb.append(c3.substring("code-".length()));
                        }
                        sb.append("\n");
                        String wt = pre.wholeText();
                        sb.append(wt);
                        if (!wt.endsWith("\n")) {
                            sb.append("\n");
                        }
                        sb.append("```");
                    }
                }
            }
        } else if ("preformatted panel".equals(c)) {
            for (Element d1 : div.getElementsByTag("div")) {
                String c2 = d1.attr("class");
                if ("preformattedContent panelContent".equals(c2)) {
                    for (Element pre : div.getElementsByTag("pre")) {
                        sb.append("\n```\n");
                        String wt = pre.wholeText();
                        sb.append(wt);
                        if (!wt.endsWith("\n")) {
                            sb.append("\n");
                        }
                        sb.append("```");
                    }
                }
            }
        } else if ("error".equals(c)) {
            if (div.child(0) != null && "span".equals(div.child(0).nodeName())) {
                if ("error".equals(div.child(0).attr("class"))) {
                    if (div.child(0).text().contains("Unknown macro")) {
                        return "";
                    }
                }
            }
        }

        return sb.toString();
    }

    /**
     * This should process text-only content and remove (un)intentional markdown used directly in Jira comment/description.
     * @param v
     * @return
     */
    private static String q(String v) {
        StringBuilder sb = new StringBuilder();
        boolean inMention = false;
        int idx = 0;
        for (char c : v.toCharArray()) {
            if (c == '<') {
                if (inMention) {
                    sb.append("`");
                    inMention = false;
                }
                sb.append("&lt;");
            } else if (c == '>') {
                if (inMention) {
                    sb.append("`");
                    inMention = false;
                }
                sb.append("&gt;");
            } else if (c == '&') {
                if (inMention) {
                    sb.append("`");
                    inMention = false;
                }
                sb.append("&amp;");
            } else if (c == '@') {
                // mention
                if (inMention) {
                    // new mention inside a mention (?)
                    sb.append("` `@");
                } else {
                    sb.append("`@");
                    inMention = true;
                }
            } else {
                if (inMention && !Character.isLetterOrDigit(c)) {
                    // end of mention
                    sb.append("`");
                    inMention = false;
                }
                sb.append(c);
            }
        }
        if (inMention) {
            sb.append("`");
        }
        return sb.toString();
    }

    /**
     * Special quote to be used inside backticks
     * @param v
     * @return
     */
    private static String qtt(String v) {
        if (!v.contains("`")) {
            return v;
        }

        v = v.replaceAll("<", "&lt;");
        v = v.replaceAll(">", "&gt;");
        v = v.replaceAll("&", "&amp;");
        return v;
    }

}
