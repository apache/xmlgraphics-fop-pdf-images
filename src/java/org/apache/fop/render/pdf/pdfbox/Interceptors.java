/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/* $Id$ */

package org.apache.fop.render.pdf.pdfbox;

import java.io.IOException;
import java.net.URI;

import org.apache.pdfbox.pdmodel.PDDocument;

/**
 * Handles interceptors (hooks) that can operate on PDF documents.
 */
public class Interceptors {

    private static Interceptors instance;

    private OnLoadInterceptor onLoad = null;

    public static synchronized Interceptors getInstance() {
        if (instance == null) {
            instance = new Interceptors();
        }
        return instance;
    }

    /**
     * Intercepts the given PDF document on load.
     * @param doc the PDF document
     * @param uri the URI it has been loaded from (may be null)
     * @return the given document or potentially an alternative PDF
     * @throws IOException if an I/O occurs
     * @see OnLoadInterceptor#intercept(PDDocument, URI)
     */
    public PDDocument interceptOnLoad(PDDocument doc, URI uri) throws IOException {
        PDDocument result = null;
        if (onLoad != null) {
            result = onLoad.intercept(doc, uri);
        }
        if (result == null) {
            result = doc;
        }
        return result;
    }

    /**
     * Set the on-load interceptor.
     * @param interceptor the interceptor instance
     */
    public void setOnLoad(OnLoadInterceptor interceptor) {
        this.onLoad = interceptor;
    }

}
