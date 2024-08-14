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

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.pdfbox.cos.COSBase;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.cos.COSObject;
import org.apache.pdfbox.pdmodel.interactive.form.PDAcroForm;

import org.apache.fop.pdf.PDFArray;
import org.apache.fop.pdf.PDFDictionary;
import org.apache.fop.pdf.PDFDocument;

public class MergeAnnotations implements HandleAnnotations<Object> {
    private PDFBoxAdapter pdfBoxAdapter;
    private Map<String, Object> fields;

    public MergeAnnotations(PDFBoxAdapter pdfBoxAdapter, Map<String, Object> fields) {
        this.pdfBoxAdapter = pdfBoxAdapter;
        this.fields = fields;
    }

    public Set<Object> getFields() {
        return new HashSet<>(fields.values());
    }

    public void load(COSObject annot, PDAcroForm srcAcroForm) {
        //not used
    }

    public void cloneAnnotParent(COSBase annot, PDFDictionary clonedAnnot, Collection<COSName> exclude)
        throws IOException {
        if (clonedAnnot.containsKey("T")) {
            List<String> nameList = new ArrayList<>();
            addToTree(clonedAnnot, nameList);
            PDFDictionary node = (PDFDictionary) fields.get(nameList.remove(0));
            while (!nameList.isEmpty()) {
                String name = nameList.remove(0);
                PDFDictionary nextNode = findKid(name, node);
                if (nextNode == null) {
                    if (nameList.isEmpty()) {
                        nextNode = node;
                    } else {
                        nextNode = new PDFDictionary();
                        pdfBoxAdapter.pdfDoc.registerTrailerObject(nextNode);
                        nextNode.put("Kids", new PDFArray());
                        nextNode.put("T", name);
                        nextNode.put(COSName.PARENT.getName(), node);
                        PDFArray kids = (PDFArray) node.get("Kids");
                        kids.add(nextNode);
                    }
                }
                node = nextNode;
            }
            if (node != clonedAnnot) {
                insert(node, clonedAnnot);
            }
        }
    }

    private void insert(PDFDictionary parent, PDFDictionary clonedAnnot) throws IOException {
        if (parent.containsKey("Kids")) {
            PDFArray kids = (PDFArray) parent.get("Kids");
            kids.add(clonedAnnot);
        } else {
            PDFDictionary grandParent = (PDFDictionary) parent.get(COSName.PARENT.getName());
            PDFDictionary child = parent;
            parent = new PDFDictionary();
            if (grandParent != null) {
                PDFArray kids = (PDFArray) grandParent.get("Kids");
                kids.add(parent);
            }
            pdfBoxAdapter.pdfDoc.registerTrailerObject(parent);
            List<String> excludeCopy = Arrays.asList("Subtype", "Type", "Rect", "MK", "F");
            for (String key : child.keySet()) {
                if (!excludeCopy.contains(key)) {
                    parent.put(key, child.get(key));
                }
            }
            child.remove("T");
            child.put(COSName.PARENT.getName(), parent);
            parent.put("Kids", new PDFArray(clonedAnnot, child));

        }
        if (getT(clonedAnnot).equals(getT(parent))) {
            clonedAnnot.remove("T");
        }
        clonedAnnot.put(COSName.PARENT.getName(), parent);
    }

    private PDFDictionary findKid(String name, PDFDictionary node) throws IOException {
        PDFArray kids = (PDFArray) node.get("Kids");
        for (int i = 0; i < kids.length(); i++) {
            PDFDictionary kid = (PDFDictionary) kids.get(i);
            if (name.equals(getT(kid))) {
                return kid;
            }
        }
        return null;
    }

    private void addToTree(PDFDictionary clonedAnnot, List<String> nameList) throws IOException {
        String tStr = getT(clonedAnnot);
        nameList.add(0, tStr);
        Object parent = clonedAnnot.get(COSName.PARENT.getName());
        if (parent instanceof PDFDictionary) {
            addToTree((PDFDictionary) parent, nameList);
        } else if (!fields.containsKey(tStr)) {
            fields.put(tStr, clonedAnnot);
        }
    }

    private String getT(PDFDictionary clonedAnnot) throws IOException {
        Object tStr = clonedAnnot.get("T");
        if (tStr instanceof byte[]) {
            tStr = new String((byte[]) tStr, PDFDocument.ENCODING);
        }
        return (String) tStr;
    }
}
