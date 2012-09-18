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

import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.io.IOUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.pdfbox.cos.COSArray;
import org.apache.pdfbox.cos.COSBase;
import org.apache.pdfbox.cos.COSBoolean;
import org.apache.pdfbox.cos.COSDictionary;
import org.apache.pdfbox.cos.COSFloat;
import org.apache.pdfbox.cos.COSInteger;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.cos.COSNull;
import org.apache.pdfbox.cos.COSObject;
import org.apache.pdfbox.cos.COSStream;
import org.apache.pdfbox.cos.COSString;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentCatalog;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageNode;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.common.COSObjectable;
import org.apache.pdfbox.pdmodel.common.COSStreamArray;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.common.PDStream;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotation;
import org.apache.pdfbox.pdmodel.interactive.form.PDAcroForm;

import org.apache.fop.pdf.PDFArray;
import org.apache.fop.pdf.PDFDictionary;
import org.apache.fop.pdf.PDFDocument;
import org.apache.fop.pdf.PDFFormXObject;
import org.apache.fop.pdf.PDFName;
import org.apache.fop.pdf.PDFNumber;
import org.apache.fop.pdf.PDFObject;
import org.apache.fop.pdf.PDFPage;
import org.apache.fop.pdf.PDFRoot;
import org.apache.fop.pdf.PDFStream;

/**
 * This class provides an adapter for transferring content from a PDFBox PDDocument to
 * FOP's PDFDocument. It is used to parse PDF using PDFBox and write content using
 * FOP's PDF library.
 */
class PDFBoxAdapter {

    /** logging instance */
    protected static Log log = LogFactory.getLog(PDFBoxAdapter.class);

    private static final Set filterFilter = new java.util.HashSet(
            Arrays.asList(new String[] {"Filter", "DecodeParms"}));
    private static final Set page2form = new java.util.HashSet(
            Arrays.asList(new String[] {"Group", "LastModified", "Metadata"}));

    private final PDFPage targetPage;
    private final PDFDocument pdfDoc;

    private final Map clonedVersion;

    /**
     * Creates a new PDFBoxAdapter.
     * @param targetPage The target FOP PDF page object
     * @param objectCache the object cache for reusing objects shared by multiple pages.
     */
    public PDFBoxAdapter(PDFPage targetPage, Map objectCache) {
        this.targetPage = targetPage;
        this.pdfDoc = this.targetPage.getDocument();
        this.clonedVersion = objectCache;
    }

    private Object cloneForNewDocument(Object base) throws IOException {
        return cloneForNewDocument(base, base);
    }

    private Object cloneForNewDocument(Object base, Object keyBase) throws IOException {
        return cloneForNewDocument(base, keyBase, Collections.EMPTY_LIST);
    }

