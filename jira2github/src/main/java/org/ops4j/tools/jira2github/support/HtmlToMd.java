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
import java.util.LinkedList;
import java.util.List;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;

public class HtmlToMd {

    private HtmlToMd() {
    }

    private static int indent = 0;

    public static String markdown(String html) {
        indent = 0;
        StringBuilder sb = new StringBuilder();
        Document doc = Jsoup.parse(html);

        int pcount = 0;
        List<Element> flatList = new LinkedList<>();
        for (Element child : doc.body().children()) {
            switch (child.nodeName().toLowerCase()) {
                case "p":
                    if (pcount > 0) {
                        sb.append("\n\n");
                    }
                    sb.append(p(child).trim());
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
                        sb.append("\n\n");
                    } else {
                        sb.append("\n");
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
                default:
                    throw new IllegalStateException("Unhandled element " + child);
            }
        }

        return sb.toString();
//        return doc.outputSettings(new Document.OutputSettings().prettyPrint(true)).body().html();
    }

    public static String pretty(String html) {
        StringBuilder sb = new StringBuilder();
        Document doc = Jsoup.parse(html);
        return doc.outputSettings(new Document.OutputSettings().prettyPrint(true)).body().html();
    }

    private static String p(Element p) {
        StringBuilder sb = new StringBuilder();
        for (Node c : p.childNodes()) {
            if (c instanceof TextNode) {
                sb.append(((TextNode) c).text());
            } else if (c instanceof Element) {
                processContent((Element) c, sb);
            }
        }
        return sb.toString();
    }

    private static String ul(Element ul) {
        StringBuilder sb = new StringBuilder();
        for (Node c : ul.childNodes()) {
            if (c instanceof Element && "li".equalsIgnoreCase(c.nodeName())) {
                sb.append("\n");
                for (int i = 0; i < indent; i++) {
                    sb.append("  ");
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
                sb.append(((TextNode) c).text());
            } else if (c instanceof Element) {
                processContent((Element) c, sb);
            }
        }
        indent--;
        return sb.toString();
    }

    private static void processContent(Element el, StringBuilder sb) {
        switch (el.nodeName().toLowerCase()) {
            case "tt":
                sb.append("`").append(el.text()).append("`");
                break;
            case "b":
                sb.append("**").append(el.text()).append("**");
                break;
            case "i":
            case "em":
                sb.append("*").append(el.text()).append("*");
                break;
            case "span":
                sb.append(el.text());
                break;
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
                sb.append("\n");
                break;
            case "del":
                sb.append("~~").append(el.text()).append("~~");
                break;
            case "p":
                sb.append("\n").append(p(el).trim());
                break;
            case "img": {
                if ("emoticon".equals(el.attr("class"))) {
                    // https://github.com/ikatyang/emoji-cheat-sheet/blob/master/README.md
                    String src = el.attr("src");
                    if (src != null && src.endsWith("warning.png")) {
                        sb.append(":warning:");
                    }
                } else {
                    throw new IllegalStateException("Unhandled <img> element " + el);
                }
                break;
            }
            case "div": {
                sb.append("\n").append(div(el).trim());
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
                if ("codeContent panelContent".equals(c2)) {
                    for (Element pre : div.getElementsByTag("pre")) {
                        sb.append("\n```");
                        String c3 = pre.attr("class");
                        if (c3 != null && c3.startsWith("code-")) {
                            sb.append(c3.substring("code-".length()));
                        }
                        sb.append("\n");
                        sb.append(pre.wholeText().trim());
                        sb.append("\n```");
                    }
                }
            }
        } else if ("preformatted panel".equals(c)) {
            for (Element d1 : div.getElementsByTag("div")) {
                String c2 = d1.attr("class");
                if ("preformattedContent panelContent".equals(c2)) {
                    for (Element pre : div.getElementsByTag("pre")) {
                        sb.append("\n```\n");
                        sb.append(pre.wholeText().trim());
                        sb.append("\n```");
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

}
