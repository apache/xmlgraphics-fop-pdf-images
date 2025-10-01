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

import java.io.IOException;

import org.junit.Assert;
import org.junit.Test;

import org.apache.pdfbox.cos.COSArray;
import org.apache.pdfbox.cos.COSDictionary;
import org.apache.pdfbox.cos.COSInteger;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.cos.COSNull;
import org.apache.pdfbox.cos.COSObject;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.common.PDNumberTreeNode;

public class PageParentTreeFinderTestCase {
    private static final String LINK = "linkTagged.pdf";

    @Test
    public void testGetPageParentTreeArray() throws IOException {
        try (PDDocument doc = PDFBoxAdapterTestCase.load(LINK)) {
            PDPage srcPage = doc.getPage(0);
            PageParentTreeFinder finder = new PageParentTreeFinder(srcPage);
            COSArray markedContentParents = finder.getPageParentTreeArray(doc);
            Assert.assertEquals(markedContentParents.size(), 3);
            COSObject firstObj = (COSObject) markedContentParents.get(0);
            COSObject secObj = (COSObject) markedContentParents.get(1);
            COSDictionary firstObjDict = (COSDictionary) firstObj.getObject();
            COSArray firstKids = (COSArray) firstObjDict.getDictionaryObject(COSName.K);
            COSDictionary firstKid = (COSDictionary) firstKids.get(0);
            int test = firstKid.getInt("MCID");
            int expected = 0;
            Assert.assertEquals(test, expected);
            COSDictionary firstKidBrother = (COSDictionary) firstKids.get(2);
            test = firstKidBrother.getInt("MCID");
            expected = 2;
            Assert.assertEquals(test, expected);
            COSDictionary secObjDict = (COSDictionary) secObj.getObject();
            COSArray secKidsArray = (COSArray) secObjDict.getDictionaryObject(COSName.K);
            COSDictionary secondKid = (COSDictionary) secKidsArray.get(0);
            test = secondKid.getInt("MCID");
            expected = 1;
            Assert.assertEquals(test, expected);
        }
    }

    @Test
    public void testNoparentTreePresent() throws IOException {
        PDPage srcPage = new PDPage();
        srcPage.getCOSObject().setItem(COSName.STRUCT_PARENTS, COSInteger.get(-1));
        PDResources res = new PDResources();
        srcPage.setResources(res);
        PageParentTreeFinder finder = new PageParentTreeFinder(srcPage);
        COSArray parentTree = finder.getPageParentTreeArray(null);
        int test = parentTree.size();
        Assert.assertEquals(test, 0);
    }

    @Test
    public void testTraverseKids() throws IOException {
        try (PDDocument doc = PDFBoxAdapterTestCase.load(LINK)) {
            PDNumberTreeNode srcNumberTreeNode = doc.getDocumentCatalog().getStructureTreeRoot().getParentTree();
            COSArray parentTree = (COSArray) srcNumberTreeNode.getCOSObject().getDictionaryObject(COSName.KIDS);
            COSObject kidCOSObj = (COSObject) parentTree.get(0);
            COSDictionary dict = (COSDictionary) kidCOSObj.getObject();
            COSArray nums = (COSArray) dict.getDictionaryObject(COSName.NUMS);
            nums.add(0, COSInteger.get(9));
            nums.add(1, new COSDictionary());
            COSArray numList = new PageParentTreeFinder(doc.getPage(0)).getPageParentTreeArray(doc);
            Assert.assertEquals(numList.size(), 3);
        }
    }

    @Test
    public void testCOSNull() throws IOException {
        try (PDDocument doc = PDFBoxAdapterTestCase.load(LINK)) {
            PDNumberTreeNode srcNumberTreeNode = doc.getDocumentCatalog().getStructureTreeRoot().getParentTree();
            COSArray parentTree = (COSArray) srcNumberTreeNode.getCOSObject().getDictionaryObject(COSName.KIDS);
            COSObject kidCOSObj = (COSObject) parentTree.get(0);
            COSDictionary dict = (COSDictionary) kidCOSObj.getObject();
            COSArray nums = (COSArray) dict.getDictionaryObject(COSName.NUMS);
            nums.add(0, COSInteger.ZERO);
            nums.add(1, new COSObject(COSNull.NULL));
            COSArray numList = new PageParentTreeFinder(doc.getPage(0)).getPageParentTreeArray(doc);
            Assert.assertEquals(numList.size(), 3);
        }
    }
}
