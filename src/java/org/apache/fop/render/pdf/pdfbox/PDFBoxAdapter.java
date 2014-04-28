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

import java.awt.Rectangle;
import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
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
import org.apache.pdfbox.pdfparser.PDFStreamParser;
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

import org.apache.fop.events.EventBroadcaster;
import org.apache.fop.pdf.PDFArray;
import org.apache.fop.pdf.PDFDictionary;
import org.apache.fop.pdf.PDFDocument;
import org.apache.fop.pdf.PDFName;
import org.apache.fop.pdf.PDFNumber;
import org.apache.fop.pdf.PDFObject;
import org.apache.fop.pdf.PDFPage;
import org.apache.fop.pdf.PDFRoot;
import org.apache.fop.pdf.PDFStream;
import org.apache.pdfbox.util.PDFOperator;

/**
 * This class provides an adapter for transferring content from a PDFBox PDDocument to
 * FOP's PDFDocument. It is used to parse PDF using PDFBox and write content using
 * FOP's PDF library.
 */
public class PDFBoxAdapter {

    /** logging instance */
    protected static Log log = LogFactory.getLog(PDFBoxAdapter.class);

    private static final Set filterFilter = new java.util.HashSet(
            Arrays.asList(new String[] {"Filter", "DecodeParms"}));

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
                array.add(cloneForNewDocument(list.get(i), list.get(i), exclude));
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
            Object obj = cloneForNewDocument(object.getObject(), object, exclude);
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
                newArray.add(cloneForNewDocument(array.get(i), array.get(i), exclude));
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
            PDFStream stream = new PDFStream();
            OutputStream out = stream.getBufferOutputStream();
            IOUtils.copyLarge(in, out);
            transferDict(originalStream, stream, filter);
            return cacheClonedObject(keyBase, stream);
        } else if (base instanceof COSDictionary) {
            COSDictionary dic = (COSDictionary)base;
            List keys = dic.keyList();
            PDFDictionary newDict = new PDFDictionary();
            cacheClonedObject(keyBase, newDict);
            for (int i = 0; i < keys.size(); i++) {
                COSName key = (COSName)keys.get(i);
                if (!exclude.contains(key)) {
                    (newDict).put(key.getName(), cloneForNewDocument(dic.getItem(key), dic.getItem(key), exclude));
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
        if (key == null) {
            return cloned;
        }
        PDFObject pdfobj = (PDFObject) cloned;
        if (!pdfobj.hasObjectNumber()) {
            pdfDoc.registerObject(pdfobj);
            if (log.isTraceEnabled()) {
                log.trace(key + ": " + pdfobj.getClass().getName() + " registered as "
                            + pdfobj.getObjectNumber() + " " + pdfobj.getGeneration());
            }
        }
        clonedVersion.put(key, cloned);
        return cloned;
    }

    private Object getBaseKey(Object base) {
        if (base instanceof COSObject) {
            COSObject obj = (COSObject)base;
            return obj.getObjectNumber().intValue() + " " + obj.getGenerationNumber().intValue();
        } else {
            return null;
        }
    }

    private void transferDict(COSDictionary orgDict, PDFStream targetDict,
            Set filter) throws IOException {
        transferDict(orgDict, targetDict, filter, false);
    }

    private void transferDict(COSDictionary orgDict, PDFStream targetDict,
            Set filter, boolean inclusive) throws IOException {
        List keys = orgDict.keyList();
        for (int i = 0, ci = keys.size(); i < ci; i++) {
            COSName key = (COSName)keys.get(i);
            if (inclusive && !filter.contains(key.getName())) {
                continue;
            } else if (!inclusive && filter.contains(key.getName())) {
                continue;
            }
            targetDict.put(key.getName(),
                    cloneForNewDocument(orgDict.getItem(key)));
        }
    }

    public class PDFWriter {
        protected StringBuilder s = new StringBuilder();
        private String key;
        private List<COSName> resourceNames;

        public PDFWriter(String key, List<COSName> resourceNames) {
            this.key = key;
            this.resourceNames = resourceNames;
        }

        public String writeText(PDStream pdStream) throws IOException {
            Iterator<Object> it = new PDFStreamParser(pdStream).getTokenIterator();
            List<COSBase> arguments = new ArrayList<COSBase>();
            while (it.hasNext()) {
                Object o = it.next();
                if (o instanceof PDFOperator) {
                    PDFOperator op = (PDFOperator)o;
                    readPDFArguments(op, arguments);
                    s.append(op.getOperation() + "\n");
                    arguments.clear();
                    if (op.getImageParameters() != null) {
                        for (Map.Entry<COSName, COSBase> cn : op.getImageParameters().getDictionary().entrySet()) {
                            arguments.add(cn.getKey());
                            arguments.add(cn.getValue());
                        }
                        readPDFArguments(op, arguments);
                        s.append("ID " + new String(op.getImageData(), "ISO-8859-1"));
                        arguments.clear();
                        s.append("EI\n");
                    }
                } else {
                    arguments.add((COSBase)o);
                }
            }
            return s.toString();
        }

        protected void readPDFArguments(PDFOperator op, Collection<COSBase> arguments) throws IOException {
            for (COSBase c : arguments) {
                processArg(op, c);
            }
        }

        protected void processArg(PDFOperator op, COSBase c) throws IOException {
            if (c instanceof COSInteger) {
                s.append(((COSInteger) c).intValue());
                s.append(" ");
            } else if (c instanceof COSFloat) {
                float f = ((COSFloat) c).floatValue();
                s.append(new DecimalFormat("#.####").format(f));
                s.append(" ");
            } else if (c instanceof COSName) {
                COSName cn = (COSName)c;
                s.append("/" + cn.getName());
                addKey(cn);
                s.append(" ");
            } else if (c instanceof COSString) {
                s.append("<" + ((COSString) c).getHexString() + ">");
            } else if (c instanceof COSArray) {
                s.append("[");
                readPDFArguments(op, (Collection<COSBase>) ((COSArray) c).toList());
                s.append("] ");
            } else if (c instanceof COSDictionary) {
                Collection<COSBase> dictArgs = new ArrayList<COSBase>();
                for (Map.Entry<COSName, COSBase> cn : ((COSDictionary)c).entrySet()) {
                    dictArgs.add(cn.getKey());
                    dictArgs.add(cn.getValue());
                }
                s.append("<<");
                readPDFArguments(op, dictArgs);
                s.append(">>");
            } else {
                throw new IOException(c + " not supported");
            }
        }

        protected void addKey(COSName cn) {
            if (resourceNames.contains(cn)) {
                s.append(key);
            }
        }
    }
    /**
     * Creates a stream (from FOP's PDF library) from a PDF page parsed with PDFBox.
     * @param sourceDoc the source PDF the given page to be copied belongs to
     * @param page the page to transform into a stream
     * @param key value to use as key for the stream
     * @param atdoc adjustment for stream
     * @return the stream
     * @throws IOException if an I/O error occurs
     */
    public String createStreamFromPDFBoxPage(PDDocument sourceDoc, PDPage page, String key,
            EventBroadcaster eventBroadcaster, AffineTransform atdoc, Rectangle pos) throws IOException {
        handleAcroForm(sourceDoc, page, eventBroadcaster, atdoc);

        PDResources sourcePageResources = page.findResources();
        PDFDictionary pageResources = null;
        PDStream pdStream = page.getContents();
        COSDictionary fonts = (COSDictionary)sourcePageResources.getCOSDictionary().getDictionaryObject(COSName.FONT);
        String uniqueName = Integer.toString(key.hashCode());
        String newStream = new PDFWriter(uniqueName, getResourceNames(sourcePageResources.getCOSDictionary())).writeText(pdStream);
        pdStream = new PDStream(sourceDoc, new ByteArrayInputStream(newStream.getBytes("ISO-8859-1")));
        pageResources = (PDFDictionary)cloneForNewDocument(
                sourcePageResources.getCOSDictionary());
        pdfDoc.registerObject(pageResources);

        COSStream originalPageContents = (COSStream)pdStream.getCOSObject();

        bindOptionalContent(sourceDoc);

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
        if (originalPageContents != null) {
            transferDict(originalPageContents, pageStream, filter);
        }

        transferPageDict(fonts, uniqueName, sourcePageResources);

        PDRectangle mediaBox = page.findMediaBox();
        PDRectangle cropBox = page.findCropBox();
        PDRectangle viewBox = (cropBox != null ? cropBox : mediaBox);

        //Handle the /Rotation entry on the page dict
        int rotation = PDFUtil.getNormalizedRotation(page);

        //Transform to FOP's user space
        float w = (float)pos.getWidth() / 1000f;
        float h = (float)pos.getHeight() / 1000f;
        if (rotation == 90 || rotation == 270) {
            float tmp = w;
            w = h;
            h = tmp;
        }
        atdoc.setTransform(AffineTransform.getScaleInstance(w / viewBox.getWidth(), h / viewBox.getHeight()));
        atdoc.translate(0, viewBox.getHeight());
        atdoc.rotate(-Math.PI);
        atdoc.scale(-1, 1);
        atdoc.translate(-viewBox.getLowerLeftX(), -viewBox.getLowerLeftY());

        switch (rotation) {
            case 90:
                atdoc.scale(viewBox.getWidth() / viewBox.getHeight(), viewBox.getHeight() / viewBox.getWidth());
                atdoc.translate(0, viewBox.getWidth());
                atdoc.rotate(-Math.PI / 2.0);
                atdoc.scale(viewBox.getWidth() / viewBox.getHeight(), viewBox.getHeight() / viewBox.getWidth());
                break;
            case 180:
                atdoc.translate(viewBox.getWidth(), viewBox.getHeight());
                atdoc.rotate(-Math.PI);
                break;
            case 270:
                atdoc.translate(0, viewBox.getHeight());
                atdoc.rotate(Math.toRadians(270 + 180));
                atdoc.translate(-viewBox.getWidth(), -viewBox.getHeight());
                break;
            default:
                //no additional transformations necessary
                break;
        }
        StringBuffer boxStr = new StringBuffer();
        boxStr.append(0).append(' ').append(0).append(' ');
        boxStr.append(PDFNumber.doubleOut(mediaBox.getWidth())).append(' ');
        boxStr.append(PDFNumber.doubleOut(mediaBox.getHeight())).append(" re W n\n");
        return boxStr.toString() + pdStream.getInputStreamAsString();
    }

    private List<COSName> getResourceNames(COSDictionary sourcePageResources) {
        List<COSName> resourceNames = new ArrayList<COSName>();
        for (COSBase e : sourcePageResources.getValues()) {
            if (e instanceof COSObject) {
                e = ((COSObject) e).getObject();
            }
            if (e instanceof COSDictionary) {
                COSDictionary d = (COSDictionary) e;
                resourceNames.addAll(d.keySet());
            }
        }
        return resourceNames;
    }

    private void transferPageDict(COSDictionary fonts, String uniqueName, PDResources sourcePageResources) throws IOException {
        if (fonts != null) {
            for (Map.Entry<COSName, COSBase> f : fonts.entrySet()) {
                String name = f.getKey().getName() + uniqueName;
                targetPage.getPDFResources().addFont(name, (PDFDictionary)cloneForNewDocument(f.getValue()));
            }
        }
        for (Map.Entry<COSName, COSBase> e : sourcePageResources.getCOSDictionary().entrySet()) {
            transferDict(e, uniqueName);
        }
    }

    private void transferDict(Map.Entry<COSName, COSBase> dict, String uniqueName) throws IOException {
        COSBase src;
        if (dict.getValue() instanceof COSObject) {
            src = ((COSObject) dict.getValue()).getObject();
        } else {
            src = dict.getValue();
        }
        if (dict.getKey() != COSName.FONT && src instanceof COSDictionary) {
            String name = dict.getKey().getName();
            PDFDictionary newDict = (PDFDictionary) targetPage.getPDFResources().get(name);
            if (newDict == null) {
                newDict = new PDFDictionary(targetPage.getPDFResources());
            }
            COSDictionary srcDict = (COSDictionary) src;
            for (Map.Entry<COSName, COSBase> v : srcDict.entrySet()) {
                newDict.put(v.getKey().getName() + uniqueName, cloneForNewDocument(v.getValue()));
            }
            targetPage.getPDFResources().put(name, newDict);
        }
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

    private void bindOptionalContent(PDDocument sourceDoc) throws IOException {
        /*
         * PDOptionalContentProperties ocProperties =
         * sourceDoc.getDocumentCatalog().getOCProperties(); PDFDictionary ocDictionary =
         * (PDFDictionary) cloneForNewDocument(ocProperties); if (ocDictionary != null) {
         * this.pdfDoc.getRoot().put(COSName.OCPROPERTIES.getName(), ocDictionary); }
         */
    }

    private void handleAcroForm(PDDocument sourceDoc, PDPage page,
            EventBroadcaster eventBroadcaster, AffineTransform at) throws IOException {
        PDDocumentCatalog srcCatalog = sourceDoc.getDocumentCatalog();
        PDAcroForm srcAcroForm = srcCatalog.getAcroForm();
        List pageAnnotations = page.getAnnotations();
        if (srcAcroForm == null && pageAnnotations.isEmpty()) {
            return;
        }

        PDRectangle mediaBox = page.findMediaBox();
        PDRectangle cropBox = page.findCropBox();
        PDRectangle viewBox = (cropBox != null ? cropBox : mediaBox);

        for (Object obj : pageAnnotations) {
            PDAnnotation annot = (PDAnnotation)obj;
            PDRectangle rect = annot.getRectangle();
            rect.move((float)(at.getTranslateX() - viewBox.getLowerLeftX()),
                    (float)at.getTranslateY() - viewBox.getLowerLeftY());
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
        Set fields = Collections.emptySet();
        if (annots != null) {
            fields = new HashSet();
            Iterator iter = annots.iterator();
            while (iter.hasNext()) {
                COSObject annot = (COSObject) iter.next();
                COSObject fieldObject = annot;
                COSDictionary field = (COSDictionary) fieldObject.getObject();
                if ("Widget".equals(field.getNameAsString(COSName.SUBTYPE))) {
                    COSObject parent;
                    while ((parent = (COSObject) field.getItem(COSName.PARENT)) != null) {
                        fieldObject = parent;
                        field = (COSDictionary) fieldObject.getObject();
                    }
                    fields.add(fieldObject);
                    Collection<COSName> exclude = new ArrayList<COSName>();
                    exclude.add(COSName.P);
                    if (((COSDictionary)annot.getObject()).getItem(COSName.getPDFName("StructParent")) != null) {
                        exclude.add(COSName.PARENT);
                    }
                    PDFObject clonedAnnot = (PDFObject) cloneForNewDocument(annot, annot, exclude);
                    targetPage.addAnnotation(clonedAnnot);
                }
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
            catalog.put(COSName.ACRO_FORM.getName(), destAcroForm );
        }
        PDFArray clonedFields = (PDFArray) destAcroForm.get(COSName.FIELDS.getName());
        if (clonedFields == null) {
            clonedFields = new PDFArray();
            destAcroForm.put(COSName.FIELDS.getName(), clonedFields);
        }
        for (Iterator iter = fields.iterator(); iter.hasNext();) {
            COSObject field = (COSObject) iter.next();
            PDFDictionary clone = (PDFDictionary) cloneForNewDocument(field, field, Arrays.asList(COSName.KIDS));
            clonedFields.add(clone);
        }
    }

}
