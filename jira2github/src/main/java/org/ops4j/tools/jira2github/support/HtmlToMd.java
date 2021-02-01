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

    public static String markdown(String html) {
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
                    sb.append(text(child).trim());
                    pcount++;
                    break;
                case "div":
                    if (pcount > 0) {
                        sb.append("\n\n");
                    }
                    sb.append(div(child).trim());
                    pcount++;
                    break;
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

    private static String text(Element p) {
        StringBuilder sb = new StringBuilder();
        for (Node c : p.childNodes()) {
            if (c instanceof TextNode) {
                sb.append(((TextNode) c).text());
            } else if (c instanceof Element) {
                switch (c.nodeName().toLowerCase()) {
                    case "tt":
                        sb.append("`").append(((Element) c).text()).append("`");
                        break;
                    case "span":
                        sb.append(((Element) c).text());
                        break;
                    case "a":
                        sb.append(String.format("[%s](%s)", ((Element) c).text(), c.attr("href")));
                        break;
                }
            }
        }
        return sb.toString();
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
        }
        if ("preformatted panel".equals(c)) {
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
        }
        return sb.toString();
    }

}
