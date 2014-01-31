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
package org.elasticsearch.hadoop.integration.hive;

import java.util.Collection;
import java.util.List;

import org.elasticsearch.hadoop.integration.QueryTestParams;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import static org.junit.Assert.*;

import static org.elasticsearch.hadoop.integration.hive.HiveSuite.*;

@RunWith(Parameterized.class)
public class HiveSearchJsonTest {

    private static int testInstance = 0;

    @Parameters
    public static Collection<Object[]> queries() {
        return QueryTestParams.params();
    }

    private String query;

    public HiveSearchJsonTest(String query) {
        this.query = query;
    }


    @Before
    public void before() throws Exception {
        provisionEsLib();
    }

    @After
    public void after() throws Exception {
        testInstance++;
        HiveSuite.after();
    }


    @Test
    public void basicLoad() throws Exception {

        String create = "CREATE EXTERNAL TABLE jsonartistsload" + testInstance + "("
                + "number 		STRING, "
                + "name 	STRING, "
                + "url  STRING, "
                + "picture  STRING) "
                + tableProps("json-hive/artists");

        String select = "SELECT * FROM jsonartistsload" + testInstance;

        server.execute(create);
        List<String> result = server.execute(select);
        assertTrue("Hive returned null", containsNoNull(result));
        assertContains(result, "Marilyn");
        assertContains(result, "last.fm/music/MALICE");
        assertContains(result, "last.fm/serve/252/5872875.jpg");
    }

    @Test
    public void basicCountOperator() throws Exception {
        String create = "CREATE EXTERNAL TABLE jsonartistscount" + testInstance + " ("
                + "number       STRING, "
                + "name     STRING, "
                + "url  STRING, "
                + "picture  STRING) "
                + tableProps("json-hive/artists");

        String select = "SELECT count(*) FROM jsonartistscount" + testInstance;

        server.execute(create);
        List<String> result = server.execute(select);
        assertTrue("Hive returned null", containsNoNull(result));
        assertEquals(1, result.size());
        assertTrue(Integer.valueOf(result.get(0)) > 1);
    }

    @Test
    public void testMissingIndex() throws Exception {
        String create = "CREATE EXTERNAL TABLE jsonmissing" + testInstance + " ("
                + "daTE     TIMESTAMP, "
                + "Name     STRING, "
                + "links    STRUCT<uRl:STRING, pICture:STRING>) "
                + tableProps("foobar/missing", "'es.index.read.missing.as.empty' = 'true'");

        String select = "SELECT * FROM jsonmissing" + testInstance;

        server.execute(create);
        List<String> result = server.execute(select);
        assertEquals(0, result.size());
    }

    @Test
    public void testVarcharLoad() throws Exception {

        String create = "CREATE EXTERNAL TABLE jsonvarcharload" + testInstance + " ("
                + "number       STRING, "
                + "name     STRING, "
                + "url  STRING, "
                + "picture  STRING) "
                + tableProps("json-hive/varcharsave");

        String select = "SELECT * FROM jsonvarcharload" + testInstance;

        System.out.println(server.execute(create));
        List<String> result = server.execute(select);
        assertTrue("Hive returned null", containsNoNull(result));
        assertTrue(result.size() > 1);
        assertContains(result, "Marilyn");
        assertContains(result, "last.fm/music/MALICE");
        assertContains(result, "last.fm/serve/252/2181591.jpg");

    }

    @Test
    public void testParentChild() throws Exception {
        String create = "CREATE EXTERNAL TABLE jsonchildload" + testInstance + " ("
                + "number       STRING, "
                + "name     STRING, "
                + "url  STRING, "
                + "picture  STRING) "
                + tableProps("json-hive/child", "'es.index.read.missing.as.empty' = 'true'");

        String select = "SELECT * FROM jsonchildload" + testInstance;

        System.out.println(server.execute(create));
        List<String> result = server.execute(select);
        assertTrue("Hive returned null", containsNoNull(result));
        assertTrue(result.size() > 1);
        assertContains(result, "Marilyn");
        assertContains(result, "last.fm/music/MALICE");
        assertContains(result, "last.fm/serve/252/2181591.jpg");
    }

    private static boolean containsNoNull(List<String> str) {
        for (String string : str) {
            if (string.contains("NULL")) {
                return false;
            }
        }

        return true;
    }

    private static void assertContains(List<String> str, String content) {
        for (String string : str) {
            if (string.contains(content)) {
                return;
            }
        }
        fail(String.format("'%s' not found in %s", content, str));
    }


    private String tableProps(String resource, String... params) {
        return HiveSuite.tableProps(resource, query, params);
    }
}