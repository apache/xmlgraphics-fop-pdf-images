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

import java.awt.Rectangle;
import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D;
import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.Assert;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSArray;
import org.apache.pdfbox.cos.COSDictionary;
import org.apache.pdfbox.cos.COSInteger;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.cos.COSObject;
import org.apache.pdfbox.io.RandomAccessReadBuffer;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.common.PDStream;

import org.apache.xmlgraphics.image.loader.ImageException;
import org.apache.xmlgraphics.image.loader.ImageInfo;
import org.apache.xmlgraphics.image.loader.impl.ImageGraphics2D;
import org.apache.xmlgraphics.image.loader.util.SoftMapCache;
import org.apache.xmlgraphics.java2d.GeneralGraphics2DImagePainter;
import org.apache.xmlgraphics.java2d.GraphicContext;
import org.apache.xmlgraphics.ps.PSGenerator;

import org.apache.fop.apps.FOUserAgent;
import org.apache.fop.fonts.FontInfo;
import org.apache.fop.pdf.PDFAnnotList;
import org.apache.fop.pdf.PDFArray;
import org.apache.fop.pdf.PDFDictionary;
import org.apache.fop.pdf.PDFDocument;
import org.apache.fop.pdf.PDFEncryptionParams;
import org.apache.fop.pdf.PDFFormXObject;
import org.apache.fop.pdf.PDFGState;
import org.apache.fop.pdf.PDFMergeFontsParams;
import org.apache.fop.pdf.PDFPage;
import org.apache.fop.pdf.PDFResources;
import org.apache.fop.pdf.PDFStream;
import org.apache.fop.render.pcl.PCLGenerator;
import org.apache.fop.render.pcl.PCLGraphics2D;
import org.apache.fop.render.pdf.PDFContentGenerator;
import org.apache.fop.render.pdf.PDFRenderingContext;
import org.apache.fop.render.ps.PSDocumentHandler;
import org.apache.fop.render.ps.PSImageFormResource;
import org.apache.fop.render.ps.PSRenderingUtil;

public class PDFBoxAdapterTestCase {
    protected static final String ROTATE = "rotate.pdf";
    protected static final String ANNOT = "annot.pdf";
    protected static final String ANNOT2 = "annot2.pdf";
    protected static final String ANNOT3 = "annot3.pdf";
    protected static final String SHADING = "shading.pdf";
    protected static final String LINK = "link.pdf";
    protected static final String IMAGE = "image.pdf";
    protected static final String HELLOTagged = "taggedWorld.pdf";
    protected static final String LOOP = "loop.pdf";
    protected static final String ERROR = "error.pdf";
    protected static final String LIBREOFFICE = "libreoffice.pdf";
    protected static final String SMASK = "smask.pdf";
    protected static final String ACCESSIBLERADIOBUTTONS = "accessibleradiobuttons.pdf";
    protected static final String PATTERN = "pattern.pdf";
    protected static final String FORMROTATED = "formrotated.pdf";
    protected static final String SOFTMASK = "softmask.pdf";

    protected static PDFPage getPDFPage(PDFDocument doc) {
        final Rectangle2D r = new Rectangle2D.Double();
        return new PDFPage(new PDFResources(doc), 0, r, r, r, r);
    }

    protected static PDFBoxAdapter getPDFBoxAdapter(boolean mergeFonts, boolean formXObject) {
        return getPDFBoxAdapter(new PDFDocument(""), mergeFonts, formXObject, false, new HashMap<String, Object>());
    }

    private static PDFBoxAdapter getPDFBoxAdapter(PDFDocument doc, boolean mergeFonts, boolean formXObject,
                                                    boolean mergeFormFields, Map<String, Object> usedFields) {
        PDFPage pdfpage = getPDFPage(doc);
        if (mergeFonts) {
            doc.setMergeFontsParams(new PDFMergeFontsParams(true));
        }
        doc.setFormXObjectEnabled(formXObject);
        doc.setMergeFormFieldsEnabled(mergeFormFields);
        pdfpage.setDocument(doc);
        pdfpage.setObjectNumber(1);
        return new PDFBoxAdapter(pdfpage, new HashMap<>(), usedFields, new HashMap<Integer, PDFArray>(),
                new HashMap<>());
    }

    public static PDDocument load(String pdf) throws IOException {
        return Loader.loadPDF(new RandomAccessReadBuffer(PDFBoxAdapterTestCase.class.getResourceAsStream(pdf)));
    }

