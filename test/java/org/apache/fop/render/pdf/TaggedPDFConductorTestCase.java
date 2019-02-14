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

import java.awt.geom.Rectangle2D;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;

import org.apache.fop.pdf.PDFArray;
import org.apache.fop.pdf.PDFDictionary;
import org.apache.fop.pdf.PDFDocument;
import org.apache.fop.pdf.PDFName;
import org.apache.fop.pdf.PDFNumber;
import org.apache.fop.pdf.PDFObject;
import org.apache.fop.pdf.PDFPage;
import org.apache.fop.pdf.PDFResources;
import org.apache.fop.pdf.PDFStructElem;
import org.apache.fop.render.pdf.pdfbox.PDFBoxAdapter;
import org.apache.fop.render.pdf.pdfbox.TaggedPDFConductor;



public class TaggedPDFConductorTestCase {
    private static final String LINK = "test/resources/linkTagged.pdf";
    private static final String HELLO = "test/resources/helloWorld.pdf";
    private static final String TABLE = "test/resources/emptyRowTable.pdf";
    private static final String OTF = "test/resources/otf.pdf";
    private static final String IMAGE = "test/resources/hello2.pdf";
    private static final String NOPARENTTREE = "test/resources/NoParentTree.pdf";
    private PDFPage pdfPage;
    private PDFDocument pdfDoc;

    @Test
    public void testHandleLogicalStructure() throws IOException {
        PDFStructElem elem = new PDFStructElem();
        runConductor(LINK, elem);
        checkStructure(elem, 0);

        elem = new PDFStructElem();
        runConductor(HELLO, elem);
        PDFNumber mcid = (PDFNumber)elem.getKids().get(0);
        int test = mcid.getNumber().intValue();
        Assert.assertEquals(test, 0);

        elem = new PDFStructElem();
        runConductor(TABLE, elem);
        Assert.assertEquals(print(elem), "/Div/Part/Sect/Table/TBody/TR/TD/P/TD/P/TR/TD/TD");

        elem = new PDFStructElem();
        elem.put("Alt", "alt-text");
        runConductor(OTF, elem);
        Assert.assertEquals(print(elem), "/Div/Part/Art/P/Span");
        Assert.assertNull(elem.get("Alt"));
    }

    private String print(PDFStructElem x) throws IOException {
        StringBuilder sb = new StringBuilder(x.get("S").toString());
        if (x.getKids() != null) {
            for (PDFObject k : x.getKids()) {
                if (k instanceof PDFStructElem) {
                    sb.append(print((PDFStructElem) k));
                }
            }
            return sb.toString();
        }
        return "";
    }

    private void runConductor(String pdf, PDFStructElem elem) throws IOException {
        setUp();
        PDDocument doc = PDDocument.load(new File(pdf));
        PDPage srcPage = doc.getPage(0);
        elem.setObjectNumber(2);
        PDFBoxAdapter adapter = new PDFBoxAdapter(
                pdfPage, new HashMap(),  new HashMap<Integer, PDFArray>());
        PDFLogicalStructureHandler handler = setUpPDFLogicalStructureHandler();
        new TaggedPDFConductor(elem, handler, srcPage, adapter).handleLogicalStructure(doc);
    }

    private void setUp() {
        Rectangle2D r = new Rectangle2D.Double();
        pdfPage = new PDFPage(new PDFResources(pdfDoc), 0, r, r, r, r);
        pdfDoc = new PDFDocument(" ");
        pdfDoc.makeStructTreeRoot(null);
        pdfPage.setObjectNumber(1);
        pdfPage.setDocument(pdfDoc);
    }

    private PDFLogicalStructureHandler setUpPDFLogicalStructureHandler() {
        PDFLogicalStructureHandler handler = new PDFLogicalStructureHandler(pdfDoc);
        handler.getParentTree().setDocument(pdfDoc);
        handler.startPage(pdfPage);
        return handler;
    }

    private void checkStructure(PDFStructElem elem, int index) {
        String [] types = {"Part", "Sect", "P"};
        List<PDFObject> list = elem.getKids();
        if (index != 3) {
            PDFStructElem kid = (PDFStructElem)list.get(0);
            String test = ((PDFName)kid.get("S")).getName();
            String expected = types[index];
            Assert.assertEquals(test, expected);
            index++;
            checkStructure(kid, index);
        } else {
            PDFDictionary firstKid = (PDFDictionary) list.get(0);
            int test = ((PDFNumber)firstKid.get("MCID")).getNumber().intValue();
            int expected = 0;
            Assert.assertEquals(test, expected);
            PDFDictionary firstKidSibling = (PDFDictionary) list.get(2);
            test = ((PDFNumber)firstKidSibling.get("MCID")).getNumber().intValue();
            expected = 2;
            Assert.assertEquals(test, expected);
            PDFStructElem second = (PDFStructElem)list.get(1);
            List secondKids = second.getKids();
            PDFStructElem secKid = (PDFStructElem) secondKids.get(0);
            List secondKidKids = secKid.getKids();
            PDFDictionary leafElem = (PDFDictionary)secondKidKids.get(0);
            test = ((PDFNumber)leafElem.get("MCID")).getNumber().intValue();
            expected = 1;
            Assert.assertEquals(test, expected);
        }
    }

    @Test
    public void testTaggedImagePDF() throws IOException {
        PDFStructElem elem = new PDFStructElem();
        runConductor(IMAGE, elem);
        Assert.assertEquals(print(elem), "/Div/Part/Sect/P/Image");
    }

    @Test
    public void testCreateDirectDescendants() throws IOException {
        PDFStructElem elem = new PDFStructElem();
        runConductor(NOPARENTTREE, elem);
        Assert.assertEquals(print(elem), "/Div/Document");
    }
}
