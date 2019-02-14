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
import org.junit.Before;
import org.junit.Test;

import org.apache.pdfbox.cos.COSArray;
import org.apache.pdfbox.cos.COSDictionary;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.cos.COSObject;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;

import org.apache.fop.pdf.PDFArray;
import org.apache.fop.pdf.PDFDictionary;
import org.apache.fop.pdf.PDFDocument;
import org.apache.fop.pdf.PDFName;
import org.apache.fop.pdf.PDFNumber;
import org.apache.fop.pdf.PDFObject;
import org.apache.fop.pdf.PDFPage;
import org.apache.fop.pdf.PDFReference;
import org.apache.fop.pdf.PDFResources;
import org.apache.fop.pdf.PDFStructElem;

import org.apache.fop.render.pdf.pdfbox.PDFBoxAdapter;
import org.apache.fop.render.pdf.pdfbox.PageParentTreeFinder;
import org.apache.fop.render.pdf.pdfbox.StructureTreeMerger;



public class StructureTreeMergerTestCase {
    private static final String LINK = "test/resources/linkTagged.pdf";
    private static final String BrokenLink = "test/resources/brokenLink.pdf";
    private static final String MissingOBJR = "test/resources/missingOBJR.pdf";
    private PDFPage pdfPage;
    private PDFDocument pdfDoc;
    private PDFBoxAdapter adapter;

    @Test
    public void testCopyStructure() throws IOException {
        PDDocument doc = PDDocument.load(new File(LINK));
        PDPage srcPage = doc.getPage(0);
        PageParentTreeFinder finder = new PageParentTreeFinder(srcPage);
        COSArray markedContentParents = finder.getPageParentTreeArray(doc);
        PDFStructElem elem = new PDFStructElem();
        elem.setObjectNumber(2);
        adapter = new PDFBoxAdapter(pdfPage, new HashMap(), new HashMap<Integer, PDFArray>());
        adapter.setCurrentMCID(1);
        PDFLogicalStructureHandler handler = setUpPDFLogicalStructureHandler();
        StructureTreeMerger merger = new StructureTreeMerger(elem, handler, adapter, srcPage);
        merger.copyStructure(markedContentParents);
        PDFArray array = handler.getPageParentTree();
        checkMarkedContentsParentsForLinkTest(array);
        PDFStructElem first = (PDFStructElem)array.get(0);
        checkParentForLinkTest(first, 0);
    }

    @Test
    public void testNullEntriesInParentTree() throws IOException {
        PDDocument doc = PDDocument.load(new File(LINK));
        PDPage srcPage = doc.getPage(0);
        PageParentTreeFinder finder = new PageParentTreeFinder(srcPage);
        COSArray markedContentParents = finder.getPageParentTreeArray(doc);
        markedContentParents.add(0, null);
        PDFStructElem elem = new PDFStructElem();
        elem.setObjectNumber(2);
        adapter = new PDFBoxAdapter(pdfPage, new HashMap(), new HashMap<Integer, PDFArray>());
        PDFLogicalStructureHandler handler = setUpPDFLogicalStructureHandler();
        StructureTreeMerger merger = new StructureTreeMerger(elem, handler, adapter, srcPage);
        merger.copyStructure(markedContentParents);
        PDFArray array = handler.getPageParentTree();
        Assert.assertNull(array.get(0));
    }

    @Test
    public void testOBJRCorrectPosition() throws IOException {
        PDDocument doc = PDDocument.load(new File(MissingOBJR));
        PDPage srcPage = doc.getPage(0);
        PageParentTreeFinder finder = new PageParentTreeFinder(srcPage);
        COSArray markedContentParents = finder.getPageParentTreeArray(doc);
        PDFStructElem elem = new PDFStructElem();
        elem.setObjectNumber(2);
        adapter = new PDFBoxAdapter(pdfPage, new HashMap(), new HashMap<Integer, PDFArray>());
        PDFLogicalStructureHandler handler = setUpPDFLogicalStructureHandler();
        StructureTreeMerger merger = new StructureTreeMerger(elem, handler, adapter, srcPage);
        merger.copyStructure(markedContentParents);
//        PDFArray array = handler.getPageParentTree();

//        PDFStructElem kid = (PDFStructElem)array.get(0);
//        PDFReference reference = (PDFReference) kid.get("P");
//        PDFStructElem parent = (PDFStructElem)reference.getObject();
//        List<PDFObject> kids = parent.getKids();
//        PDFDictionary first = (PDFDictionary) kids.get(0);

//        Assert.assertEquals(first.get("Type").toString(), "/OBJR");
//        PDFDictionary last = (PDFDictionary) kids.get(2);
//        Assert.assertEquals(last.get("Type").toString(), "/OBJR");

//        PDFStructElem middle = (PDFStructElem) kids.get(1);
//        Assert.assertEquals(middle.get("Type").toString(), "/StructElem");
    }