    @Test
    public void testTaggedPDFWriter() throws IOException {
        PDFBoxAdapter adapter = getPDFBoxAdapter(false, false);
        adapter.setCurrentMCID(5);
        PDDocument doc = load(HELLOTagged);
        PDPage page = doc.getPage(0);
        AffineTransform pageAdjust = new AffineTransform();
        Rectangle r = new Rectangle(0, 1650, 842000, 595000);
        String stream = (String) adapter.createStreamFromPDFBoxPage(doc, page, "key", pageAdjust, null, r, pageAdjust);
        Assert.assertTrue(stream, stream.contains("/P <</MCID 5 >>BDC"));
        doc.close();
    }

    @Test
    public void testAnnot() throws Exception {
        PDFDocument pdfdoc = new PDFDocument("");
        PDFPage pdfpage = getPDFPage(pdfdoc);
        pdfpage.setDocument(pdfdoc);
        pdfpage.setObjectNumber(1);
        PDFBoxAdapter adapter = new PDFBoxAdapter(pdfpage, new HashMap<>(), new HashMap<Integer, PDFArray>());
        PDDocument doc = load(ANNOT);
        PDPage page = doc.getPage(0);
        AffineTransform pageAdjust = new AffineTransform();
        Rectangle r = new Rectangle(0, 1650, 842000, 595000);
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        pdfdoc.output(os);
        os.reset();
        adapter.createStreamFromPDFBoxPage(doc, page, "key", pageAdjust, null, r, pageAdjust);
        pdfdoc.outputTrailer(os);
        Assert.assertTrue(os.toString(StandardCharsets.UTF_8.name()).contains("/Fields ["));
        doc.close();
    }

    @Test
    public void testAnnot2() throws Exception {
        PDFBoxAdapter adapter = getPDFBoxAdapter(false, false);
        PDDocument doc = load(ANNOT);
        PDPage page = doc.getPage(0);
        COSArray annots = (COSArray) page.getCOSObject().getDictionaryObject(COSName.ANNOTS);
        COSDictionary dict = (COSDictionary) ((COSObject)annots.get(0)).getObject();
        dict.setItem(COSName.PARENT, COSInteger.ONE);

        AffineTransform pageAdjust = new AffineTransform();
        Rectangle r = new Rectangle(0, 1650, 842000, 595000);
        adapter.createStreamFromPDFBoxPage(doc, page, "key", pageAdjust, null, r, pageAdjust);
        doc.close();
    }


    @Test
    public void testAnnot3() throws Exception {
        PDFDocument pdfdoc = new PDFDocument("");
        PDFPage pdfpage = getPDFPage(pdfdoc);
        pdfpage.setDocument(pdfdoc);
        pdfpage.setObjectNumber(1);
        PDFBoxAdapter adapter = new PDFBoxAdapter(pdfpage, new HashMap<>(), new HashMap<Integer, PDFArray>());
        PDDocument doc = load(ACCESSIBLERADIOBUTTONS);
        PDPage page = doc.getPage(0);
        AffineTransform pageAdjust = new AffineTransform();
        Rectangle r = new Rectangle(0, 1650, 842000, 595000);
        adapter.createStreamFromPDFBoxPage(doc, page, "key", pageAdjust, null, r, pageAdjust);
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        pdfdoc.output(os);
        String out = os.toString(StandardCharsets.UTF_8.name());
        Assert.assertTrue(out.contains("/Parent "));
        Assert.assertTrue(out.contains("/Kids "));
        doc.close();
    }

    @Test
    public void testAnnotFields() throws Exception {
        PDFDocument pdfdoc = new PDFDocument("");
        PDFPage pdfpage = getPDFPage(pdfdoc);
        pdfpage.setDocument(pdfdoc);
        pdfpage.setObjectNumber(1);
        PDFBoxAdapter adapter = new PDFBoxAdapter(pdfpage, new HashMap<>(), new HashMap<Integer, PDFArray>());
        PDDocument doc = load(ACCESSIBLERADIOBUTTONS);
        COSArray fields = (COSArray)
                doc.getDocumentCatalog().getAcroForm().getCOSObject().getDictionaryObject(COSName.FIELDS);
        fields.remove(0);
        PDPage page = doc.getPage(0);
        AffineTransform pageAdjust = new AffineTransform();
        Rectangle r = new Rectangle(0, 1650, 842000, 595000);
        adapter.createStreamFromPDFBoxPage(doc, page, "key", pageAdjust, null, r, pageAdjust);
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        pdfdoc.outputTrailer(os);
        Assert.assertTrue(os.toString(StandardCharsets.UTF_8.name()).contains("/Fields []"));
        doc.close();
    }

