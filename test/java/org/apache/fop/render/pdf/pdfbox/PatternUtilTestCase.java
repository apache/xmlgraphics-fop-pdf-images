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

import java.awt.Rectangle;
import java.awt.geom.AffineTransform;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.junit.Test;
import static org.junit.Assert.assertFalse;

import org.apache.pdfbox.pdmodel.PDDocument;

import org.apache.fop.events.DefaultEventBroadcaster;
import org.apache.fop.pdf.PDFDocument;
import org.apache.fop.pdf.PDFPage;

public class PatternUtilTestCase {

    @Test
    public void testPatternsAreWrittenOnce() throws Exception {
        checkPatternsWrittenOnce(false);
        checkPatternsWrittenOnce(true);
    }

    @Test
    public void testPatternsOfTwoImagesOnOnePageAreWrittenOnce() throws Exception {
        PDFDocument pdfdoc = new PDFDocument("");
        PDFPage pdfpage = PDFBoxAdapterTestCase.getPDFPage(pdfdoc);
        pdfdoc.assignObjectNumber(pdfpage);
        pdfpage.setDocument(pdfdoc);
        Rectangle[] destRects = {new Rectangle(0, 1650, 274818, 174879), new Rectangle(0, 200000, 274818, 174879)};
        for (Rectangle destRect : destRects) {
            try (PDDocument doc = PDFBoxAdapterTestCase.load(PDFBoxAdapterTestCase.SHADING)) {
                PDFBoxAdapter adapter = new PDFBoxAdapter(pdfpage, new HashMap<>(), new HashMap<>(), new HashMap<>(),
                        new HashMap<>(), new DefaultEventBroadcaster());
                adapter.createStreamFromPDFBoxPage(doc, doc.getPage(0), "key", new AffineTransform(), null,
                        destRect, new AffineTransform());
            }
        }
        assertNoDuplicateObjects(pdfdoc);
    }

    private void checkPatternsWrittenOnce(boolean formXObject) throws Exception {
        PDFDocument pdfdoc = new PDFDocument("");
        pdfdoc.setFormXObjectEnabled(formXObject);

        String pdf = PDFBoxAdapterTestCase.SHADING;
        try (PDDocument doc = PDFBoxAdapterTestCase.load(pdf)) {
            PDFPage pdfpage = PDFBoxAdapterTestCase.getPDFPage(pdfdoc);
            pdfdoc.assignObjectNumber(pdfpage);
            pdfpage.setDocument(pdfdoc);
            PDFBoxAdapter adapter = new PDFBoxAdapter(pdfpage, new HashMap<>(), new HashMap<>(), new HashMap<>(),
                    new HashMap<>(), new DefaultEventBroadcaster());
            adapter.createStreamFromPDFBoxPage(doc, doc.getPage(0), "key", new AffineTransform(), null,
                    new Rectangle(0, 1650, 274818, 174879), new AffineTransform());
        }
        assertNoDuplicateObjects(pdfdoc);
    }

    private void assertNoDuplicateObjects(PDFDocument pdfdoc) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        pdfdoc.output(bos);
        List<String> objects = new ArrayList<>();
        for (String line : bos.toString(StandardCharsets.ISO_8859_1.name()).split("\n")) {
            if (line.endsWith(" 0 obj")) {
                assertFalse("Object written twice: " + line, objects.contains(line));
                objects.add(line);
            }
        }
        assertFalse("The output must contain PDF objects, otherwise the duplicate check above verified nothing",
                objects.isEmpty());
    }
}
