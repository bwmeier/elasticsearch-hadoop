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

import java.util.Collection;
import java.util.Properties;

import org.elasticsearch.hadoop.cascading.EsTap;
import org.elasticsearch.hadoop.integration.HdpBootstrap;
import org.elasticsearch.hadoop.integration.QueryTestParams;
import org.elasticsearch.hadoop.integration.Stream;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import cascading.flow.hadoop.HadoopFlowConnector;
import cascading.operation.AssertionLevel;
import cascading.operation.assertion.AssertNotNull;
import cascading.operation.assertion.AssertSizeEquals;
import cascading.operation.assertion.AssertSizeLessThan;
import cascading.pipe.Each;
import cascading.pipe.Pipe;
import cascading.tap.Tap;
import cascading.tuple.Fields;

@RunWith(Parameterized.class)
public class CascadingHadoopSearchTest {

    @Parameters
    public static Collection<Object[]> queries() {
        return QueryTestParams.params();
    }

    private String query;

    public CascadingHadoopSearchTest(String query) {
        this.query = query;
    }

    @Test
    public void testReadFromES() throws Exception {
        Tap in = new EsTap("cascading-hadoop/artists", query);
        Pipe pipe = new Pipe("copy");
        pipe = new Each(pipe, AssertionLevel.STRICT, new AssertSizeLessThan(5));
        pipe = new Each(pipe, AssertionLevel.STRICT, new AssertNotNull());

        // print out
        Tap out = new HadoopPrintStreamTap(Stream.NULL);
        //Tap out = new Hfs(new TextDelimited(), "cascadingbug-1", SinkMode.REPLACE);
        //FlowDef flowDef = FlowDef.flowDef().addSource(pipe, in).addTailSink(pipe, out);

        new HadoopFlowConnector(cfg()).connect(in, out, pipe).complete();
    }


    @Test
    public void testReadFromESWithFields() throws Exception {
        Tap in = new EsTap("cascading-hadoop/artists", query, new Fields("url", "name"));
        Pipe pipe = new Pipe("copy");
        pipe = new Each(pipe, AssertionLevel.STRICT, new AssertSizeEquals(2));
        pipe = new Each(pipe, AssertionLevel.STRICT, new AssertNotNull());

        // print out
        Tap out = new HadoopPrintStreamTap(Stream.NULL);
        new HadoopFlowConnector(cfg()).connect(in, out, pipe).complete();
    }

    @Test
    public void testReadFromESAliasedField() throws Exception {
        Tap in = new EsTap("cascading-hadoop/alias", query, new Fields("address"));
        Pipe pipe = new Pipe("copy");
        pipe = new Each(pipe, AssertionLevel.STRICT, new AssertNotNull());

        // print out
        Tap out = new HadoopPrintStreamTap(Stream.NULL);
        new HadoopFlowConnector(cfg()).connect(in, out, pipe).complete();
    }

    @Test
    public void testReadFromESWithFieldAlias() throws Exception {
        Tap in = new EsTap("cascading-hadoop/alias", query, new Fields("url"));
        Pipe pipe = new Pipe("copy");
        pipe = new Each(pipe, AssertionLevel.STRICT, new AssertNotNull());

        // print out
        Tap out = new HadoopPrintStreamTap(Stream.NULL);
        Properties cfg = cfg();
        cfg.setProperty("es.mapping.names", "url:address");
        new HadoopFlowConnector(cfg).connect(in, out, pipe).complete();
    }

    private Properties cfg() {
        Properties props = HdpBootstrap.asProperties(QueryTestParams.provisionQueries(CascadingHadoopSuite.configuration));
        //props.put(ConfigurationOptions.ES_QUERY, query);

        return props;
    }
}