    private Object cloneForNewDocument(Object base, Object keyBase, Collection exclude)
            throws IOException {
        if (base == null) {
            return null;
        }
        Object cached = getCachedClone(keyBase);
        if (cached != null) {
            // we are done, it has already been converted.
            return cached;
        } else if (base instanceof List) {
            PDFArray array = new PDFArray();
            cacheClonedObject(keyBase, array);
            List list = (List)base;
            for (int i = 0; i < list.size(); i++) {
                array.add(cloneForNewDocument(list.get(i)));
            }
            return array;
        } else if (base instanceof COSObjectable && !(base instanceof COSBase)) {
            Object o = ((COSObjectable)base).getCOSObject();
            Object retval = cloneForNewDocument(o, o, exclude);
            return cacheClonedObject(keyBase, retval);
        } else if (base instanceof COSObject) {
            COSObject object = (COSObject)base;
            if (log.isTraceEnabled()) {
                log.trace("Cloning indirect object: "
                        + object.getObjectNumber().longValue()
                        + " " + object.getGenerationNumber().longValue());
            }
            Object obj = cloneForNewDocument(object.getObject(), object);
            if (obj instanceof PDFObject) {
                PDFObject pdfobj = (PDFObject)obj;
                //pdfDoc.registerObject(pdfobj);
                if (!pdfobj.hasObjectNumber()) {
                    throw new IllegalStateException("PDF object was not registered!");
                }
                if (log.isTraceEnabled()) {
                    log.trace("Object registered: "
                            + pdfobj.getObjectNumber()
                            + " " + pdfobj.getGeneration()
                            + " for COSObject: "
                            + object.getObjectNumber().longValue()
                            + " " + object.getGenerationNumber().longValue());
                }
            }
            return obj;
        } else if (base instanceof COSArray) {
            PDFArray newArray = new PDFArray();
            cacheClonedObject(keyBase, newArray);
            COSArray array = (COSArray)base;
            for (int i = 0; i < array.size(); i++) {
                newArray.add(cloneForNewDocument(array.get(i)));
            }
            return newArray;
        } else if (base instanceof COSStreamArray) {
            COSStreamArray array = (COSStreamArray)base;
            PDFArray newArray = new PDFArray();
            cacheClonedObject(keyBase, newArray);
            for (int i = 0, c = array.getStreamCount(); i < c; i++) {
                newArray.add(cloneForNewDocument(array.get(i)));
            }
            return newArray;
        } else if (base instanceof COSStream) {
            COSStream originalStream = (COSStream)base;

            PDFStream stream = new PDFStream();

            InputStream in;
            Set filter;
            if (pdfDoc.isEncryptionActive()) {
                in = originalStream.getUnfilteredStream();
                filter = filterFilter;
            } else {
                //transfer encoded data (don't reencode)
                in = originalStream.getFilteredStream();
                filter = Collections.EMPTY_SET;
            }
            OutputStream out = stream.getBufferOutputStream();
            IOUtils.copyLarge(in, out);
            transferDict(originalStream, stream, filter);
            return cacheClonedObject(keyBase, stream);
        } else if (base instanceof COSDictionary) {
            COSDictionary dic = (COSDictionary)base;
            PDFDictionary newDict = new PDFDictionary();
            cacheClonedObject(keyBase, newDict);
            Iterator iter = dic.keySet().iterator();
            while (iter.hasNext()) {
                COSName key = (COSName)iter.next();
                if (!exclude.contains(key)) {
                    (newDict).put(key.getName(), cloneForNewDocument(dic.getItem(key)));
                }
            }
            return newDict;
        } else if (base instanceof COSName) {
            PDFName newName = new PDFName(((COSName)base).getName());
            return cacheClonedObject(keyBase, newName);
        } else if (base instanceof COSInteger) {
            PDFNumber number = new PDFNumber();
            number.setNumber(new Long(((COSInteger)base).longValue()));
            return cacheClonedObject(keyBase, number);
        } else if (base instanceof COSFloat) {
            PDFNumber number = new PDFNumber();
            number.setNumber(new Float(((COSFloat)base).floatValue()));
            return cacheClonedObject(keyBase, number);
        } else if (base instanceof COSBoolean) {
            //TODO Do we need a PDFBoolean here?
            Boolean retval = ((COSBoolean)base).getValueAsObject();
            if (keyBase instanceof COSObject) {
                return cacheClonedObject(keyBase, new PDFBoolean(retval.booleanValue()));
            } else {
                return cacheClonedObject(keyBase, retval);
            }
        } else if (base instanceof COSString) {
            COSString string = (COSString)base;
            //retval = ((COSString)base).getString(); //this is unsafe for binary content
            byte[] bytes = string.getBytes();
            //Be on the safe side and use the byte array to avoid encoding problems
            //as PDFBox doesn't indicate whether the string is just
            //a string (PDF 1.4, 3.2.3) or a text string (PDF 1.4, 3.8.1).
            if (keyBase instanceof COSObject) {
                return cacheClonedObject(keyBase, new PDFString(bytes));
            } else {
                if (PDFString.isUSASCII(bytes)) {
                    return cacheClonedObject(keyBase, string.getString());
                } else {
                    return cacheClonedObject(keyBase, bytes);
                }
            }
        } else if (base instanceof COSNull) {
            return cacheClonedObject(keyBase, null);
        } else {
            throw new UnsupportedOperationException("NYI: " + base.getClass().getName());
        }
    }

    private Object getCachedClone(Object base) {
        return clonedVersion.get(getBaseKey(base));
    }

    private Object cacheClonedObject(Object base, Object cloned) {
        Object key = getBaseKey(base);
        if (base instanceof COSObject) {
            PDFObject pdfobj = (PDFObject)cloned;
            if (!pdfobj.hasObjectNumber()) {
                pdfDoc.registerObject(pdfobj);
                if (log.isTraceEnabled()) {
                    log.trace(key + ": " + pdfobj.getClass().getName() + " registered as "
                            + pdfobj.getObjectNumber() + " " + pdfobj.getGeneration());
                }
            }
        } else if (log.isTraceEnabled()) {
            //TODO PDFDictionary.toString() may cause stack overflows
            //log.trace(key + ": cached, direct object");
        }
        clonedVersion.put(key, cloned);
        return cloned;
    }

