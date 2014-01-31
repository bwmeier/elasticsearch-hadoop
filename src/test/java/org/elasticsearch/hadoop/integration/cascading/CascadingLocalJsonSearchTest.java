/*
 * Licensed to Elasticsearch under one or more contributor
 * license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright
 * ownership. Elasticsearch licenses this file to you under
 * the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.elasticsearch.hadoop.integration.cascading;

import java.io.OutputStream;
import java.util.Collection;
import java.util.Properties;

import org.elasticsearch.hadoop.cascading.EsTap;
import org.elasticsearch.hadoop.cfg.ConfigurationOptions;
import org.elasticsearch.hadoop.integration.QueryTestParams;
import org.elasticsearch.hadoop.integration.Stream;
import org.elasticsearch.hadoop.util.TestSettings;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import cascading.flow.local.LocalFlowConnector;
import cascading.operation.AssertionLevel;
import cascading.operation.aggregator.Count;
import cascading.operation.assertion.AssertSizeLessThan;
import cascading.operation.filter.FilterNotNull;
import cascading.pipe.Each;
import cascading.pipe.Every;
import cascading.pipe.GroupBy;
import cascading.pipe.Pipe;
import cascading.scheme.local.TextLine;
import cascading.tap.Tap;

@RunWith(Parameterized.class)
public class CascadingLocalJsonSearchTest {

    @Parameters
    public static Collection<Object[]> queries() {
        return QueryTestParams.localParams();
    }

    private final String indexPrefix = "json-";
    private final String query;

    public CascadingLocalJsonSearchTest(String query) {
        this.query = query;
    }

    private OutputStream OUT = Stream.NULL.stream();

    @Test
    public void testReadFromES() throws Exception {
        Tap in = new EsTap(indexPrefix + "cascading-local/artists");
        Pipe pipe = new Pipe("copy");
        pipe = new Each(pipe, new FilterNotNull());
        pipe = new Each(pipe, AssertionLevel.STRICT, new AssertSizeLessThan(5));
        // can't select when using unknown
        //pipe = new Each(pipe, new Fields("name"), AssertionLevel.STRICT, new AssertNotNull());
        pipe = new GroupBy(pipe);
        pipe = new Every(pipe, new Count());

        // print out
        Tap out = new OutputStreamTap(new TextLine(), OUT);
        new LocalFlowConnector(cfg()).connect(in, out, pipe).complete();
    }

    private Properties cfg() {
        Properties props = new TestSettings().getProperties();
        props.put(ConfigurationOptions.ES_QUERY, query);
        return props;
    }
}