    @Test
    public void testAnnotNoField() throws Exception {
        PDFDocument pdfdoc = new PDFDocument("");
        PDFPage pdfpage = getPDFPage(pdfdoc);
        pdfpage.setDocument(pdfdoc);
        pdfpage.setObjectNumber(1);
        PDFBoxAdapter adapter = new PDFBoxAdapter(pdfpage, new HashMap<>(), new HashMap<Integer, PDFArray>());
        PDDocument doc = load(ACCESSIBLERADIOBUTTONS);
        doc.getDocumentCatalog().getAcroForm().getCOSObject().removeItem(COSName.FIELDS);
        PDPage page = doc.getPage(0);
        AffineTransform pageAdjust = new AffineTransform();
        Rectangle r = new Rectangle(0, 1650, 842000, 595000);
        adapter.createStreamFromPDFBoxPage(doc, page, "key", pageAdjust, null, r, pageAdjust);
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        pdfdoc.outputTrailer(os);
        Assert.assertTrue(os.toString(StandardCharsets.UTF_8.name()).contains("/Fields []"));
        doc.close();
    }

    @Test
    public void testLink() throws Exception {
        PDFDocument pdfdoc = new PDFDocument("");
        PDFPage pdfpage = getPDFPage(pdfdoc);
        pdfpage.setDocument(pdfdoc);
        pdfpage.setObjectNumber(1);
        Map<Integer, PDFArray> pageNumbers = new HashMap<Integer, PDFArray>();
        PDFBoxAdapter adapter = new PDFBoxAdapter(pdfpage, new HashMap<>(), pageNumbers);
        PDDocument doc = load(LINK);
        PDPage page = doc.getPage(0);
        AffineTransform pageAdjust = new AffineTransform();
        Rectangle r = new Rectangle(0, 1650, 842000, 595000);
        String stream = (String) adapter.createStreamFromPDFBoxPage(doc, page, "key", pageAdjust, null, r, pageAdjust);
        Assert.assertTrue(stream.contains("/Link <</MCID 5 >>BDC"));
        assertEquals(pageNumbers.size(), 4);
        PDFAnnotList annots = (PDFAnnotList) pdfpage.get("Annots");
        assertEquals(annots.toPDFString(), "[\n1 0 R\n2 0 R\n]");
        doc.close();
    }

    @Test
    public void testPSPDFGraphics2D() throws Exception {
        ByteArrayOutputStream stream = pdfToPS(IMAGE);
        assertEquals(countString(stream.toString(StandardCharsets.UTF_8.name()), "%AXGBeginBitmap:"), 1);

        pdfToPS(FontMergeTestCase.CFF1);
        pdfToPS(FontMergeTestCase.CFF2);
        pdfToPS(FontMergeTestCase.CFF3);
        pdfToPS(FontMergeTestCase.TTCID1);
        pdfToPS(FontMergeTestCase.TTCID2);
        pdfToPS(FontMergeTestCase.TTSubset1);
        pdfToPS(FontMergeTestCase.TTSubset2);
        pdfToPS(FontMergeTestCase.TTSubset3);
        pdfToPS(FontMergeTestCase.TTSubset5);
        stream = pdfToPS(FontMergeTestCase.CFFCID1);
        assertEquals(countString(stream.toString(StandardCharsets.UTF_8.name()), "%AXGBeginBitmap:"), 2);
        pdfToPS(FontMergeTestCase.CFFCID2);
        pdfToPS(FontMergeTestCase.Type1Subset1);
        pdfToPS(FontMergeTestCase.Type1Subset2);
        pdfToPS(FontMergeTestCase.Type1Subset3);
        pdfToPS(FontMergeTestCase.Type1Subset4);
        pdfToPS(ROTATE);
        pdfToPS(LINK);
        pdfToPS(LOOP);
        stream = pdfToPS(LIBREOFFICE);
        Assert.assertTrue(stream.toString(StandardCharsets.UTF_8.name()).contains("/MaskColor [ 255 255 255 ]"));
    }

    private int countString(String s, String value) {
        return s.split(value).length - 1;
    }

    protected static ByteArrayOutputStream pdfToPS(String pdf) throws IOException, ImageException {
        return pdfToPS(pdf, null);
    }

