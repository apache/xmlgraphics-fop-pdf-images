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
package org.apache.fop.render.pdf.pdfbox;

import java.nio.charset.StandardCharsets;

import org.junit.Assert;
import org.junit.Test;

import org.apache.commons.io.IOUtils;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.io.RandomAccessReadBuffer;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;

import org.apache.xmlgraphics.image.loader.ImageInfo;
import org.apache.xmlgraphics.image.loader.impl.ImageRawStream;

public class ImageConverterPDF2AFPTestCase {
    @Test
    public void testConverter() throws Exception {
        try (PDDocument orgdoc = PDFBoxAdapterTestCase.load(PDFBoxAdapterTestCase.ANNOT)) {
            String orgPage = IOUtils.toString(orgdoc.getPage(1).getContents(), StandardCharsets.UTF_8);
            Assert.assertEquals(orgdoc.getNumberOfPages(), 2);
            ImageInfo info = new ImageInfo("x.pdf#page=2", ImagePDF.MIME_PDF);
            ImagePDF imagePDF = new ImagePDF(info, orgdoc);
            ImageConverterPDF2AFP converter = new ImageConverterPDF2AFP();
            ImageRawStream stream = (ImageRawStream) converter.convert(imagePDF, null);
            try (PDDocument doc = Loader.loadPDF(new RandomAccessReadBuffer(stream.createInputStream()))) {
                PDPage page = doc.getPage(0);
                Assert.assertEquals(orgPage, IOUtils.toString(page.getContents(), StandardCharsets.UTF_8));
                Assert.assertEquals(doc.getNumberOfPages(), 1);
                Assert.assertEquals(stream.getMimeType(), ImagePDF.MIME_PDF);
            }
        }
    }
}
