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

import org.junit.Assert;
import org.junit.Test;

import org.apache.pdfbox.cos.COSDictionary;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;

import org.apache.fop.pdf.PDFArray;
import org.apache.fop.pdf.PDFDictionary;
import org.apache.fop.pdf.PDFDocument;
import org.apache.fop.pdf.PDFName;
import org.apache.fop.pdf.PDFPage;
import org.apache.fop.pdf.PDFResources;
import org.apache.fop.pdf.PDFStructTreeRoot;

import org.apache.fop.render.pdf.pdfbox.DocumentRootModifier;
import org.apache.fop.render.pdf.pdfbox.PDFBoxAdapter;



public class DocumentRootModifierTestCase {
    private static final String CLASSMAP = "test/resources/classMap.pdf";

    @Test
    public void testStructTreeRootEntriesToCopy() throws IOException {
        Rectangle2D r = new Rectangle2D.Double();
        PDFDocument pdfDoc = new PDFDocument("");
        PDFPage page = new PDFPage(new PDFResources(pdfDoc), 0, r, r, r, r);
        page.setObjectNumber(1);
        page.setDocument(pdfDoc);
        pdfDoc.makeStructTreeRoot(null);
        PDFStructTreeRoot structTreeRoot = pdfDoc.getRoot().getStructTreeRoot();
        PDFDictionary rootBaseRoleMap = new PDFDictionary();
        PDFBoxAdapter adapter = new PDFBoxAdapter(page, new HashMap(),  new HashMap<Integer, PDFArray>());
        DocumentRootModifier modifier = new DocumentRootModifier(adapter, pdfDoc);
        COSDictionary root = new COSDictionary();
        COSDictionary mapRole = new COSDictionary();
        mapRole.setName("Icon", "Figure");
        root.setItem(COSName.ROLE_MAP, mapRole);
        modifier.structTreeRootEntriesToCopy(root);
        structTreeRoot = pdfDoc.getRoot().getStructTreeRoot();
        PDFDictionary baseRoot = (PDFDictionary) structTreeRoot.get("RoleMap");
        String test = baseRoot.get("Icon").toString();
        String expected = "/Figure";
        Assert.assertEquals(test, expected);

        PDFName para = new PDFName("P");
        rootBaseRoleMap.put("MyPara", para);
        structTreeRoot.put("RoleMap", rootBaseRoleMap);
        modifier.structTreeRootEntriesToCopy(root);
        structTreeRoot = pdfDoc.getRoot().getStructTreeRoot();
        PDFDictionary baseRoot2 = (PDFDictionary) structTreeRoot.get("RoleMap");
        PDFName nameIcon = (PDFName) baseRoot2.get("Icon");
        PDFName myPara = (PDFName)baseRoot2.get("MyPara");
        test = nameIcon.getName();
        expected = "Figure";
        Assert.assertEquals(test, expected);
        test = myPara.getName();
        expected = "P";
        Assert.assertEquals(test, expected);


        PDDocument doc = PDDocument.load(new File(CLASSMAP));
        COSDictionary temp = (COSDictionary)doc.getDocumentCatalog().getStructureTreeRoot().getCOSObject();
        PDFDictionary classMap = new PDFDictionary();
        PDFDictionary inner = new PDFDictionary();
        inner.put("StartIndent", 0);
        classMap.put("Normal2", inner);
        structTreeRoot.put("ClassMap", classMap);
        modifier.structTreeRootEntriesToCopy(temp);
        structTreeRoot = pdfDoc.getRoot().getStructTreeRoot();
        PDFDictionary testDict = (PDFDictionary)structTreeRoot.get("ClassMap");
        Assert.assertNotNull(testDict.get("Normal2"));
    }

}

