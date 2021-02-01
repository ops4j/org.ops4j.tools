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

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.time.ZoneId;
import java.util.Date;
import java.util.TimeZone;
import javax.xml.bind.annotation.adapters.XmlAdapter;

public class DateAdapter extends XmlAdapter<String, Date> {

    // Mon, 16 Apr 2018 16:18:16 +0200
    public static final DateFormat JIRA_FORMAT = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss Z");
    public static final DateFormat GH_FORMAT = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");

    static {
        GH_FORMAT.setTimeZone(TimeZone.getTimeZone(ZoneId.of("UTC")));
    }

    @Override
    public Date unmarshal(String v) throws Exception {
        return JIRA_FORMAT.parse(v);
    }

    @Override
    public String marshal(Date v) throws Exception {
        return JIRA_FORMAT.format(v);
    }

}
