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
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.io.IOUtils;

import org.apache.pdfbox.cos.COSArray;
import org.apache.pdfbox.cos.COSBase;
import org.apache.pdfbox.cos.COSDictionary;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.cos.COSObject;
import org.apache.pdfbox.cos.COSStream;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentCatalog;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.common.PDStream;
import org.apache.pdfbox.pdmodel.interactive.form.PDAcroForm;

import org.apache.fop.fonts.FontInfo;
import org.apache.fop.fonts.Typeface;
import org.apache.fop.pdf.PDFArray;
import org.apache.fop.pdf.PDFDictionary;
import org.apache.fop.pdf.PDFDocument;
import org.apache.fop.pdf.PDFFormXObject;
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

    protected static final Set<String> FILTER_FILTER = Collections.unmodifiableSet(
            new HashSet<String>(Arrays.asList("Filter", "DecodeParms")));

    private final PDFPage targetPage;
    protected final PDFDocument pdfDoc;

    protected final Map<Object, Object> clonedVersion;
    protected final Map<Object, Object> objectCache;
    private Map<COSName, String> newXObj = new HashMap<COSName, String>();
    private Map<Integer, PDFArray> pageNumbers;
    private Collection<String> parentFonts = new ArrayList<String>();

    // Current Marked Content ID
    protected int currentMCID;
    protected UniqueName uniqueName;
    private HandleAnnotations handleAnnotations;

    /**
     * Creates a new PDFBoxAdapter.
     * @param targetPage The target FOP PDF page object
     * @param objectCachePerFile the object cache for reusing objects shared by multiple pages.
     * @param pageNumbers references to page object numbers
     */
    public PDFBoxAdapter(PDFPage targetPage, Map<Object, Object> objectCachePerFile,
                         Map<Integer, PDFArray> pageNumbers) {
        this(targetPage, objectCachePerFile, null, pageNumbers, new HashMap<>());
    }

    public PDFBoxAdapter(PDFPage targetPage, Map<Object, Object> objectCachePerFile, Map<String, Object> usedFields,
                         Map<Integer, PDFArray> pageNumbers, Map<Object, Object> objectCache) {
        this.targetPage = targetPage;
        this.pdfDoc = this.targetPage.getDocument();
        this.clonedVersion = objectCachePerFile;
        this.pageNumbers = pageNumbers;
        this.objectCache = objectCache;
        if (pdfDoc.isMergeFormFieldsEnabled()) {
            handleAnnotations = new MergeAnnotations(this, usedFields);
        } else {
            handleAnnotations = new CloneAnnotations(this);
        }
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

    protected Object getCachedClone(Object base) throws IOException {
        Object key = PDFBoxAdapterUtil.getBaseKey(base);
        Object o = clonedVersion.get(key);
        if (o == null) {
            return objectCache.get(key);
        }
        return o;
    }

    protected Object cloneForNewDocument(Object base) throws IOException {
        return new PDFCloner(this, false).cloneForNewDocument(base);
    }

    protected Object cloneForNewDocument(Object base, Object keyBase, Collection exclude) throws IOException {
        return new PDFCloner(this, false).cloneForNewDocument(base, keyBase, exclude);
    }

    protected void cacheClonedObject(Object base, Object cloned) throws IOException {
        new PDFCloner(this, false).cacheClonedObject(base, cloned);
    }

    protected void transferDict(COSDictionary orgDict, PDFStream targetDict, Set filter) throws IOException {
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
     * @param sourceDoc the source PDF the given sourcePage to be copied belongs to
     * @param sourcePage the page to transform into a stream
     * @param key value to use as key for the stream
     * @param pageAdjust adjustment for annotations
     * @param fontinfo fonts
     * @param destRect rectangle
     * @return the stream
     * @throws IOException if an I/O error occurs
     */
    public Object createStreamFromPDFBoxPage(PDDocument sourceDoc, PDPage sourcePage, String key,
                                             AffineTransform pageAdjust, FontInfo fontinfo, Rectangle destRect)
        throws IOException {
        COSDictionary sourcePageResources = getResources(sourcePage);
        PatternUtil patternUtil = new PatternUtil(targetPage, destRect, sourcePage, pdfDoc.isFormXObjectEnabled());
        uniqueName = new UniqueName(
                key, sourcePageResources, patternUtil.getPatternNames(), pdfDoc.isFormXObjectEnabled(), destRect);
        key = patternUtil.getKey(key);
        handleAnnotations(sourceDoc, sourcePage, pageAdjust, destRect);
        if (pageNumbers.containsKey(targetPage.getPageIndex())) {
            pageNumbers.get(targetPage.getPageIndex()).set(0, targetPage.makeReference());
        }
        PDStream pdStream = getContents(sourcePage);

        COSDictionary fonts = (COSDictionary)sourcePageResources.getDictionaryObject(COSName.FONT);
        COSDictionary fontsBackup = null;
        String newStream = null;
        if (fonts != null && pdfDoc.isMergeFontsEnabled()) {
            fontsBackup = new COSDictionary(fonts);
            MergeFontsPDFWriter m = new MergeFontsPDFWriter(fonts, fontinfo, uniqueName, parentFonts, currentMCID);
            newStream = m.writeText(pdStream);
        }
        if (!pdfDoc.isFormXObjectEnabled()) {
            if (newStream == null) {
                newStream = (String) clonedVersion.get(key);
                if (newStream == null) {
                    PDFWriter writer = new PDFWriter(uniqueName, currentMCID);
                    newStream = writer.writeText(pdStream);
                    clonedVersion.put(key, newStream);
                }
            }
            pdStream = new PDStream(sourceDoc, new ByteArrayInputStream(newStream.getBytes(PDFDocument.ENCODING)));
        }
        mergeXObj(sourcePageResources, fontinfo, uniqueName);
        PDFDictionary pageResources =
                (PDFDictionary)cloneForNewDocument(sourcePageResources, sourcePageResources, patternUtil.getExclude());

        updateMergeFontInfo(pageResources, fontinfo);
        updateXObj(sourcePageResources, pageResources);
        if (fontsBackup != null) {
            sourcePageResources.setItem(COSName.FONT, fontsBackup);
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
            pageStream = (PDFStream)cloneForNewDocument(originalPageContents, originalPageContents,
                    patternUtil.getExclude());
            filter = Collections.EMPTY_SET;
//        }
        if (pageStream == null) {
            pageStream = new PDFStream();
        }

        if (pdfDoc.isFormXObjectEnabled()) {
            return getFormXObject(pageResources, pageStream, key, sourcePage);
        }

        if (originalPageContents != null) {
            transferDict(originalPageContents, pageStream, filter);
        }

        transferPageDict(fonts, uniqueName, sourcePageResources);

        patternUtil.promotePatterns();

        PDRectangle mediaBox = sourcePage.getMediaBox();
        PDRectangle cropBox = sourcePage.getCropBox();
        PDRectangle viewBox = cropBox != null ? cropBox : mediaBox;

        //Handle the /Rotation entry on the sourcePage dict
        int rotation = PDFUtil.getNormalizedRotation(sourcePage);

        //Transform to FOP's user space
        float w = (float)destRect.getWidth() / 1000f;
        float h = (float)destRect.getHeight() / 1000f;
        if (rotation == 90 || rotation == 270) {
            float tmp = w;
            w = h;
            h = tmp;
        }
        pageAdjust.setTransform(AffineTransform.getScaleInstance(w / viewBox.getWidth(), h / viewBox.getHeight()));
        pageAdjust.translate(0, viewBox.getHeight());
        pageAdjust.rotate(-Math.PI);
        pageAdjust.scale(-1, 1);
        pageAdjust.translate(-viewBox.getLowerLeftX(), -viewBox.getLowerLeftY());

        PDFBoxAdapterUtil.rotate(rotation, viewBox, pageAdjust);

        StringBuilder boxStr = new StringBuilder();
        boxStr.append(PDFNumber.doubleOut(mediaBox.getLowerLeftX())).append(' ')
                .append(PDFNumber.doubleOut(mediaBox.getLowerLeftY())).append(' ')
                .append(PDFNumber.doubleOut(mediaBox.getWidth())).append(' ')
                .append(PDFNumber.doubleOut(mediaBox.getHeight())).append(" re W n\n");
        return boxStr.toString() + IOUtils.toString(pdStream.createInputStream(), PDFDocument.ENCODING);
    }

    private PDStream getContents(PDPage page) throws IOException {
        PDStream pdStream = new PDStream(new COSStream());
        OutputStream os = pdStream.createOutputStream();
        IOUtils.copy(page.getContents(), os);
        os.close();
        return pdStream;
    }

    private void updateMergeFontInfo(PDFDictionary pageResources, FontInfo fontinfo) {
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
    }

    private PDFFormXObject getFormXObject(PDFDictionary pageResources, PDFStream pageStream, String key, PDPage page)
        throws IOException {
        if (pdfDoc.isMergeFontsEnabled()) {
            throw new RuntimeException("merge-fonts and form-xobject can't both be enabled");
        }
        if (!pageResources.hasObjectNumber()) {
            pdfDoc.registerObject(pageResources);
        }
        PDFFormXObject form = pdfDoc.addFormXObject(null, pageStream, pageResources.makeReference(), key);
        final Set<String> page2Form = new HashSet<String>(Arrays.asList("Group", "LastModified", "Metadata"));
        transferDict(page.getCOSObject(), pageStream, page2Form, true);

        AffineTransform at = form.getMatrix();
        PDRectangle mediaBox = page.getMediaBox();
        PDRectangle cropBox = page.getCropBox();
        PDRectangle viewBox = cropBox != null ? cropBox : mediaBox;

        //Handle the /Rotation entry on the page dict
        int rotation = PDFUtil.getNormalizedRotation(page);

        //Transform to FOP's user space
        at.scale(1 / viewBox.getWidth(), 1 / viewBox.getHeight());
        at.translate(mediaBox.getLowerLeftX() - viewBox.getLowerLeftX(),
                mediaBox.getLowerLeftY() - viewBox.getLowerLeftY());
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
                break;
            default:
                //no additional transformations necessary
                break;
        }
        form.setMatrix(at);

        form.setBBox(new Rectangle2D.Float(
                viewBox.getLowerLeftX(), viewBox.getLowerLeftY(),
                viewBox.getUpperRightX(), viewBox.getUpperRightY()));
        return form;
    }

    private COSDictionary getResources(PDPage page) {
        PDResources res = page.getResources();
        if (res == null) {
            return new COSDictionary();
        }
        return res.getCOSObject();
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
                    PDFStream s = (PDFStream) target.get(uniqueName.getName(entry));
                    s.setData(newXObj.get(entry).getBytes(PDFDocument.ENCODING));
                    PDFDictionary xobjr = (PDFDictionary) s.get("Resources");
                    xobjr.put("Font", pageResources.get("Font"));
                }
            }
        }
    }

    /**
     * Transfer source page resources to target page.
     * @param srcPgFonts The source page's fonts.
     * @param uniqueName Name de-collisioner.
     * @param sourcePageResources The source page's resources.
     * @throws IOException On IO exception.
     */
    private void transferPageDict(COSDictionary srcPgFonts, UniqueName uniqueName, COSDictionary sourcePageResources)
            throws IOException {
        if (srcPgFonts != null) {
            for (Map.Entry<COSName, COSBase> f : srcPgFonts.entrySet()) {
                String name = uniqueName.getName(f.getKey());
                targetPage.getPDFResources().addFont(name, (PDFDictionary)cloneForNewDocument(f.getValue()));
            }
        }
        for (Map.Entry<COSName, COSBase> e : sourcePageResources.entrySet()) {
            transferDict(e, uniqueName);
        }
    }

    /**
     * Transfer source page resource to target page.
     * @param dict A source page resource.
     * @param uniqueName Name de-collisioner.
     * @throws IOException On IO exception.
     */
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

    private void handleAnnotations(PDDocument sourceDoc, PDPage page, AffineTransform at, Rectangle pos)
        throws IOException {
        PDDocumentCatalog srcCatalog = sourceDoc.getDocumentCatalog();
        PDAcroForm srcAcroForm = srcCatalog.getAcroForm();
        List pageAnnotations = page.getAnnotations();
        if (srcAcroForm == null && pageAnnotations.isEmpty()) {
            return;
        }

        PDFBoxAdapterUtil.moveAnnotations(page, pageAnnotations, at, pos);

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

        Set<?> fields = copyAnnotations(page, srcAcroForm);
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
        for (Object field : fields) {
            if (field instanceof COSBase) {
                field = cloneForNewDocument(field, field, Arrays.asList(COSName.KIDS));
            }
            if (!clonedFields.contains(field)) {
                clonedFields.add(field);
            }
        }
    }

    private Set<?> copyAnnotations(PDPage page, PDAcroForm srcAcroForm) throws IOException {
        COSArray annots = (COSArray) page.getCOSObject().getDictionaryObject(COSName.ANNOTS);
        if (annots != null) {
            for (COSBase annotBase : annots) {
                Collection<COSName> exclude = new ArrayList<COSName>();
                exclude.add(COSName.P);
                if (annotBase instanceof COSObject) {
                    COSObject annot = (COSObject) annotBase;
                    handleAnnotations.load(annot, srcAcroForm);
                    if (((COSDictionary) annot.getObject()).getItem(COSName.STRUCT_PARENT) != null) {
                        exclude.add(COSName.PARENT);
                    }
                }
                PDFObject clonedAnnot = (PDFObject)new PDFCloner(this, pdfDoc.isMergeFormFieldsEnabled())
                        .cloneForNewDocument(annotBase, annotBase, exclude);
                if (clonedAnnot instanceof PDFDictionary) {
                    handleAnnotations.cloneAnnotParent(annotBase, (PDFDictionary) clonedAnnot, exclude);
                    clonedAnnot.setParent(targetPage);
                    PDFBoxAdapterUtil.updateAnnotationLink((PDFDictionary) clonedAnnot);
                }
                targetPage.addAnnotation(clonedAnnot);
            }
        }
        return handleAnnotations.getFields();
    }
}
