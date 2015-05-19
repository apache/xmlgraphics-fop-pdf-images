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
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;



import org.junit.Test;

import org.apache.pdfbox.cos.COSArray;
import org.apache.pdfbox.cos.COSDictionary;
import org.apache.pdfbox.cos.COSInteger;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.cos.COSObject;


import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;

import org.apache.fop.pdf.PDFArray;
import org.apache.fop.pdf.PDFDictionary;
import org.apache.fop.pdf.PDFDocument;
import org.apache.fop.pdf.PDFName;
import org.apache.fop.pdf.PDFNumber;
import org.apache.fop.pdf.PDFPage;
import org.apache.fop.pdf.PDFReference;
import org.apache.fop.pdf.PDFResources;
import org.apache.fop.pdf.PDFStructElem;

import org.apache.fop.render.pdf.pdfbox.PDFBoxAdapter;
import org.apache.fop.render.pdf.pdfbox.PageParentTreeFinder;
import org.apache.fop.render.pdf.pdfbox.StructureTreeMerger;

import junit.framework.Assert;

public class StructureTreeMergerTestCase {
    private static final String LINK = "test/resources/linkTagged.pdf";
    private static final String NoParentTree = "test/resources/NoParentTree.pdf";
    private static final String BrokenLink = "test/resources/brokenLink.pdf";
    private PDFPage pdfPage;
    private PDFDocument pdfDoc;
    private PDFBoxAdapter adapter;

    @Test
    public void testCopyStructure() throws IOException {
        setUp();
        PDDocument doc = PDDocument.load(LINK);
        PDPage srcPage = doc.getPage(0);
        PageParentTreeFinder finder = new PageParentTreeFinder(srcPage);
        COSArray markedContentParents = finder.getPageParentTreeArray(doc);
        PDFStructElem elem = new PDFStructElem();
        elem.setObjectNumber(2);
        adapter = new PDFBoxAdapter(pdfPage, new HashMap(), new HashMap<Integer, PDFArray>());
        PDFLogicalStructureHandler handler = setUpPDFLogicalStructureHandler();
        StructureTreeMerger merger = new StructureTreeMerger(elem, handler, adapter, srcPage);
        merger.copyStructure(markedContentParents);
        PDFArray array = handler.getPageParentTree();
        checkMarkedContentsParentsForLinkTest(array);
        PDFStructElem first = (PDFStructElem)array.get(0);
        checkParentForLinkTest(first, 0);
    }

    @Test
    public void testCreateDirectDescendants() throws IOException {
        setUp();
        PDDocument doc = PDDocument.load(NoParentTree);
        PDPage srcPage = doc.getPage(0);
        PDFStructElem elem = new PDFStructElem();
        elem.setObjectNumber(2);
        adapter = new PDFBoxAdapter(pdfPage, new HashMap(), new HashMap<Integer, PDFArray>());
        PDFLogicalStructureHandler handler = setUpPDFLogicalStructureHandler();
        StructureTreeMerger merger = new StructureTreeMerger(elem, handler, adapter, srcPage);
        COSDictionary strucRootDict = (COSDictionary)doc.getDocumentCatalog().getStructureTreeRoot()
            .getCOSObject();
        merger.createDirectDescendants(strucRootDict, elem);
        checkNoParentTree(elem, 0);
    }

    private void checkMarkedContentsParentsForLinkTest(PDFArray array) {
        PDFStructElem first = (PDFStructElem)array.get(0);
        List firstKids = first.getKids();
        PDFDictionary firstKid = (PDFDictionary) firstKids.get(0);
        int test = ((PDFNumber)firstKid.get("MCID")).getNumber().intValue();
        int expected = 0;
        Assert.assertEquals(test, expected);
        PDFDictionary firstKidSibling = (PDFDictionary) firstKids.get(2);
        test = ((PDFNumber)firstKidSibling.get("MCID")).getNumber().intValue();
        expected = 2;
        Assert.assertEquals(test, expected);
        PDFStructElem second = (PDFStructElem)array.get(1);
        List secondKids = second.getKids();
        PDFDictionary secKid = (PDFDictionary) secondKids.get(0);
        test = ((PDFNumber)secKid.get("MCID")).getNumber().intValue();
        expected = 1;
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

    private void checkNoParentTree(PDFStructElem elem, int index) {
        String [] types = {"Document", "Part"};
        if (index != 2) {
            PDFStructElem kid = (PDFStructElem)elem.getKids().get(0);
            String test = ((PDFName)kid.get("S")).getName();
            String expected = types[index];
            Assert.assertEquals(test, expected);
            index++;
            checkNoParentTree(kid, index);
        }
    }


    @Test
    public void testNullEntriesInParentTree() throws IOException {
        setUp();
        PDDocument doc = PDDocument.load(LINK);
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
    public void checkNullCOSObject() throws IOException {
        setUp();
        PDDocument doc = PDDocument.load(BrokenLink);
        PDPage srcPage = doc.getPage(0);
        PageParentTreeFinder finder = new PageParentTreeFinder(srcPage);
        COSArray markedContentParents = finder.getPageParentTreeArray(doc);
        COSObject nullObj = new COSObject(null);
        nullObj.setObjectNumber(COSInteger.get(100));
        nullObj.setGenerationNumber(COSInteger.ZERO);
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
}
