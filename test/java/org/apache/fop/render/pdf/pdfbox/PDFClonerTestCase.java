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

import java.awt.Rectangle;
import java.awt.geom.Rectangle2D;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Assert;
import org.junit.Test;

import org.apache.pdfbox.cos.COSArray;
import org.apache.pdfbox.cos.COSDictionary;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.cos.COSObject;
import org.apache.pdfbox.cos.COSStream;
import org.apache.pdfbox.cos.COSString;

import org.apache.fop.pdf.PDFArray;
import org.apache.fop.pdf.PDFDocument;
import org.apache.fop.pdf.PDFPage;
import org.apache.fop.pdf.PDFResources;
import org.apache.fop.pdf.PDFStream;

public class PDFClonerTestCase {
    @Test
    public void testCompareTree() throws IOException {
        Assert.assertEquals(PDFBoxAdapterUtil.getBaseKey(makeTree(2)),
                PDFBoxAdapterUtil.getBaseKey(makeTree(1)));
    }

    private COSDictionary makeTree(long objNumber) throws IOException {
        COSStream stream = new COSStream();
        COSObject obj = new COSObject(stream);
        obj.setObjectNumber(objNumber);
        COSArray array = new COSArray();
        array.add(obj);
        COSDictionary root = new COSDictionary();
        root.setItem(COSName.C, array);
        return root;
    }

    @Test
    public void testString() throws IOException {
        COSString string = new COSString(new byte[]{(byte) 127, (byte) 127});
        PDFDocument doc = new PDFDocument("");
        Rectangle2D r = new Rectangle2D.Double();
        PDFPage page = new PDFPage(new PDFResources(doc), 0, r, r, r, r);
        page.setDocument(doc);
        PDFBoxAdapter adapter = new PDFBoxAdapter(page, new HashMap<>(), new HashMap<Integer, PDFArray>());
        String cloned = (String) new PDFCloner(adapter, false).cloneForNewDocument(string);
        Assert.assertArrayEquals(cloned.getBytes(PDFDocument.ENCODING), string.getBytes());
    }

    @Test
    public void testStream() throws IOException {
        PDFDocument doc = new PDFDocument("");
        Rectangle2D rectangle = new Rectangle2D.Double();
        PDFPage page = new PDFPage(new PDFResources(doc), 0, rectangle, rectangle, rectangle, rectangle);
        page.setDocument(doc);
        PDFBoxAdapter adapter = new PDFBoxAdapter(page, new HashMap<>(), new HashMap<Integer, PDFArray>());
        COSDictionary res = new COSDictionary();
        COSDictionary child = new COSDictionary();
        child.setBoolean("a", true);
        res.setItem("a", child);
        Rectangle rect = new Rectangle(0, 0, 100, 100);
        List<COSName> patternNames = new ArrayList<>();
        adapter.uniqueName = new UniqueName("a", res, patternNames, false, rect);
        PDFStream cloneda = (PDFStream) new PDFCloner(adapter, false).cloneForNewDocument(getStream());
        adapter.uniqueName = new UniqueName("b", res, patternNames, false, rect);
        PDFStream clonedb = (PDFStream) new PDFCloner(adapter, false).cloneForNewDocument(getStream());
        Assert.assertNotSame(cloneda, clonedb);
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        cloneda.setDocument(doc);
        clonedb.setDocument(doc);
        setFilterMap(doc);
        cloneda.output(bos);
        clonedb.output(bos);
        Assert.assertEquals(bos.toString(PDFDocument.ENCODING), "<< /Length 1 0 R /Subtype /Form >>\n"
                + "stream\n"
                + "/a97 tf\n\n"
                + "endstream"
                + "<< /Length 2 0 R /Subtype /Form >>\n"
                + "stream\n"
                + "/a98 tf\n\n"
                + "endstream");
    }

    private void setFilterMap(PDFDocument doc) {
        Map<String, List<String>> filterMap = new HashMap<>();
        List<String> filterList = new ArrayList<>();
        filterList.add("null");
        filterMap.put("default", filterList);
        doc.setFilterMap(filterMap);
    }

    private COSStream getStream() throws IOException {
        COSStream stream = new COSStream();
        stream.setItem(COSName.SUBTYPE, COSName.FORM);
        try (OutputStream os = stream.createUnfilteredStream()) {
            os.write("/a tf".getBytes(PDFDocument.ENCODING));
        }
        return stream;
    }
}