    protected static ByteArrayOutputStream pdfToPS(String pdf, Map<String, Object> hints)
        throws IOException, ImageException {
        ImageConverterPDF2G2D i = new ImageConverterPDF2G2D();
        ImageInfo imgi = new ImageInfo(pdf, "b");
        try (PDDocument doc = load(pdf)) {
            org.apache.xmlgraphics.image.loader.Image img = new ImagePDF(imgi, doc);
            ImageGraphics2D ig = (ImageGraphics2D) i.convert(img, hints);
            GeneralGraphics2DImagePainter g = (GeneralGraphics2DImagePainter) ig.getGraphics2DImagePainter();
            ByteArrayOutputStream stream = new ByteArrayOutputStream();
            PSPDFGraphics2D g2d = (PSPDFGraphics2D) g.getGraphics(true, new FOPPSGeneratorImpl(stream));
            Rectangle2D rect = new Rectangle2D.Float(0, 0, 100, 100);
            GraphicContext gc = new GraphicContext();
            g2d.setGraphicContext(gc);
            ig.getGraphics2DImagePainter().paint(g2d, rect);
            return stream;
        }
    }

    @Test
    public void testSmask() throws IOException, ImageException {
        ByteArrayOutputStream ps = pdfToPS(SMASK);
        Assert.assertTrue(ps.toString(StandardCharsets.UTF_8.name()).contains("/Pattern"));
        Assert.assertTrue(ps.toString(StandardCharsets.UTF_8.name()).contains("{<\nf1f1f1"));
    }

    @Test
    public void testPCL() {
        UnsupportedOperationException ex = Assert.assertThrows(UnsupportedOperationException.class, () ->
                pdfToPCL(SHADING));
        Assert.assertTrue(ex.getMessage().contains("Clipping is not supported."));
    }

    private void pdfToPCL(String pdf) throws IOException, ImageException {
        try (PDDocument doc = load(pdf)) {
            pdfToPCL(doc, pdf);
        }
    }

    private void pdfToPCL(PDDocument doc, String pdf) throws IOException, ImageException {
        ImageConverterPDF2G2D i = new ImageConverterPDF2G2D();
        ImageInfo imgi = new ImageInfo(pdf, "b");
        org.apache.xmlgraphics.image.loader.Image img = new ImagePDF(imgi, doc);
        ImageGraphics2D ig = (ImageGraphics2D) i.convert(img, null);
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        PCLGraphics2D g2d = new PCLGraphics2D(new PCLGenerator(stream));
        Rectangle2D rect = new Rectangle2D.Float(0, 0, 100, 100);
        GraphicContext gc = new GraphicContext();
        g2d.setGraphicContext(gc);
        ig.getGraphics2DImagePainter().paint(g2d, rect);
    }

    static class FOPPSGeneratorImpl extends PSGenerator implements PSDocumentHandler.FOPPSGenerator {
        public FOPPSGeneratorImpl(OutputStream out) {
            super(out);
        }

        public PSDocumentHandler getHandler() {
            PSDocumentHandler handler = mock(PSDocumentHandler.class);
            PSRenderingUtil util = mock(PSRenderingUtil.class);
            when(util.isOptimizeResources()).thenReturn(false);
            when(handler.getPSUtil()).thenReturn(util);
            FOUserAgent mockedAgent = mock(FOUserAgent.class);
            when(handler.getUserAgent()).thenReturn(mockedAgent);
            when(mockedAgent.getTargetResolution()).thenReturn(72f);
            when(handler.getFormForImage(any(String.class))).thenReturn(new PSImageFormResource(0, ""));
            return handler;
        }

        public BufferedOutputStream getTempStream(URI uri) throws IOException {
            return new BufferedOutputStream(new ByteArrayOutputStream());
        }

        public Map<Integer, URI> getImages() {
            return new HashMap<Integer, URI>();
        }
    }

    private void loadPage(PDFDocument pdfdoc, String src) throws IOException {
        PDDocument doc = load(src);
        loadPage(pdfdoc, doc, new Rectangle());
    }

    private PDFPage loadPage(PDFDocument pdfdoc, PDDocument doc, Rectangle destRect) throws IOException {
        PDFPage pdfpage = getPDFPage(pdfdoc);
        createStreamFromPDFBoxPage(pdfpage, pdfdoc, doc, destRect, new AffineTransform());
        return pdfpage;
    }

