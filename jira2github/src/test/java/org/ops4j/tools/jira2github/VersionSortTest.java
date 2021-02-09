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

import java.util.Comparator;

import org.junit.jupiter.api.Test;
import org.ops4j.tools.jira2github.client.GithubIssueImporter;

import static org.assertj.core.api.Assertions.assertThat;

public class VersionSortTest {

    @Test
    public void testVersions() {
        Comparator<String> comp = new GithubIssueImporter.VersionComparator();
        assertThat(comp.compare("1.0", "1.0")).isEqualTo(0);
        assertThat(comp.compare("1.0.1", "1.0.2")).isEqualTo(-1);
        assertThat(comp.compare("1.0", "2.0")).isEqualTo(-1);
        assertThat(comp.compare("1.0.1", "1.0")).isEqualTo(1);
    }

}
