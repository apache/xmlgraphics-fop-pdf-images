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

import org.apache.pdfbox.cos.COSArray;
import org.apache.pdfbox.cos.COSDictionary;
import org.apache.pdfbox.cos.COSName;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.documentinterchange.logicalstructure.PDMarkInfo;

import org.apache.fop.pdf.PDFDocument;
import org.apache.fop.pdf.PDFName;
import org.apache.fop.pdf.PDFPage;
import org.apache.fop.pdf.PDFStructElem;
import org.apache.fop.render.pdf.PDFLogicalStructureHandler;

public class TaggedPDFConductor {

    private PDPage srcPage;
    private PDFPage targetPage;
    private DocumentRootModifier rootMod;
    private StructureTreeMerger merger;

    public TaggedPDFConductor(PDFStructElem currentSessionElem,
                              PDFLogicalStructureHandler logicalStructHandler, PDPage srcPage,
                              PDFBoxAdapter adapter) {
        this.srcPage = srcPage;
        this.targetPage = adapter.getTargetPage();
        PDFDocument pdfDoc = targetPage.getDocument();
        this.rootMod = new DocumentRootModifier(adapter, pdfDoc);
        merger = new StructureTreeMerger(currentSessionElem, logicalStructHandler, adapter, srcPage);
    }

    public void handleLogicalStructure(PDDocument srcDoc) throws IOException {
        if (isInputPDFTagged(srcDoc) && isStructureTreeRootNull(srcDoc)) {
            merger.setCurrentSessionElem();
            COSDictionary strucRootDict = srcDoc.getDocumentCatalog().getStructureTreeRoot()
                .getCOSObject();
            rootMod.structTreeRootEntriesToCopy(strucRootDict);
            if (!isParentTreeIsPresent(strucRootDict)) {
                merger.createDirectDescendants(strucRootDict, merger.currentSessionElem);
            } else {
                PageParentTreeFinder markedContentsParentFinder = new PageParentTreeFinder(srcPage);
                COSArray markedContentsParents = markedContentsParentFinder.getPageParentTreeArray(srcDoc);
                COSDictionary roleMap = (COSDictionary)strucRootDict.getDictionaryObject(COSName.ROLE_MAP);
                if (roleMap != null) {
                    merger.setRoleMap(roleMap);
                }
                merger.copyStructure(markedContentsParents);
            }
        }
        configureCurrentSessionElem(srcDoc);
    }

    private void configureCurrentSessionElem(PDDocument srcDoc) {
        if (!(isInputPDFTagged(srcDoc) && isStructureTreeRootNull(srcDoc))) {
            merger.setCurrentSessionElemKid();
            merger.currentSessionElem.put(COSName.PG.getName(), targetPage.makeReference());
        } else {
            merger.currentSessionElem.put("S", new PDFName("Div"));
            merger.currentSessionElem.remove("Alt");
        }
    }
    private boolean isInputPDFTagged(PDDocument srcDoc) {
        PDMarkInfo mark = srcDoc.getDocumentCatalog().getMarkInfo();
        return mark != null && mark.isMarked();
    }

    private boolean isStructureTreeRootNull(PDDocument srcDoc) {
        return srcDoc.getDocumentCatalog().getStructureTreeRoot() != null;
    }

    private boolean isParentTreeIsPresent(COSDictionary strucRootDict) {
        return strucRootDict.keySet().contains(COSName.PARENT_TREE);
    }
}