    private Object createStreamFromPDFBoxPage(PDFPage pdfpage, PDFDocument pdfdoc, PDDocument doc,
                                                  Rectangle destRect, AffineTransform generatorAT) throws IOException {
        pdfdoc.assignObjectNumber(pdfpage);
        pdfpage.setDocument(pdfdoc);
        PDFBoxAdapter adapter = new PDFBoxAdapter(pdfpage, new HashMap<>(), new HashMap<Integer, PDFArray>());
        PDPage page = doc.getPage(0);
        AffineTransform pageAdjust = new AffineTransform();
        Object object = adapter.createStreamFromPDFBoxPage(doc, page, "key", pageAdjust, null, destRect, generatorAT);
        doc.close();

        return object;
    }

    @Test
    public void testPDFBoxImageHandler() throws Exception {
        ImageInfo imgi = new ImageInfo("a", "b");
        PDDocument doc = load(SHADING);
        ImagePDF img = new ImagePDF(imgi, doc);
        PDFDocument pdfdoc = new PDFDocument("");
        PDFPage pdfpage = getPDFPage(pdfdoc);
        pdfpage.setDocument(pdfdoc);
        PDFGState g = new PDFGState();
        pdfdoc.assignObjectNumber(g);
        pdfpage.addGState(g);
        PDFContentGenerator con = new PDFContentGenerator(pdfdoc, null, null);
        FOUserAgent mockedAgent = mock(FOUserAgent.class);
        when(mockedAgent.isAccessibilityEnabled()).thenReturn(false);
        when(mockedAgent.getPDFObjectCache()).thenReturn(new SoftMapCache(true));
        PDFRenderingContext c = new PDFRenderingContext(mockedAgent, con, pdfpage, null);
        c.setPageNumbers(new HashMap<Integer, PDFArray>());
        new PDFBoxImageHandler().handleImage(c, img, new Rectangle(0, 0, 100, 100));
        PDFResources res = c.getPage().getPDFResources();
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        res.output(bos);
        Assert.assertTrue(bos.toString(StandardCharsets.UTF_8.name()).contains("/ExtGState << /GS1"));
    }

    @Test
    public void testPDFCache() throws IOException {
        LoadPDFWithCache loadPDFWithCache = new LoadPDFWithCache();
        loadPDFWithCache.run(LOOP);

        Object item = loadPDFWithCache.pdfCache.values().iterator().next();
        assertEquals(item.getClass(), PDFStream.class);
        item = loadPDFWithCache.pdfCache.keySet().iterator().next();
        assertEquals(item.getClass(), Integer.class);
        assertEquals(loadPDFWithCache.pdfCache.size(), 12);

        Iterator<Object> iterator = loadPDFWithCache.objectCachePerFile.values().iterator();
        iterator.next();
        item = iterator.next();
        assertEquals(item.getClass(), PDFDictionary.class);
        item = loadPDFWithCache.objectCachePerFile.keySet().iterator().next();
        assertEquals(item.getClass(), String.class);
        assertEquals(loadPDFWithCache.objectCachePerFile.size(), 46);
    }

    @Test
    public void testPDFCache2() throws IOException {
        LoadPDFWithCache loadPDFWithCache = new LoadPDFWithCache();
        String stream = loadPDFWithCache.run(LOOP);
        String cachedStream = (String) loadPDFWithCache.objectCachePerFile.get(LOOP);
        Assert.assertTrue(cachedStream.contains("EMC"));
        Assert.assertTrue(stream.endsWith(cachedStream));
    }

    private static class LoadPDFWithCache {
        private PDFDocument pdfdoc = new PDFDocument("");
        private Map<Object, Object> pdfCache = new LinkedHashMap<Object, Object>();
        private Map<Object, Object> objectCachePerFile = new LinkedHashMap<Object, Object>();
        private String run(String pdf) throws IOException {
            PDFPage pdfpage = getPDFPage(pdfdoc);
            pdfdoc.assignObjectNumber(pdfpage);
            pdfpage.setDocument(pdfdoc);
            PDFBoxAdapter adapter = new PDFBoxAdapter(
                    pdfpage, objectCachePerFile, null, new HashMap<Integer, PDFArray>(), pdfCache);
            PDDocument doc = load(pdf);
            PDPage page = doc.getPage(0);
            String stream = (String) adapter.createStreamFromPDFBoxPage(
                    doc, page, pdf, new AffineTransform(), null, new Rectangle(), new AffineTransform());
            doc.close();
            return stream;
        }
    }

    @Test
    public void testPDFSize() throws IOException {
        LoadPDFWithCache loadPDFWithCache = new LoadPDFWithCache();
        loadPDFWithCache.run(ANNOT);
        loadPDFWithCache.run(ANNOT);
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        loadPDFWithCache.pdfdoc.output(bos);
        assertEquals(loadPDFWithCache.pdfCache.size(), 2);
        Assert.assertTrue(bos.size() <= 6418);
    }

