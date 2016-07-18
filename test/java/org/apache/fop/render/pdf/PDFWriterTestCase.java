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
package org.apache.fop.render.pdf;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Locale;

import org.junit.Assert;
import org.junit.Test;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.common.PDStream;

import org.apache.fop.render.pdf.pdfbox.PDFWriter;

public class PDFWriterTestCase {
    @Test
    public void testFloat() throws IOException {
        Locale l = Locale.getDefault();
        Locale.setDefault(Locale.FRENCH);
        PDFWriter pdfWriter = new PDFWriter(null, 0);
        String text = "[1.1 ] a";
        PDStream pdStream = new PDStream(new PDDocument(), new ByteArrayInputStream(text.getBytes("UTF-8")));
        Assert.assertEquals(pdfWriter.writeText(pdStream), text + "\n");
        Locale.setDefault(l);
    }
}
