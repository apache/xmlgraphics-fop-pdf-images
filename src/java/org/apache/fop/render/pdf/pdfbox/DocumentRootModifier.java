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

import org.apache.pdfbox.cos.COSDictionary;
import org.apache.pdfbox.cos.COSName;

import org.apache.fop.pdf.PDFDictionary;
import org.apache.fop.pdf.PDFDocument;
import org.apache.fop.pdf.PDFStructTreeRoot;

public class DocumentRootModifier {

    private PDFBoxAdapter adapter;
    private PDFDocument pdfDoc;

    public DocumentRootModifier(PDFBoxAdapter adapter, PDFDocument pdfDoc) {
        this.adapter = adapter;
        this.pdfDoc = pdfDoc;
    }

    public void structTreeRootEntriesToCopy(COSDictionary structRootDict) throws IOException {
        checkForMap(structRootDict, COSName.ROLE_MAP.getName());
        checkForMap(structRootDict, "ClassMap");
    }

    private void checkForMap(COSDictionary structRootDict, String mapName) throws IOException {
        if (structRootDict.containsKey(mapName)) {
            COSDictionary addedMapDict = (COSDictionary)structRootDict.getDictionaryObject(mapName);
            PDFDictionary temp = (PDFDictionary) adapter.cloneForNewDocument(addedMapDict);
            PDFStructTreeRoot structTreeRoot = pdfDoc.getRoot().getStructTreeRoot();
            if (!structTreeRoot.containsKey(mapName)) {
                structTreeRoot.put(mapName, temp);
            } else {
                PDFDictionary rootMap = (PDFDictionary)structTreeRoot.get(mapName);
                addMapToStructTreeRoot(rootMap, temp, mapName);
            }
        }
    }

    private void mergeMapClass(PDFDictionary main, PDFDictionary addition) {
        for (String key : addition.keySet()) {
            main.put(key, addition.get(key));
        }
    }

    private void mergeRoleMaps(PDFDictionary structRoot, PDFDictionary addition) {
        for (String key : addition.keySet()) {
            if (!structRoot.containsKey(key)) {
                structRoot.put(key, addition.get(key));
            }
        }
    }

    private void addMapToStructTreeRoot(PDFDictionary rootMap, PDFDictionary map, String mapName) {
        if (mapName.equals(COSName.ROLE_MAP.getName())) {
            mergeRoleMaps(rootMap, map);
        } else {
            mergeMapClass(rootMap, map);
        }
    }
}
