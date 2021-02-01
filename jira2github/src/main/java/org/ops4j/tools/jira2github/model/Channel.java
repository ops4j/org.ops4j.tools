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
package org.ops4j.tools.jira2github.model;

import java.util.LinkedList;
import java.util.List;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;

@XmlType
public class Channel {

    /*
<title>OPS4J Issues</title>
<link>https://ops4j1.jira.com/issues/?jql=project+%3D+PAXTRANSX+ORDER+BY+key+ASC%2C+status+DESC%2C+priority+DESC</link>
<description>An XML representation of a search request</description>
<language>en-us</language>
<issue start="0" end="12" total="12" />
<build-info>
    <version>1001.0.0-SNAPSHOT</version>
    <build-number>100154</build-number>
    <build-date>29-01-2021</build-date>
</build-info>
     */

    @XmlElement
    public String type;
    @XmlElement
    public String link;
    @XmlElement
    public String description;
    @XmlElement
    public String language;
    @XmlElement(name = "issue")
    public IssueSummary issueSummary;
    @XmlElement(name = "build-info")
    public BuildInfo buildInfo;

    @XmlElement(name = "item")
    public List<Item> items = new LinkedList<>();

    @XmlType
    public static class IssueSummary {
        @XmlAttribute
        public int start;
        @XmlAttribute
        public int end;
        @XmlAttribute
        public int total;
    }

    @XmlType
    public static class BuildInfo {
        @XmlElement
        public String version;
        @XmlElement(name = "build-number")
        public String buildNumber;
        @XmlElement(name = "build-date")
        public String buildDate;
    }

}
