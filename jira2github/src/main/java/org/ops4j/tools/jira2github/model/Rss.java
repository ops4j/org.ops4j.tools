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

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name = "rss")
public class Rss {

    @XmlElement(name = "channel")
    public Channel channel;

    public void sort() {
        channel.items.sort((i1, i2) -> {
            int k1 = Integer.parseInt(i1.key.value.substring(i1.key.value.indexOf('-') + 1));
            int k2 = Integer.parseInt(i2.key.value.substring(i2.key.value.indexOf('-') + 1));
            return k1 - k2;
        });
    }

}
