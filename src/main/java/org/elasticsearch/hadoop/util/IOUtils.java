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
package org.elasticsearch.hadoop.util;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.net.URL;

import org.apache.commons.codec.binary.Base64;
import org.elasticsearch.hadoop.serialization.SerializationException;

/**
 * Utility class used internally for the Pig support.
 */
public abstract class IOUtils {

    public static String serializeToBase64(Serializable object) throws IOException {
        FastByteArrayOutputStream baos = new FastByteArrayOutputStream();
        ObjectOutputStream oos = new ObjectOutputStream(baos);
        try {
            oos.writeObject(object);
        } finally {
            close(oos);
        }
        return StringUtils.asUTFString(Base64.encodeBase64(baos.bytes().bytes(), false, true));
    }

    @SuppressWarnings("unchecked")
    public static <T extends Serializable> T deserializeFromBase64(String data) {
        byte[] rawData = Base64.decodeBase64(data.getBytes(StringUtils.UTF_8));
        ObjectInputStream ois = null;
        try {
            ois = new ObjectInputStream(new FastByteArrayInputStream(rawData));
            Object o = ois.readObject();
            return (T) o;
        } catch (ClassNotFoundException ex) {
            throw new IllegalStateException("cannot deserialize object", ex);
        } catch (IOException ex) {
            throw new SerializationException("cannot deserialize object", ex);
        } finally {
            close(ois);
        }
    }

    public static BytesArray asBytes(InputStream in) throws IOException {
        return asBytes(new BytesArray(in.available()), in);
    }

    public static BytesArray asBytes(BytesArray ba, InputStream in) throws IOException {
        FastByteArrayOutputStream bos = new FastByteArrayOutputStream(ba);
        byte[] buffer = new byte[1024];
        int read = 0;
        try {
            while ((read = in.read(buffer)) != -1) {
                bos.write(buffer, 0, read);
            }

        } finally {
            try {
                in.close();
            } catch (IOException ex) {
                // ignore
            }
        }
        return bos.bytes();
    }

    public static String asString(InputStream in) throws IOException {
        return asBytes(in).toString();
    }

    public static InputStream open(String resource, ClassLoader loader) {
        if (loader == null) {
            loader = Thread.currentThread().getContextClassLoader();
        }

        if (loader == null) {
            loader = IOUtils.class.getClassLoader();
        }

        try {
            // no prefix means classpath
            if (!resource.contains(":")) {
                return loader.getResourceAsStream(resource);
            }
            return new URL(resource).openStream();
        } catch (IOException ex) {
            throw new IllegalArgumentException(String.format("Cannot open stream for resource %s", resource));
        }
    }

    public static InputStream open(String location) {
        return open(location, null);
    }

    public static void close(Closeable closable) {
        if (closable != null) {
            try {
                closable.close();
            } catch (IOException e) {
                // silently ignore
            }
        }
    }
}