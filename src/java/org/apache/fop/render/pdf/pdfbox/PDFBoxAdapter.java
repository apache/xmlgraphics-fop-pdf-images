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
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

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
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.common.COSObjectable;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.common.PDStream;

import org.apache.pdfbox.pdmodel.interactive.form.PDAcroForm;

import org.apache.fop.fonts.FontInfo;
import org.apache.fop.fonts.Typeface;
import org.apache.fop.pdf.PDFArray;
import org.apache.fop.pdf.PDFDictionary;
import org.apache.fop.pdf.PDFDocument;
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
public class PDFBoxAdapter {

    /** logging instance */
    protected static final Log log = LogFactory.getLog(PDFBoxAdapter.class);

    private static final Set FILTER_FILTER = new HashSet(
            Arrays.asList(new String[] {"Filter", "DecodeParms"}));

    private final PDFPage targetPage;
    private final PDFDocument pdfDoc;

    private final Map<Object, Object> clonedVersion;
    private final Map<Object, Object> objectCache;
    private Map<COSName, String> newXObj = new HashMap<COSName, String>();
    private Map<Integer, PDFArray> pageNumbers;
    private Collection<String> parentFonts = new ArrayList<String>();

    private int currentMCID;

    /**
     * Creates a new PDFBoxAdapter.
     * @param targetPage The target FOP PDF page object
     * @param objectCachePerFile the object cache for reusing objects shared by multiple pages.
     * @param pageNumbers references to page object numbers
     */
    public PDFBoxAdapter(PDFPage targetPage, Map<Object, Object> objectCachePerFile,
                         Map<Integer, PDFArray> pageNumbers) {
        this(targetPage, objectCachePerFile, pageNumbers, new HashMap<Object, Object>());
    }

    public PDFBoxAdapter(PDFPage targetPage, Map<Object, Object> objectCachePerFile,
                         Map<Integer, PDFArray> pageNumbers, Map<Object, Object> objectCache) {
        this.targetPage = targetPage;
        this.pdfDoc = this.targetPage.getDocument();
        this.clonedVersion = objectCachePerFile;
        this.pageNumbers = pageNumbers;
        this.objectCache = objectCache;
    }

    public PDFPage getTargetPage() {
        return targetPage;
    }

    public int getCurrentMCID() {
        return currentMCID;
    }

    public void setCurrentMCID(int currentMCID) {
        this.currentMCID = currentMCID;
    }

    protected Object cloneForNewDocument(Object base) throws IOException {
        return cloneForNewDocument(base, base);
    }

    protected Object cloneForNewDocument(Object base, Object keyBase) throws IOException {
        return cloneForNewDocument(base, keyBase, Collections.EMPTY_LIST);
    }