    private void checkMarkedContentsParentsForLinkTest(PDFArray array) {
        PDFStructElem first = (PDFStructElem)array.get(0);
        List firstKids = first.getKids();
        PDFDictionary firstKid = (PDFDictionary) firstKids.get(0);
        int test = ((PDFNumber)firstKid.get("MCID")).getNumber().intValue();
        int expected = 1;
        Assert.assertEquals(test, expected);
        PDFDictionary firstKidSibling = (PDFDictionary) firstKids.get(2);
        test = ((PDFNumber)firstKidSibling.get("MCID")).getNumber().intValue();
        expected = 3;
        Assert.assertEquals(test, expected);
        PDFStructElem second = (PDFStructElem)array.get(1);
        List secondKids = second.getKids();
        PDFDictionary secKid = (PDFDictionary) secondKids.get(0);
        test = ((PDFNumber)secKid.get("MCID")).getNumber().intValue();
        expected = 2;
        Assert.assertEquals(test, expected);
    }

    private void checkParentForLinkTest(PDFStructElem elem, int index) {
        String [] types = {"Sect", "Part"};
        PDFStructElem parent = (PDFStructElem)((PDFReference)elem.get("P")).getObject();
        if (index != 2) {
            String test = ((PDFName)parent.get("S")).getName();
            String expected = types[index];
            Assert.assertEquals(test, expected);
            index++;
            checkParentForLinkTest(parent, index);
        }
    }

    @Before
    public void setUp() {
        Rectangle2D r = new Rectangle2D.Double();
        pdfDoc = new PDFDocument(" ");
        pdfPage = new PDFPage(new PDFResources(pdfDoc), 0, r, r, r, r);
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

    @Test
    public void testCheckNullCOSObject() throws IOException {
        PDDocument doc = PDDocument.load(new File(BrokenLink));
        PDPage srcPage = doc.getPage(0);
        PageParentTreeFinder finder = new PageParentTreeFinder(srcPage);
        COSArray markedContentParents = finder.getPageParentTreeArray(doc);
        COSObject nullObj = new COSObject(null);
        nullObj.setObjectNumber(100);
        nullObj.setGenerationNumber(0);
        PDFStructElem elem = new PDFStructElem();
        elem.setObjectNumber(2);
        COSObject parent = (COSObject)markedContentParents.get(1);
        COSArray kids = (COSArray) parent.getDictionaryObject(COSName.K);
        COSDictionary kid = (COSDictionary) kids.get(1);
        kid.setItem(COSName.OBJ, nullObj);
        adapter = new PDFBoxAdapter(pdfPage, new HashMap(), new HashMap<Integer, PDFArray>());
        PDFLogicalStructureHandler handler = setUpPDFLogicalStructureHandler();
        StructureTreeMerger merger = new StructureTreeMerger(elem, handler, adapter, srcPage);
        merger.copyStructure(markedContentParents);
        PDFArray array = handler.getPageParentTree();
        PDFStructElem parentElem = (PDFStructElem)array.get(1);
        PDFDictionary objrDict = (PDFDictionary) parentElem.getKids().get(1);
        Assert.assertNull(objrDict.get("Obj"));
    }

    @Test
    public void testDirectDescedants() throws IOException {
        PDFStructElem elem = new PDFStructElem();
        elem.setObjectNumber(100);
        adapter = new PDFBoxAdapter(pdfPage, new HashMap(), new HashMap<Integer, PDFArray>());
        PDFLogicalStructureHandler handler = setUpPDFLogicalStructureHandler();
        PDPage srcPage = new PDPage();
        StructureTreeMerger merger = new StructureTreeMerger(elem, handler, adapter, srcPage);
        COSArray array = new COSArray();
        COSDictionary dict = new COSDictionary();
        dict.setItem(COSName.S, COSName.P);
        COSObject obj = new COSObject(dict);
        obj.setObjectNumber(200);
        obj.setGenerationNumber(0);
        array.add(0, obj);
        merger.createDirectDescendants(array, elem);
        List<PDFObject> list = elem.getKids();
        PDFStructElem kid = (PDFStructElem)list.get(0);
        PDFName name = (PDFName)kid.get("S");
        String test = name.getName();
        Assert.assertEquals(test, "P");
    }

    @Test
    public void testEmptyDict() throws IOException {
        adapter = new PDFBoxAdapter(pdfPage, new HashMap(), new HashMap<Integer, PDFArray>());
        StructureTreeMerger structureTreeMerger = new StructureTreeMerger(null, null, adapter, null);
        COSArray cosArray = new COSArray();
        COSObject o = new COSObject(new COSDictionary());
        o.setObjectNumber(1);
        o.setGenerationNumber(1);
        cosArray.add(o);
        structureTreeMerger.copyStructure(cosArray);
        structureTreeMerger.addToPageParentTreeArray();
    }
}