    private Object getBaseKey(Object base) {
        if (base instanceof COSObject) {
            COSObject obj = (COSObject)base;
            return obj.getObjectNumber().intValue()
                    + " " + obj.getGenerationNumber().intValue();
        } else {
            return base;
        }
    }

    private void transferDict(COSDictionary orgDict, PDFStream targetDict,
            Set filter) throws IOException {
        transferDict(orgDict, targetDict, filter, false);
    }

    private void transferDict(COSDictionary orgDict, PDFStream targetDict,
            Set filter, boolean inclusive) throws IOException {
        Iterator iter = orgDict.keySet().iterator();
        while (iter.hasNext()) {
            COSName key = (COSName)iter.next();
            if (inclusive && !filter.contains(key.getName())) {
                continue;
            } else if (!inclusive && filter.contains(key.getName())) {
                continue;
            }
            targetDict.put(key.getName(),
                    cloneForNewDocument(orgDict.getItem(key)));
        }
    }

    /**
     * Creates a PDFFormXObject (from FOP's PDF library) from a PDF page parsed with PDFBox.
     * @param sourceDoc the source PDF the given page to be copied belongs to
     * @param page the page to transform into a Form XObject
     * @param key value to use as key for the Form XObject
     * @return the Form XObject
     * @throws IOException if an I/O error occurs
     */
    public PDFFormXObject createFormFromPDFBoxPage(PDDocument sourceDoc, PDPage page, String key)
            throws IOException {
        handleAcroForm(sourceDoc, page);

        PDResources sourcePageResources = page.findResources();
        PDFDictionary pageResources = null;
        if (sourcePageResources != null) {
            pageResources = (PDFDictionary)cloneForNewDocument(
                    sourcePageResources.getCOSDictionary());
            pdfDoc.registerObject(pageResources);
        }

        COSStream originalPageContents = null;
        PDStream pdStream = page.getContents();
        if (pdStream != null) {
            originalPageContents = (COSStream)pdStream.getCOSObject();
        }

        PDFStream pageStream;
        Set filter;
        if (originalPageContents instanceof COSStreamArray) {
            COSStreamArray array = (COSStreamArray)originalPageContents;
            pageStream = new PDFStream();
            InputStream in = array.getUnfilteredStream();
            OutputStream out = pageStream.getBufferOutputStream();
            IOUtils.copyLarge(in, out);
            filter = filterFilter;
        } else {
            pageStream = (PDFStream)cloneForNewDocument(originalPageContents);
            filter = Collections.EMPTY_SET;
        }
        if (pageStream == null) {
            pageStream = new PDFStream();
        }

        PDFFormXObject form;

        form = pdfDoc.addFormXObject(null, pageStream,
                (pageResources != null ? pageResources.makeReference() : null), key);
        if (originalPageContents != null) {
            transferDict(originalPageContents, pageStream, filter);
        }
        transferDict(page.getCOSDictionary(), pageStream, page2form, true);
        AffineTransform at = form.getMatrix();
        PDRectangle mediaBox = page.findMediaBox();
        PDRectangle cropBox = page.findCropBox();
        PDRectangle viewBox = (cropBox != null ? cropBox : mediaBox);

        //Handle the /Rotation entry on the page dict
        int rotation = PDFUtil.getNormalizedRotation(page);

        //Transform to FOP's user space
        at.scale(1 / viewBox.getWidth(), 1 / viewBox.getHeight());
        switch (rotation) {
        case 90:
            at.scale(viewBox.getWidth() / viewBox.getHeight(), viewBox.getHeight() / viewBox.getWidth());
            at.translate(0, viewBox.getWidth());
            at.rotate(-Math.PI / 2.0);
            break;
        case 180:
            at.translate(viewBox.getWidth(), viewBox.getHeight());
            at.rotate(-Math.PI);
            break;
        case 270:
            at.scale(viewBox.getWidth() / viewBox.getHeight(), viewBox.getHeight() / viewBox.getWidth());
            at.translate(viewBox.getHeight(), 0);
            at.rotate(-Math.PI * 1.5);
        default:
            //no additional transformations necessary
        }
        //Compensate for Crop Boxes not starting at 0,0
        at.translate(-viewBox.getLowerLeftX(), -viewBox.getLowerLeftY());
        form.setMatrix(at);

        form.setBBox(new Rectangle2D.Float(
                viewBox.getLowerLeftX(), viewBox.getLowerLeftY(),
                viewBox.getUpperRightX(), viewBox.getUpperRightY()));
        return form;
    }