    protected Object cloneForNewDocument(Object base, Object keyBase, Collection exclude) throws IOException {
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
            for (Object o : list) {
                array.add(cloneForNewDocument(o, o, exclude));
            }
            return array;
        } else if (base instanceof COSObjectable && !(base instanceof COSBase)) {
            Object o = ((COSObjectable)base).getCOSObject();
            Object retval = cloneForNewDocument(o, o, exclude);
            return cacheClonedObject(keyBase, retval);
        } else if (base instanceof COSObject) {
            return readCOSObject((COSObject) base, exclude);
        } else if (base instanceof COSArray) {
            PDFArray newArray = new PDFArray();
            cacheClonedObject(keyBase, newArray);
            COSArray array = (COSArray)base;
            for (int i = 0; i < array.size(); i++) {
                newArray.add(cloneForNewDocument(array.get(i), array.get(i), exclude));
            }
            return newArray;
//        } else if (base instanceof COSStreamArray) {
//            COSStreamArray array = (COSStreamArray)base;
//            PDFArray newArray = new PDFArray();
//            cacheClonedObject(keyBase, newArray);
//            for (int i = 0, c = array.getStreamCount(); i < c; i++) {
//                newArray.add(cloneForNewDocument(array.get(i)));
//            }
//            return newArray;
        } else if (base instanceof COSStream) {
            return readCOSStream((COSStream) base, keyBase);
        } else if (base instanceof COSDictionary) {
            return readCOSDictionary((COSDictionary) base, keyBase, exclude);
        } else if (base instanceof COSName) {
            byte[] name = ((COSName)base).getName().getBytes("ISO-8859-1");
            PDFName newName = new PDFName(new String(name, "ISO-8859-1"));
            return cacheClonedObject(keyBase, newName);
        } else if (base instanceof COSInteger) {
            PDFNumber number = new PDFNumber();
            number.setNumber(((COSInteger)base).longValue());
            return cacheClonedObject(keyBase, number);
        } else if (base instanceof COSFloat) {
            PDFNumber number = new PDFNumber();
            number.setNumber(((COSFloat)base).floatValue());
            return cacheClonedObject(keyBase, number);
        } else if (base instanceof COSBoolean) {
            //TODO Do we need a PDFBoolean here?
            Boolean retval = ((COSBoolean)base).getValueAsObject();
            if (keyBase instanceof COSObject) {
                return cacheClonedObject(keyBase, new PDFBoolean(retval));
            } else {
                return cacheClonedObject(keyBase, retval);
            }
        } else if (base instanceof COSString) {
            return readCOSString((COSString) base, keyBase);
        } else if (base instanceof COSNull) {
            return cacheClonedObject(keyBase, null);
        } else {
            throw new UnsupportedOperationException("NYI: " + base.getClass().getName());
        }
    }

    private PDFDictionary readCOSDictionary(COSDictionary dic, Object keyBase, Collection exclude) throws IOException {
        PDFDictionary newDict = new PDFDictionary();
        cacheClonedObject(keyBase, newDict);
        for (Map.Entry<COSName, COSBase> e : dic.entrySet()) {
            if (!exclude.contains(e.getKey())) {
                newDict.put(e.getKey().getName(), cloneForNewDocument(e.getValue(), e.getValue(), exclude));
            }
        }
        return newDict;
    }

    private Object readCOSObject(COSObject object, Collection exclude) throws IOException {
        if (log.isTraceEnabled()) {
            log.trace("Cloning indirect object: "
                    + object.getObjectNumber()
                    + " " + object.getGenerationNumber());
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
                        + object.getObjectNumber()
                        + " " + object.getGenerationNumber());
            }
        }
        return obj;
    }

    private Object readCOSString(COSString string, Object keyBase) throws IOException {
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
    }

    private Object readCOSStream(COSStream originalStream, Object keyBase) throws IOException {
        InputStream in;
        Set filter;
        if (pdfDoc.isEncryptionActive()
                || (originalStream.containsKey(COSName.DECODE_PARMS) && !originalStream.containsKey(COSName.FILTER))) {
            in = originalStream.getUnfilteredStream();
            filter = FILTER_FILTER;
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
    }

    protected Object getCachedClone(Object base) throws IOException {
        Object key = PDFBoxAdapterUtil.getBaseKey(base);
        Object o = clonedVersion.get(key);
        if (o == null) {
            return objectCache.get(key);
        }
        return o;
    }

    protected Object cacheClonedObject(Object base, Object cloned) throws IOException {
        Object key = PDFBoxAdapterUtil.getBaseKey(base);
        if (key == null) {
            return cloned;
        }
        PDFObject pdfobj = (PDFObject) cloned;
        if (pdfobj != null && !pdfobj.hasObjectNumber() && !(base instanceof COSDictionary)) {
            pdfDoc.registerObject(pdfobj);
            if (log.isTraceEnabled()) {
                log.trace(key + ": " + pdfobj.getClass().getName() + " registered as "
                        + pdfobj.getObjectNumber() + " " + pdfobj.getGeneration());
            }
        }
        clonedVersion.put(key, cloned);
        if (key instanceof Integer) {
            objectCache.put(key, cloned);
        }
        return cloned;
    }

    private void transferDict(COSDictionary orgDict, PDFStream targetDict, Set filter) throws IOException {
        transferDict(orgDict, targetDict, filter, false);
    }

    private void transferDict(COSDictionary orgDict, PDFStream targetDict, Set filter, boolean inclusive)
        throws IOException {
        Set<COSName> keys = orgDict.keySet();
        for (COSName key : keys) {
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
     * Creates a stream (from FOP's PDF library) from a PDF page parsed with PDFBox.
     * @param sourceDoc the source PDF the given page to be copied belongs to
     * @param page the page to transform into a stream
     * @param key value to use as key for the stream
     * @param atdoc adjustment for stream
     * @param fontinfo fonts
     * @param pos rectangle
     * @return the stream
     * @throws IOException if an I/O error occurs
     */
    public String createStreamFromPDFBoxPage(PDDocument sourceDoc, PDPage page, String key,
                                             AffineTransform atdoc, FontInfo fontinfo, Rectangle pos)
        throws IOException {
        handleAnnotations(sourceDoc, page, atdoc);
        if (pageNumbers.containsKey(targetPage.getPageIndex())) {
            pageNumbers.get(targetPage.getPageIndex()).set(0, targetPage.makeReference());
        }
        PDResources sourcePageResources = page.getResources();
        PDStream pdStream = getContents(page);

        COSDictionary fonts = (COSDictionary)sourcePageResources.getCOSObject().getDictionaryObject(COSName.FONT);
        COSDictionary fontsBackup = null;
        UniqueName uniqueName = new UniqueName(key, sourcePageResources);
        String newStream = null;
        if (fonts != null && pdfDoc.isMergeFontsEnabled()) {
            fontsBackup = new COSDictionary(fonts);
            MergeFontsPDFWriter m = new MergeFontsPDFWriter(fonts, fontinfo, uniqueName, parentFonts, currentMCID);
            newStream = m.writeText(pdStream);
//            if (newStream != null) {
//                for (Object f : fonts.keySet().toArray()) {
//                    COSDictionary fontdata = (COSDictionary)fonts.getDictionaryObject((COSName)f);
//                    if (getUniqueFontName(fontdata) != null) {
//                        fonts.removeItem((COSName)f);
//                    }
//                }
//            }
        }
        if (newStream == null) {
            PDFWriter writer = new PDFWriter(uniqueName, currentMCID);
            newStream = writer.writeText(pdStream);
            currentMCID = writer.getCurrentMCID();

        }
        pdStream = new PDStream(sourceDoc, new ByteArrayInputStream(newStream.getBytes("ISO-8859-1")));
        mergeXObj(sourcePageResources.getCOSObject(), fontinfo, uniqueName);
        PDFDictionary pageResources = (PDFDictionary)cloneForNewDocument(sourcePageResources.getCOSObject());

        PDFDictionary fontDict = (PDFDictionary)pageResources.get("Font");
        if (fontDict != null && pdfDoc.isMergeFontsEnabled()) {
            for (Map.Entry<String, Typeface> fontEntry : fontinfo.getUsedFonts().entrySet()) {
                Typeface font = fontEntry.getValue();
                if (font instanceof FOPPDFFont) {
                    FOPPDFFont pdfFont = (FOPPDFFont)font;
                    if (pdfFont.getRef() == null) {
                        pdfFont.setRef(new PDFDictionary());
                        pdfDoc.assignObjectNumber(pdfFont.getRef());
                    }
                    fontDict.put(fontEntry.getKey(), pdfFont.getRef());
                }
            }
        }
        updateXObj(sourcePageResources.getCOSObject(), pageResources);
        if (fontsBackup != null) {
            sourcePageResources.getCOSObject().setItem(COSName.FONT, fontsBackup);
        }

        COSStream originalPageContents = pdStream.getCOSObject();

        bindOptionalContent(sourceDoc);

        PDFStream pageStream;
        Set filter;
//        if (originalPageContents instanceof COSStreamArray) {
//            COSStreamArray array = (COSStreamArray)originalPageContents;
//            pageStream = new PDFStream();
//            InputStream in = array.getUnfilteredStream();
//            OutputStream out = pageStream.getBufferOutputStream();
//            IOUtils.copyLarge(in, out);
//            filter = FILTER_FILTER;
//        } else {
            pageStream = (PDFStream)cloneForNewDocument(originalPageContents);
            filter = Collections.EMPTY_SET;
//        }
        if (pageStream == null) {
            pageStream = new PDFStream();
        }
        if (originalPageContents != null) {
            transferDict(originalPageContents, pageStream, filter);
        }

        transferPageDict(fonts, uniqueName, sourcePageResources);

        PDRectangle mediaBox = page.getMediaBox();
        PDRectangle cropBox = page.getCropBox();
        PDRectangle viewBox = cropBox != null ? cropBox : mediaBox;

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

        PDFBoxAdapterUtil.rotate(rotation, viewBox, atdoc);

        StringBuilder boxStr = new StringBuilder();
        boxStr.append(PDFNumber.doubleOut(mediaBox.getLowerLeftX())).append(' ')
                .append(PDFNumber.doubleOut(mediaBox.getLowerLeftY())).append(' ')
                .append(PDFNumber.doubleOut(mediaBox.getWidth())).append(' ')
                .append(PDFNumber.doubleOut(mediaBox.getHeight())).append(" re W n\n");
        return boxStr.toString() + IOUtils.toString(pdStream.createInputStream(null), "ISO-8859-1");
    }

    private PDStream getContents(PDPage page) throws IOException {
        PDStream pdStream = new PDStream(new COSStream());
        OutputStream os = pdStream.createOutputStream();
        IOUtils.copy(page.getContents(), os);
        os.close();
        return pdStream;
    }

    private void mergeXObj(COSDictionary sourcePageResources, FontInfo fontinfo, UniqueName uniqueName)
        throws IOException {
        COSDictionary xobj = (COSDictionary) sourcePageResources.getDictionaryObject(COSName.XOBJECT);
        if (xobj != null && pdfDoc.isMergeFontsEnabled()) {
            for (Map.Entry<COSName, COSBase> i : xobj.entrySet()) {
                COSObject v = (COSObject) i.getValue();
                COSStream stream = (COSStream) v.getObject();
                COSDictionary res = (COSDictionary) stream.getDictionaryObject(COSName.RESOURCES);
                if (res != null) {
                    COSDictionary src = (COSDictionary) res.getDictionaryObject(COSName.FONT);
                    if (src != null) {
                        COSDictionary target = (COSDictionary) sourcePageResources.getDictionaryObject(COSName.FONT);
                        if (target == null) {
                            sourcePageResources.setItem(COSName.FONT, src);
                        } else {
                            for (Map.Entry<COSName, COSBase> entry : src.entrySet()) {
                                if (!target.keySet().contains(entry.getKey())) {
                                    target.setItem(uniqueName.getName(entry.getKey()), entry.getValue());
                                }
                            }
                        }
                        PDFWriter writer = new MergeFontsPDFWriter(src, fontinfo, uniqueName, parentFonts, 0);
                        String c = writer.writeText(new PDStream(stream));
                        if (c != null) {
                            stream.removeItem(COSName.FILTER);
                            newXObj.put(i.getKey(), c);
                            for (Object e : src.keySet().toArray()) {
                                COSName name = (COSName) e;
                                src.setItem(uniqueName.getName(name), src.getItem(name));
                                src.removeItem(name);
                            }
                        }
                    }
                }
            }
        }
    }

    private void updateXObj(COSDictionary sourcePageResources, PDFDictionary pageResources) throws IOException {
        COSDictionary xobj = (COSDictionary) sourcePageResources.getDictionaryObject(COSName.XOBJECT);
        if (xobj != null && pdfDoc.isMergeFontsEnabled()) {
            PDFDictionary target = (PDFDictionary) pageResources.get("XObject");
            for (COSName entry : xobj.keySet()) {
                if (newXObj.containsKey(entry)) {
                    PDFStream s = (PDFStream) target.get(entry.getName());
                    s.setData(newXObj.get(entry).getBytes("ISO-8859-1"));
                    PDFDictionary xobjr = (PDFDictionary) s.get("Resources");
                    xobjr.put("Font", pageResources.get("Font"));
                }
            }
        }
    }

    private void transferPageDict(COSDictionary fonts, UniqueName uniqueName, PDResources sourcePageResources)
        throws IOException {
        if (fonts != null) {
            for (Map.Entry<COSName, COSBase> f : fonts.entrySet()) {
                String name = uniqueName.getName(f.getKey());
                targetPage.getPDFResources().addFont(name, (PDFDictionary)cloneForNewDocument(f.getValue()));
            }
        }
        for (Map.Entry<COSName, COSBase> e : sourcePageResources.getCOSObject().entrySet()) {
            transferDict(e, uniqueName);
        }
    }

    private void transferDict(Map.Entry<COSName, COSBase> dict, UniqueName uniqueName) throws IOException {
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
                newDict.put(uniqueName.getName(v.getKey()), cloneForNewDocument(v.getValue()));
            }
            targetPage.getPDFResources().put(name, newDict);
        }
    }

    private void bindOptionalContent(PDDocument sourceDoc) throws IOException {
        /*
         * PDOptionalContentProperties ocProperties =
         * sourceDoc.getDocumentCatalog().getOCProperties(); PDFDictionary ocDictionary =
         * (PDFDictionary) cloneForNewDocument(ocProperties); if (ocDictionary != null) {
         * this.pdfDoc.getRoot().put(COSName.OCPROPERTIES.getName(), ocDictionary); }
         */
    }

    private void handleAnnotations(PDDocument sourceDoc, PDPage page, AffineTransform at) throws IOException {
        PDDocumentCatalog srcCatalog = sourceDoc.getDocumentCatalog();
        PDAcroForm srcAcroForm = srcCatalog.getAcroForm();
        List pageAnnotations = page.getAnnotations();
        if (srcAcroForm == null && pageAnnotations.isEmpty()) {
            return;
        }

        PDFBoxAdapterUtil.moveAnnotations(page, pageAnnotations, at);

        //Pseudo-cache the target page in place of the original source page.
        //This essentially replaces the original page reference with the target page.
        COSObject cosPage = null;
        COSDictionary parentDic = (COSDictionary) page.getCOSObject().getDictionaryObject(COSName.PARENT, COSName.P);
        COSArray kids = (COSArray) parentDic.getDictionaryObject(COSName.KIDS);
        for (int i = 0; i < kids.size(); i++) {
            //Hopefully safe to cast, as kids need to be indirect objects
            COSObject kid = (COSObject) kids.get(i);
            if (!pageNumbers.containsKey(i)) {
                PDFArray a = new PDFArray();
                a.add(null);
                pdfDoc.assignObjectNumber(a);
                pdfDoc.addTrailerObject(a);
                pageNumbers.put(i, a);
            }
            cacheClonedObject(kid, pageNumbers.get(i));
            if (kid.getObject() == page.getCOSObject()) {
                cosPage = kid;
            }
        }
        if (cosPage == null) {
            throw new IOException("Illegal PDF. Page not part of parent page node.");
        }

        Set<COSObject> fields = copyAnnotations(page);

        boolean formAlreadyCopied = getCachedClone(srcAcroForm) != null;
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
            pdfDoc.assignObjectNumber(destAcroForm);
            pdfDoc.addTrailerObject(destAcroForm);
            catalog.put(COSName.ACRO_FORM.getName(), destAcroForm);
        }
        PDFArray clonedFields = (PDFArray) destAcroForm.get(COSName.FIELDS.getName());
        if (clonedFields == null) {
            clonedFields = new PDFArray();
            destAcroForm.put(COSName.FIELDS.getName(), clonedFields);
        }
        for (COSObject field : fields) {
            PDFDictionary clone = (PDFDictionary) cloneForNewDocument(field, field, Arrays.asList(COSName.KIDS));
            clonedFields.add(clone);
        }
    }

    private Set<COSObject> copyAnnotations(PDPage page) throws IOException {
        COSArray annots = (COSArray) page.getCOSObject().getDictionaryObject(COSName.ANNOTS);
        Set<COSObject> fields = Collections.emptySet();
        if (annots != null) {
            fields = new TreeSet<COSObject>(new CompareFields());
            for (Object annot1 : annots) {
                Collection<COSName> exclude = new ArrayList<COSName>();
                exclude.add(COSName.P);
                if (annot1 instanceof COSObject) {
                    COSObject annot = (COSObject) annot1;
                    COSObject fieldObject = annot;
                    COSDictionary field = (COSDictionary) fieldObject.getObject();
                    COSObject parent;
                    while ((parent = (COSObject) field.getItem(COSName.PARENT)) != null) {
                        fieldObject = parent;
                        field = (COSDictionary) fieldObject.getObject();
                    }
                    fields.add(fieldObject);

                    if (((COSDictionary) annot.getObject()).getItem(COSName.getPDFName("StructParent")) != null) {
                        exclude.add(COSName.PARENT);
                    }
                }

                PDFObject clonedAnnot = (PDFObject) cloneForNewDocument(annot1, annot1, exclude);
                if (clonedAnnot instanceof PDFDictionary) {
                    clonedAnnot.setParent(targetPage);
                    PDFBoxAdapterUtil.updateAnnotationLink((PDFDictionary) clonedAnnot);
                }
                targetPage.addAnnotation(clonedAnnot);
            }
        }
        return fields;
    }

    static class CompareFields implements Comparator<COSObject>, Serializable {
        private static final long serialVersionUID = -6081505461660440801L;

        public int compare(COSObject o1, COSObject o2) {
            return (int) (o1.getObjectNumber() - o2.getObjectNumber());
        }
    }
}