    @Test
    public void testErrorMsgToPS() throws IOException {
        PDDocument doc = new PDDocument();
        PDPage page = new PDPage();
        page.setContents(new PDStream(doc, new ByteArrayInputStream("<".getBytes(StandardCharsets.UTF_8))));
        doc.addPage(page);
        RuntimeException ex = Assert.assertThrows(RuntimeException.class, () -> pdfToPCL(doc, ERROR));
        Assert.assertTrue(ex.getMessage().startsWith("Error while painting PDF page: " + ERROR));
    }

    @Test
    public void testErrorMsgToPDF() {
        PDFDocument pdfdoc = new PDFDocument("");
        PDFContentGenerator contentGenerator = new PDFContentGenerator(pdfdoc, null, null);
        PDFRenderingContext context = new PDFRenderingContext(null, contentGenerator, null, null);
        ImagePDF imagePDF = new ImagePDF(new ImageInfo(ERROR, null), null);
        RuntimeException ex = Assert.assertThrows(RuntimeException.class, () ->
                new PDFBoxImageHandler().handleImage(context, imagePDF, null));
        Assert.assertTrue(ex.getMessage().startsWith("Error on PDF page: " + ERROR));
    }

    @Test
    public void testNoPageResource() throws IOException {
        PDDocument doc = load(FontMergeTestCase.CFF1);
        PDPage page = doc.getPage(0);
        page.setResources(null);
        AffineTransform pageAdjust = new AffineTransform();
        getPDFBoxAdapter(false, false).createStreamFromPDFBoxPage(
                doc, page, FontMergeTestCase.CFF1, pageAdjust, new FontInfo(), new Rectangle(), pageAdjust);
        doc.close();
    }

    @Test
    public void testPDFBoxImageHandlerAccessibilityEnabled() throws Exception {
        ImageInfo imgi = new ImageInfo("a", "b");
        PDDocument doc = load(SHADING);
        ImagePDF img = new ImagePDF(imgi, doc);
        PDFDocument pdfdoc = new PDFDocument("");
        PDFPage pdfpage = getPDFPage(pdfdoc);
        pdfpage.setDocument(pdfdoc);
        PDFContentGenerator con = new PDFContentGenerator(pdfdoc, null, null);
        FOUserAgent mockedAgent = mock(FOUserAgent.class);
        when(mockedAgent.isAccessibilityEnabled()).thenReturn(true);
        when(mockedAgent.getPDFObjectCache()).thenReturn(new SoftMapCache(true));
        PDFRenderingContext c = new PDFRenderingContext(mockedAgent, con, pdfpage, null);
        c.setPageNumbers(new HashMap<Integer, PDFArray>());
        new PDFBoxImageHandler().handleImage(c, img, new Rectangle());
    }

    @Test
    public void testFormXObject() throws IOException {
        PDDocument doc = load(IMAGE);
        PDPage page = doc.getPage(0);
        AffineTransform pageAdjust = new AffineTransform();
        PDFFormXObject formXObject = (PDFFormXObject) getPDFBoxAdapter(false, true)
                .createStreamFromPDFBoxPage(doc, page, IMAGE, pageAdjust, new FontInfo(), new Rectangle(), pageAdjust);
        doc.close();
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        formXObject.output(bos);
        Assert.assertTrue(bos.toString(StandardCharsets.UTF_8.name()).contains("/Type /XObject"));
    }

    @Test
    public void testRewriteOfForms() throws IOException {
        Assert.assertTrue(getPDFToPDF(ACCESSIBLERADIOBUTTONS).contains("/F15106079 12 Tf"));
    }

    private String getPDFToPDF(String pdf) throws IOException {
        PDFDocument pdfdoc = new PDFDocument("");
        PDFPage pdfpage = getPDFPage(pdfdoc);
        pdfpage.setDocument(pdfdoc);
        pdfpage.setObjectNumber(1);
        PDFBoxAdapter adapter = new PDFBoxAdapter(pdfpage, new HashMap<>(), new HashMap<Integer, PDFArray>());
        PDDocument doc = load(pdf);
        PDPage page = doc.getPage(0);
        AffineTransform pageAdjust = new AffineTransform();
        Rectangle r = new Rectangle(0, 1650, 842000, 595000);
        adapter.createStreamFromPDFBoxPage(doc, page, "key", pageAdjust, null, r, pageAdjust);
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        Map<String, List<String>> filterMap = new HashMap<String, List<String>>();
        List<String> filterList = new ArrayList<String>();
        filterList.add("null");
        filterMap.put("default", filterList);
        pdfdoc.setFilterMap(filterMap);
        pdfdoc.output(os);
        doc.close();
        return os.toString(StandardCharsets.UTF_8.name());
    }

