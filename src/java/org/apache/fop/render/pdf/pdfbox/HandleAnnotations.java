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
import java.util.Collection;
import java.util.Set;

import org.apache.pdfbox.cos.COSBase;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.cos.COSObject;
import org.apache.pdfbox.pdmodel.interactive.form.PDAcroForm;

import org.apache.fop.pdf.PDFDictionary;

public interface HandleAnnotations<T> {
    /*
     * @return Set of fields
     */
    Set<T> getFields();

    /*
     * Load annotations and add to fields set
     */
    void load(COSObject annot, PDAcroForm srcAcroForm);

    /*
     * Clone annotation and set parent
     */
    void cloneAnnotParent(COSBase annot, PDFDictionary clonedAnnot, Collection<COSName> exclude) throws IOException;
}
