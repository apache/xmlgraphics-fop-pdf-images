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

import java.awt.geom.Rectangle2D;
import java.io.IOException;
import java.util.HashMap;

import org.junit.Assert;
import org.junit.Test;

import org.apache.pdfbox.cos.COSArray;
import org.apache.pdfbox.cos.COSDictionary;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.cos.COSObject;
import org.apache.pdfbox.cos.COSStream;
import org.apache.pdfbox.cos.COSString;

import org.apache.fop.pdf.PDFDocument;
import org.apache.fop.pdf.PDFPage;
import org.apache.fop.pdf.PDFResources;

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
        PDFBoxAdapter adapter = new PDFBoxAdapter(page, new HashMap<>(), null, new HashMap<>());
        String cloned = (String) new PDFCloner(adapter).cloneForNewDocument(string);
        Assert.assertArrayEquals(cloned.getBytes(PDFDocument.ENCODING), string.getBytes());
    }
}