    @Test
    public void testRewriteOfPatternForms() throws IOException {
        Assert.assertTrue(getPDFToPDF(PATTERN).contains("/R1106079 gs\n1 1 m"));
    }

    @Test
    public void testDCTEncryption() throws IOException {
        PDFDocument pdfdoc = new PDFDocument("");
        pdfdoc.setEncryption(new PDFEncryptionParams());
        loadPage(pdfdoc, IMAGE);
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        pdfdoc.output(bos);
        Assert.assertTrue(bos.toString(StandardCharsets.UTF_8.name()).contains("/Filter /DCTDecode"));
    }

    @Test
    public void testFormRotated() throws IOException {
        PDFDocument pdfdoc = new PDFDocument("");
        loadPage(pdfdoc, FORMROTATED);
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        pdfdoc.output(bos);
        Assert.assertFalse(bos.toString(StandardCharsets.UTF_8.name()).contains("/R 90"));
    }

    private String getAnnotationsID(PDFPage page) throws IOException {
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        page.getAnnotations().output(os);
        return os.toString(StandardCharsets.UTF_8.name()).split("\n")[1];
    }

    private PDFPage drawAnnot(PDFDocument pdfDoc, Map<String, Object> usedFields, String pdf) throws Exception {
        PDFBoxAdapter adapter = getPDFBoxAdapter(pdfDoc, false, false, true, usedFields);
        PDDocument input = load(pdf);
        PDPage srcPage = input.getPage(0);
        AffineTransform pageAdjust = new AffineTransform();
        Rectangle r = new Rectangle(0, 1650, 842000, 595000);
        adapter.createStreamFromPDFBoxPage(input, srcPage, "key", pageAdjust, null, r, pageAdjust);
        input.close();
        return adapter.getTargetPage();
    }

    @Test
    public void testMergeAnnotsTree() throws Exception {
        PDFDocument pdfDoc = new PDFDocument("");
        Map<String, Object> usedFields = new HashMap<>();
        PDFPage page1 = drawAnnot(pdfDoc, usedFields, ANNOT2);
        PDFPage page2 = drawAnnot(pdfDoc, usedFields, ANNOT3);
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        pdfDoc.outputHeader(os);
        pdfDoc.output(os);
        pdfDoc.outputTrailer(os);
        PDDocument out = Loader.loadPDF(os.toByteArray());
        out.close();
        String id1 = "30 0 R";
        assertEquals(getAnnotationsID(page1), id1);
        String id2 = "33 0 R";
        assertEquals(getAnnotationsID(page2), id2);
        String outStr = os.toString(StandardCharsets.UTF_8.name()).replaceAll("\\s\\s/", "/");
        Assert.assertTrue(outStr.contains("<< /Kids [32 0 R] /T ([Signer1) >>"));
        Assert.assertTrue(outStr.contains("<<\n"
                + "/Kids [" + id1 + " " + id2 + "]\n"
                + "/Parent 34 0 R\n"
                + "/T (Fullname1)\n"
                + ">>"));
    }

    @Test
    public void testPreservePropertyNames() throws Exception {
        PDDocument doc = load(FontMergeTestCase.CFF1);
        COSDictionary properties = new COSDictionary();
        properties.setItem(COSName.S, COSName.S);
        doc.getPage(0).getResources().getCOSObject().setItem(COSName.PROPERTIES, properties);
        PDFDocument pdfdoc = new PDFDocument("");
        PDFPage page = loadPage(pdfdoc, doc, new Rectangle());
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        page.getPDFResources().output(bos);
        Assert.assertTrue(bos.toString(StandardCharsets.UTF_8.name()).contains("/Properties << /S /S >>"));
    }

    @Test
    public void testPatternMatrix() throws Exception {
        PDDocument doc = load(SHADING);
        PDFDocument pdfdoc = new PDFDocument("");
        Rectangle destRect = new Rectangle(0, 1650, 274818, 174879);
        loadPage(pdfdoc, doc, destRect);
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        pdfdoc.output(bos);
        String outStr = removeWhiteSpace(bos);
        Assert.assertTrue(outStr.contains("<<\n"
                + "/Type /Pattern\n"
                + "/PatternType 2\n"
                + "/Shading 2 0 R\n"
                + "/Matrix [53.8858833313 0 0 -26.4968185425 72.7459411621 -20.8601989746]\n"
                + ">>"));
    }

