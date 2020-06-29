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

import javax.imageio.stream.ImageInputStream;

/**
 * Last resort PDF preloader for PDFs with data before the header
 */
public class LastResortPreloaderPDF extends PreloaderPDF {
    protected byte[] getHeader(ImageInputStream in, int size) throws IOException {
        byte[] header = new byte[1024];
        long startPos = in.getStreamPosition();
        int len = in.read(header);
        in.seek(startPos);
        String s = new String(header, "US-ASCII");
        if (len > 0 && s.contains(PDF_HEADER)) {
            return PDF_HEADER.getBytes("US-ASCII");
        }
        return new byte[0];
    }

    public int getPriority() {
        return DEFAULT_PRIORITY * 3;
    }
}