    private List getWidgets(PDPage page) throws IOException {
        List annots = page.getAnnotations();
        List widgets = new java.util.ArrayList();
        Iterator iter = annots.iterator();
        while (iter.hasNext()) {
            PDAnnotation annot = (PDAnnotation)iter.next();
            if (annot.getSubtype().equals("Widget")) {
                widgets.add(annot);
            }
        }
        return widgets;
    }

    private void handleAcroForm(PDDocument sourceDoc, PDPage page) throws IOException {
        PDDocumentCatalog srcCatalog = sourceDoc.getDocumentCatalog();
        PDAcroForm srcAcroForm = srcCatalog.getAcroForm();
        List pageWidgets = getWidgets(page);
        if (srcAcroForm == null && pageWidgets.isEmpty()) {
            return;
        }

        //Pseudo-cache the target page in place of the original source page.
        //This essentially replaces the original page reference with the target page.
        COSObject cosPage = null;
        if (page.getCOSObject() instanceof COSObject) {
            cosPage = (COSObject)page.getCOSObject();
        } else {
            PDPageNode pageNode = page.getParent();

            COSArray kids = (COSArray)pageNode.getDictionary().getDictionaryObject(COSName.KIDS);
            Iterator iter = kids.iterator();
            while (iter.hasNext()) {
                //Hopefully safe to cast, as kids need to be indirect objects
                COSObject kid = (COSObject)iter.next();
                if (kid.getObject() == page.getCOSObject()) {
                    cosPage = kid;
                    break;
                }
            }
            if (cosPage == null) {
                throw new IOException("Illegal PDF. Page not part of parent page node.");
            }
        }
        cacheClonedObject(cosPage, this.targetPage);

        COSArray annots = (COSArray)page.getCOSDictionary().getDictionaryObject(COSName.ANNOTS);
        Set fields = Collections.EMPTY_SET;
        if (annots != null) {
            fields = new java.util.HashSet();
            Iterator iter = annots.iterator();
            while (iter.hasNext()) {
                COSBase annot = (COSBase)iter.next();
                PDFObject clonedAnnot;
                //Exclude the parent of the annotation from being duplicated
                COSObject cosAnnot = (COSObject)annot.getCOSObject();
                COSDictionary field = (COSDictionary)(cosAnnot).getObject();
                if (!"Widget".equals(field.getNameAsString(COSName.SUBTYPE))) {
                    continue; //not a widget
                }
                COSDictionary parent;
                while ((parent = (COSDictionary)field.getDictionaryObject(COSName.PARENT))
                        != null) {
                    field = parent;
                }
                fields.add(field);
                Collection exclude = Collections.singletonList(COSName.P);
                clonedAnnot = (PDFObject)cloneForNewDocument(
                        (cosAnnot).getObject(), cosAnnot, exclude);
                targetPage.addAnnotation(clonedAnnot);
            }
        }

        boolean formAlreadyCopied = (getCachedClone(srcAcroForm) != null);
        PDFRoot catalog = this.pdfDoc.getRoot();
        PDFDictionary destAcroForm = (PDFDictionary)catalog.get(COSName.ACRO_FORM.getName());
        if (formAlreadyCopied) {
            //skip, already copied
        } else if (destAcroForm == null) {
            if (srcAcroForm != null) {
                //With this, only the first PDF's AcroForm is copied over. If later AcroForms have
                //different properties besides the actual fields, these get lost. Only fields
                //get merged.
                Collection exclude = Collections.singletonList(COSName.FIELDS);
                destAcroForm = (PDFDictionary)cloneForNewDocument(srcAcroForm, srcAcroForm, exclude);
            } else {
                //Work-around for incorrectly split PDFs which lack an AcroForm but have widgets
                //on pages. This doesn't handle the case where field dicts have "C" entries
                //(for the "CO" entry), so this may produce problems, but we have almost no chance
                //to guess the calculation order.
                destAcroForm = new PDFDictionary(pdfDoc.getRoot());
            }
            pdfDoc.registerObject(destAcroForm);
            catalog.put(COSName.ACRO_FORM.getName(), destAcroForm);
        }

        //Merge fields
        PDFArray clonedFields = (PDFArray)destAcroForm.get(COSName.FIELDS.getName());
        if (clonedFields == null) {
            //No cloned AcroForm, yet
            clonedFields = new PDFArray();
            destAcroForm.put(COSName.FIELDS.getName(), clonedFields);
        }
        for (Iterator iter = fields.iterator(); iter.hasNext();) {
            //Add collected fields to existing cloned AcroForm
            COSDictionary field = (COSDictionary)iter.next();
            PDFDictionary clone = (PDFDictionary)cloneForNewDocument(field);
            pdfDoc.registerObject(clone);
            clonedFields.add(clone);
        }
    }

}