    @Test
    public void testPatternMatrixWithPageAdjust() throws Exception {
        PDDocument doc = load(SHADING);
        PDFDocument pdfdoc = new PDFDocument("");
        Rectangle destRect = new Rectangle(0, 1650, 274818, 174879);
        PDFPage pdfpage = getPDFPage(pdfdoc);
        AffineTransform generatorAT = AffineTransform.getTranslateInstance(0, 100);
        createStreamFromPDFBoxPage(pdfpage, pdfdoc, doc, destRect, generatorAT);
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        pdfdoc.output(bos);
        String outStr = removeWhiteSpace(bos);
        Assert.assertTrue(outStr.contains("<<\n"
                + "/Type /Pattern\n/PatternType 2\n/Shading 2 0 R\n"
                + "/Matrix [53.8858833313 0 0 -26.4968185425 72.7459411621 -120.8601837158]\n"
                + ">>"));
    }

    @Test
    public void testPatternMatrixFormXObject() throws Exception {
        PDDocument doc = load(SHADING);
        PDFDocument pdfdoc = new PDFDocument("");
        pdfdoc.setFormXObjectEnabled(true);
        Rectangle destRect = new Rectangle(0, 1650, 274818, 174879);
        loadPage(pdfdoc, doc, destRect);
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        pdfdoc.output(bos);
        String outStr = removeWhiteSpace(bos);
        Assert.assertTrue(outStr.contains("/Pattern << /Pa1 12 0 R /Pa2 13 0 R >>"));
        Assert.assertTrue(outStr.contains("<<\n"
                + "/Type /Pattern\n"
                + "/PatternType 2\n"
                + "/Shading 2 0 R\n"
                + "/Matrix [120 0 0 -120 162 705]\n"
                + ">>"));
    }

    @Test
    public void testGetFormXObject() throws Exception {
        defaultCreateStreamFromPDFBoxPage(0, 0.083, 0, -100, 0, 0.003, -3.424);
        defaultCreateStreamFromPDFBoxPage(90, 0, 0.003, -51.712, -0.083, 0, 49.287);
        defaultCreateStreamFromPDFBoxPage(180, -0.083, 0, 1, 0, -0.003, 0.999);
        defaultCreateStreamFromPDFBoxPage(270, 0, -0.003, -47.287, 0.083, 0, -51.712);
    }

    private void defaultCreateStreamFromPDFBoxPage(int rotation, double scaleX, double shearX,
                                                   double translateX, double shearY, double scaleY,
                                                   double translateY) throws IOException {
        PDFDocument pdfdoc = new PDFDocument("");
        pdfdoc.setFormXObjectEnabled(true);
        PDFPage pdfPage = getPDFPage(pdfdoc);
        PDDocument pdDoc = load(SHADING);
        PDPage page = pdDoc.getPage(0);
        page.setCropBox(new PDRectangle(600, 500, 1200, 800));
        page.setRotation(rotation);

        PDFFormXObject form = (PDFFormXObject) createStreamFromPDFBoxPage(pdfPage, pdfdoc, pdDoc,
                new Rectangle(0, 0, 274818, 174879), new AffineTransform());

        AffineTransform at = form.getMatrix();
        String message = "Value must be calculated based on the rotation";
        assertEquals(message, scaleX, at.getScaleX(), 0.001); //m00
        assertEquals(message, shearX, at.getShearX(), 0.001); //m01
        assertEquals(message, translateX, at.getTranslateX(), 0.001); //m02
        assertEquals(message, shearY, at.getShearY(), 0.001); //m10
        assertEquals(message, scaleY, at.getScaleY(), 0.001); //m11
        assertEquals(message, translateY, at.getTranslateY(), 0.001); //m12
    }

    private String removeWhiteSpace(ByteArrayOutputStream bos) throws Exception {
        return bos.toString(StandardCharsets.UTF_8.name()).replaceAll("\\s\\s/", "/");
    }

    @Test
    public void testSoftMask() throws Exception {
        ByteArrayOutputStream bos = pdfToPS(SOFTMASK);
        String output = bos.toString(StandardCharsets.UTF_8.name());
        assertEquals(output.split("BeginBitmap").length, 3);
        Assert.assertTrue(output.contains("/ImageMatrix [148 0 0 78 0 0]"));
        Assert.assertTrue(output.contains("/ImageMatrix [192 0 0 192 0 0]"));
    }
}
