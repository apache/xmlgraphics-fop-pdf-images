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
import java.io.Serializable;
import java.util.Collection;
import java.util.Comparator;
import java.util.Set;
import java.util.TreeSet;

import org.apache.pdfbox.cos.COSArray;
import org.apache.pdfbox.cos.COSBase;
import org.apache.pdfbox.cos.COSDictionary;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.cos.COSObject;
import org.apache.pdfbox.pdmodel.interactive.form.PDAcroForm;

import org.apache.fop.pdf.PDFDictionary;

public class CloneAnnotations implements HandleAnnotations<COSObject> {
    private PDFBoxAdapter pdfBoxAdapter;
    private Set<COSObject> fields = new TreeSet<>(new CompareFields());

    public CloneAnnotations(PDFBoxAdapter pdfBoxAdapter) {
        this.pdfBoxAdapter = pdfBoxAdapter;
    }

    public Set<COSObject> getFields() {
        return fields;
    }

    public void load(COSObject annot, PDAcroForm srcAcroForm) {
        getField(annot, srcAcroForm);
    }

    private COSDictionary getField(COSObject fieldObject, PDAcroForm srcAcroForm) {
        COSDictionary field = (COSDictionary) fieldObject.getObject();
        COSObject parent;
        while ((parent = getParent(field)) != null) {
            fieldObject = parent;
            field = (COSDictionary) fieldObject.getObject();
        }
        if (srcAcroForm != null) {
            COSArray srcFields = (COSArray) srcAcroForm.getCOSObject().getDictionaryObject(COSName.FIELDS);
            if (srcFields != null && srcFields.toList().contains(fieldObject)) {
                fields.add(fieldObject);
            }
        } else {
            fields.add(fieldObject);
        }
        return field;
    }

    private COSObject getParent(COSDictionary field) {
        COSBase parent = field.getItem(COSName.PARENT);
        if (parent instanceof COSObject) {
            return (COSObject) parent;
        }
        return null;
    }

    public void cloneAnnotParent(COSBase annot, PDFDictionary clonedAnnot, Collection<COSName> exclude)
        throws IOException {
        if (annot instanceof COSObject) {
            COSDictionary dictionary = (COSDictionary) ((COSObject) annot).getObject();
            COSBase parent = dictionary.getItem(COSName.PARENT);
            if (parent != null) {
                clonedAnnot.put(COSName.PARENT.getName(), pdfBoxAdapter.cloneForNewDocument(parent, parent, exclude));
            }
        }
    }

    static class CompareFields implements Comparator<COSObject>, Serializable {
        private static final long serialVersionUID = -6081505461660440801L;

        public int compare(COSObject o1, COSObject o2) {
            return (int) (o1.getObjectNumber() - o2.getObjectNumber());
        }
    }
}
