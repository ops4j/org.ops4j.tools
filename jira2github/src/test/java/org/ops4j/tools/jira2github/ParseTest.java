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

import java.io.FileReader;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.Unmarshaller;
import javax.xml.transform.stream.StreamSource;

import org.junit.jupiter.api.Test;
import org.ops4j.tools.jira2github.model.Item;
import org.ops4j.tools.jira2github.model.Rss;
import org.ops4j.tools.jira2github.support.HtmlToMd;

public class ParseTest {

    @Test
    public void parseJiraIssues() throws Exception {
        JAXBContext jaxb = JAXBContext.newInstance(Rss.class.getPackage().getName());
        Unmarshaller u = jaxb.createUnmarshaller();

        try (FileReader reader = new FileReader("data/ops4j-tools-20210204.xml")) {
            Rss rss = u.unmarshal(new StreamSource(reader), Rss.class).getValue();
            rss.sort();
            System.out.println(rss.channel.items.size());

            for (Item item : rss.channel.items) {
                System.out.printf("======= Issue %s =======%n", item.key.value);
                System.out.printf(" - %s%n", item.summary);
                System.out.println(HtmlToMd.pretty(item.htmlDescription));

                for (Item.Comment comment : item.comments) {
                    System.out.printf("======= Comment %s =======%n", comment.id);
                    System.out.println(HtmlToMd.pretty(comment.html));
                }
            }
        }
    }

}
