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

import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;
import javax.xml.bind.annotation.XmlType;
import javax.xml.bind.annotation.XmlValue;
import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;

import org.ops4j.tools.jira2github.support.DateAdapter;

@XmlType(propOrder = {

})
public class Item {

    @XmlElement
    public String title;
    @XmlElement
    public String link;
    @XmlElement
    public Project project;
    @XmlElement(name = "description")
    public String htmlDescription;
    @XmlElement
    public String environment;
    @XmlElement
    public Key key;
    @XmlElement
    public String summary;
    @XmlElement
    public Type type;
    @XmlElement
    public Priority priority;
    @XmlElement
    public Status status;
    @XmlElement
    public StatusCategory statusCategory;
    @XmlElement
    public Resolution resolution;
    @XmlElement
    public Person assignee;
    @XmlElement
    public Person reporter;
    @XmlElementWrapper(name = "labels")
    @XmlElement(name = "label")
    public List<String> labels = new LinkedList<>();

    @XmlElement
    @XmlJavaTypeAdapter(DateAdapter.class)
    public Date created;
    @XmlElement
    @XmlJavaTypeAdapter(DateAdapter.class)
    public Date updated;
    @XmlElement
    @XmlJavaTypeAdapter(DateAdapter.class)
    public Date resolved;

    @XmlElement
    public List<String> fixVersion = new LinkedList<>();
    @XmlElement
    public List<String> version = new LinkedList<>();

    @XmlElement
    public String due;
    @XmlElement
    public int votes = 0;
    @XmlElement
    public int watches = 0;

    @XmlElementWrapper(name = "comments")
    @XmlElement(name = "comment")
    public List<Comment> comments = new LinkedList<>();

    @XmlElementWrapper(name = "attachments")
    @XmlElement(name = "attachment")
    public List<Attachment> attachments = new LinkedList<>();

    @XmlType
    public static class Project {
        @XmlAttribute
        public int id;
        @XmlAttribute
        public String key;
        @XmlValue
        public String value;
    }

    @XmlType
    public static class Key {
        @XmlAttribute
        public int id;
        @XmlValue
        public String value;
    }

    @XmlType
    public static class Type {
        @XmlAttribute
        public int id;
        @XmlAttribute
        public String iconUrl;
        @XmlValue
        public String value;
    }

    @XmlType
    public static class Priority {
        @XmlAttribute
        public int id;
        @XmlAttribute
        public String iconUrl;
        @XmlValue
        public String value;
    }

    @XmlType
    public static class Status {
        @XmlAttribute
        public int id;
        @XmlAttribute
        public String iconUrl;
        @XmlAttribute
        public String description;
        @XmlValue
        public String value;
    }

    @XmlType
    public static class StatusCategory {
        @XmlAttribute
        public int id;
        @XmlAttribute
        public String key;
        @XmlAttribute
        public String colorName;
    }

    @XmlType
    public static class Resolution {
        @XmlAttribute
        public int id;
        @XmlAttribute
        public String colorName;
    }

    @XmlType
    public static class Person {
        @XmlAttribute
        public String accountid;
        @XmlValue
        public String colorName;
    }

    @XmlType
    public static class Comment {
        @XmlAttribute
        public int id;
        @XmlAttribute
        public String author;
        @XmlAttribute
        @XmlJavaTypeAdapter(DateAdapter.class)
        public Date created;
        @XmlValue
        public String html;
    }

    @XmlType
    public static class Attachment {
        @XmlAttribute
        public int id;
        @XmlAttribute
        public String name;
        @XmlAttribute
        public int size;
        @XmlAttribute
        public String author;
        @XmlAttribute
        @XmlJavaTypeAdapter(DateAdapter.class)
        public Date created;
    }

}
