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
 * Interceptor interface for performing on newly loaded PDF files. This can be used, for example,
 * to decrypt an encrypted PDF.
 */
public interface OnLoadInterceptor {

    /**
     * Called when a new PDF document has been loaded.
     * @param doc the PDF document
     * @param uri the URI it has been loaded from (may be null)
     * @return the given document or potentially an alternative PDF
     * @throws IOException if an I/O occurs
     */
    PDDocument intercept(PDDocument doc, URI uri) throws IOException;

}
