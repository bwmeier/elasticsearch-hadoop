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
package org.elasticsearch.hadoop.cascading;

import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.apache.commons.logging.LogFactory;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapred.JobConf;
import org.apache.hadoop.mapred.OutputCollector;
import org.apache.hadoop.mapred.RecordReader;
import org.elasticsearch.hadoop.cfg.InternalConfigurationOptions;
import org.elasticsearch.hadoop.cfg.Settings;
import org.elasticsearch.hadoop.cfg.SettingsManager;
import org.elasticsearch.hadoop.mr.EsInputFormat;
import org.elasticsearch.hadoop.mr.EsOutputFormat;
import org.elasticsearch.hadoop.mr.HadoopCfgUtils;
import org.elasticsearch.hadoop.mr.WritableBytesConverter;
import org.elasticsearch.hadoop.rest.InitializationUtils;
import org.elasticsearch.hadoop.serialization.builder.JdkValueReader;
import org.elasticsearch.hadoop.util.FieldAlias;
import org.elasticsearch.hadoop.util.StringUtils;

import cascading.flow.FlowProcess;
import cascading.scheme.Scheme;
import cascading.scheme.SinkCall;
import cascading.scheme.SourceCall;
import cascading.tap.Tap;
import cascading.tuple.Fields;
import cascading.tuple.Tuple;
import cascading.tuple.TupleEntry;

/**
 * Cascading Scheme handling
 */
@SuppressWarnings("rawtypes")
class EsHadoopScheme extends Scheme<JobConf, RecordReader, OutputCollector, Object[], Object[]> {

    private static final long serialVersionUID = 4304172465362298925L;

    private final String index;
    private final String query;
    private final String host;
    private final int port;

    EsHadoopScheme(String host, int port, String index, String query, Fields fields) {
        this.index = index;
        this.query = query;
        this.host = host;
        this.port = port;
        if (fields != null) {
            setSinkFields(fields);
            setSourceFields(fields);
        }
    }

    @Override
    public void sourcePrepare(FlowProcess<JobConf> flowProcess, SourceCall<Object[], RecordReader> sourceCall) throws IOException {
        super.sourcePrepare(flowProcess, sourceCall);

        Object[] context = new Object[3];
        context[0] = sourceCall.getInput().createKey();
        context[1] = sourceCall.getInput().createValue();
        // as the tuple _might_ vary (some objects might be missing), we use a map rather then a collection
        context[2] = CascadingUtils.alias(SettingsManager.loadFrom(flowProcess.getConfigCopy()));

        sourceCall.setContext(context);
    }

    @Override
    public void sourceCleanup(FlowProcess<JobConf> flowProcess, SourceCall<Object[], RecordReader> sourceCall) throws IOException {
        sourceCall.setContext(null);
    }

    @Override
    public void sinkPrepare(FlowProcess<JobConf> flowProcess, SinkCall<Object[], OutputCollector> sinkCall) throws IOException {
        super.sinkPrepare(flowProcess, sinkCall);

        Object[] context = new Object[1];
        // the tuple wil be fixed, so we can just use a collection/index
        context[0] = CascadingUtils.fieldToAlias(SettingsManager.loadFrom(flowProcess.getConfigCopy()), getSinkFields());
        sinkCall.setContext(context);
    }

    public void sinkCleanup(FlowProcess<JobConf> flowProcess, SinkCall<Object[], OutputCollector> sinkCall) throws IOException {
        sinkCall.setContext(null);
    }

    @Override
    public void sourceConfInit(FlowProcess<JobConf> flowProcess, Tap<JobConf, RecordReader, OutputCollector> tap, JobConf conf) {
        initTargetUri(conf);
        conf.setInputFormat(EsInputFormat.class);
        Collection<String> fields = CascadingUtils.fieldToAlias(SettingsManager.loadFrom(flowProcess.getConfigCopy()), getSourceFields());
        // load only the necessary fields
        conf.set(InternalConfigurationOptions.INTERNAL_ES_TARGET_FIELDS, StringUtils.concatenate(fields, ","));
    }

    @Override
    public void sinkConfInit(FlowProcess<JobConf> flowProcess, Tap<JobConf, RecordReader, OutputCollector> tap, JobConf conf) {
        initTargetUri(conf);
        conf.setOutputFormat(EsOutputFormat.class);
        // define an output dir to prevent Cascading from setting up a TempHfs and overriding the OutputFormat
        Settings set = SettingsManager.loadFrom(conf);

        InitializationUtils.setValueWriterIfNotSet(set, CascadingValueWriter.class, LogFactory.getLog(EsTap.class));
        InitializationUtils.setValueReaderIfNotSet(set, JdkValueReader.class, LogFactory.getLog(EsTap.class));
        InitializationUtils.setBytesConverterIfNeeded(set, WritableBytesConverter.class, LogFactory.getLog(EsTap.class));

        // NB: we need to set this property even though it is not being used - and since and URI causes problem, use only the resource/file
        //conf.set("mapred.output.dir", set.getTargetUri() + "/" + set.getTargetResource());
        HadoopCfgUtils.setFileOutputFormatDir(conf, set.getTargetResource());
        HadoopCfgUtils.setOutputCommitterClass(conf, EsOutputFormat.ESOldAPIOutputCommitter.class.getName());

    }

    private void initTargetUri(JobConf conf) {
        // init
        SettingsManager.loadFrom(conf).setHosts(host).setPort(port).setResource(index).setQuery(query).save();
    }

    @SuppressWarnings("unchecked")
    @Override
    public boolean source(FlowProcess<JobConf> flowProcess, SourceCall<Object[], RecordReader> sourceCall) throws IOException {
        Object[] context = sourceCall.getContext();

        if (!sourceCall.getInput().next(context[0], context[1])) {
            return false;
        }

        TupleEntry entry = sourceCall.getIncomingEntry();
        Map data = (Map) context[1];
        FieldAlias alias = (FieldAlias) context[2];

        if (entry.getFields().isDefined()) {
            // lookup using writables
            Text lookupKey = new Text();
            // TODO: it's worth benchmarking whether using an index/offset yields significantly better performance
            for (Comparable<?> field : entry.getFields()) {
                //NB: coercion should be applied automatically by the TupleEntry
                lookupKey.set(alias.toES(field.toString()));
                entry.setObject(field, data.get(lookupKey));
            }
        }
        else {
            // no definition means no coercion
            List<Object> elements = Tuple.elements(entry.getTuple());
            elements.clear();
            elements.addAll(data.values());
        }

        return true;
    }

    @SuppressWarnings("unchecked")
    @Override
    public void sink(FlowProcess<JobConf> flowProcess, SinkCall<Object[], OutputCollector> sinkCall) throws IOException {
        sinkCall.getOutput().collect(null, sinkCall);
    }
}