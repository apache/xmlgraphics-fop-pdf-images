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

package org.apache.fop.render.pdf;

import java.awt.Rectangle;
import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D;
import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.Assert;
import org.junit.Test;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.apache.commons.io.IOUtils;
import org.apache.fontbox.cff.CFFParser;
import org.apache.fontbox.ttf.TTFParser;
import org.apache.fontbox.type1.Type1Font;
import org.apache.pdfbox.cos.COSArray;
import org.apache.pdfbox.cos.COSDictionary;
import org.apache.pdfbox.cos.COSInteger;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.cos.COSObject;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.font.PDCIDFontType2;
import org.apache.pdfbox.pdmodel.font.PDType0Font;

import org.apache.xmlgraphics.image.loader.ImageException;
import org.apache.xmlgraphics.image.loader.ImageInfo;
import org.apache.xmlgraphics.image.loader.impl.ImageGraphics2D;
import org.apache.xmlgraphics.image.loader.util.SoftMapCache;
import org.apache.xmlgraphics.java2d.GeneralGraphics2DImagePainter;
import org.apache.xmlgraphics.java2d.GraphicContext;
import org.apache.xmlgraphics.ps.PSGenerator;

import org.apache.fop.apps.FOUserAgent;
import org.apache.fop.fonts.CustomFont;
import org.apache.fop.fonts.FontInfo;
import org.apache.fop.fonts.FontType;
import org.apache.fop.fonts.MultiByteFont;
import org.apache.fop.fonts.Typeface;
import org.apache.fop.pdf.PDFAnnotList;
import org.apache.fop.pdf.PDFArray;
import org.apache.fop.pdf.PDFDictionary;
import org.apache.fop.pdf.PDFDocument;
import org.apache.fop.pdf.PDFFilterList;
import org.apache.fop.pdf.PDFFormXObject;
import org.apache.fop.pdf.PDFGState;
import org.apache.fop.pdf.PDFPage;
import org.apache.fop.pdf.PDFResources;
import org.apache.fop.pdf.PDFStream;
import org.apache.fop.render.pcl.PCLGenerator;
import org.apache.fop.render.pcl.PCLGraphics2D;
import org.apache.fop.render.pdf.pdfbox.ImageConverterPDF2G2D;
import org.apache.fop.render.pdf.pdfbox.ImagePDF;
import org.apache.fop.render.pdf.pdfbox.MergeTTFonts;
import org.apache.fop.render.pdf.pdfbox.PDFBoxAdapter;
import org.apache.fop.render.pdf.pdfbox.PDFBoxImageHandler;
import org.apache.fop.render.pdf.pdfbox.PSPDFGraphics2D;
import org.apache.fop.render.ps.PSDocumentHandler;
import org.apache.fop.render.ps.PSImageFormResource;
import org.apache.fop.render.ps.PSRenderingUtil;

public class PDFBoxAdapterTestCase {
    protected static final String CFF1 = "test/resources/2fonts.pdf";
    protected static final String CFF2 = "test/resources/2fonts2.pdf";
    protected static final String CFF3 = "test/resources/simpleh.pdf";
    protected static final String TTCID1 = "test/resources/ttcid1.pdf";
    protected static final String TTCID2 = "test/resources/ttcid2.pdf";
    protected static final String TTSubset1 = "test/resources/ttsubset.pdf";
    protected static final String TTSubset2 = "test/resources/ttsubset2.pdf";
    private static final String TTSubset3 = "test/resources/ttsubset3.pdf";
    private static final String TTSubset5 = "test/resources/ttsubset5.pdf";
    protected static final String CFFCID1 = "test/resources/cffcid1.pdf";
    private static final String CFFCID2 = "test/resources/cffcid2.pdf";
    protected static final String Type1Subset1 = "test/resources/t1subset.pdf";
    protected static final String Type1Subset2 = "test/resources/t1subset2.pdf";
    private static final String Type1Subset3 = "test/resources/t1subset3.pdf";
    private static final String Type1Subset4 = "test/resources/t1subset4.pdf";
    protected static final String ROTATE = "test/resources/rotate.pdf";
    private static final String ANNOT = "test/resources/annot.pdf";
    private static final String SHADING = "test/resources/shading.pdf";
    private static final String LINK = "test/resources/link.pdf";
    private static final String IMAGE = "test/resources/image.pdf";
    private static final String HELLOTagged = "test/resources/taggedWorld.pdf";
    private static final String XFORM = "test/resources/xform.pdf";
    private static final String LOOP = "test/resources/loop.pdf";
    private static final String ERROR = "test/resources/error.pdf";
    private static final String LIBREOFFICE = "test/resources/libreoffice.pdf";
    private static final String SMASK = "test/resources/smask.pdf";
    private static final String TYPE0TT = "test/resources/type0tt.pdf";
    private static final String TYPE0CFF = "test/resources/type0cff.pdf";
    private static final String ACCESSIBLERADIOBUTTONS = "test/resources/accessibleradiobuttons.pdf";

    private static PDFPage getPDFPage(PDFDocument doc) {
        final Rectangle2D r = new Rectangle2D.Double();
        return new PDFPage(new PDFResources(doc), 0, r, r, r, r);
    }

    protected static PDFBoxAdapter getPDFBoxAdapter(boolean mergeFonts, boolean formXObject) {
        PDFDocument doc = new PDFDocument("");
        PDFPage pdfpage = getPDFPage(doc);
        doc.setMergeFontsEnabled(mergeFonts);
        doc.setFormXObjectEnabled(formXObject);
        pdfpage.setDocument(doc);
        pdfpage.setObjectNumber(1);
        return new PDFBoxAdapter(pdfpage, new HashMap(), new HashMap<Integer, PDFArray>());
    }

    @Test
    public void testPDFWriter() throws Exception {
        FontInfo fi = new FontInfo();
        String msg = writeText(fi, CFF3);
        Assert.assertTrue(msg, msg.contains("/Myriad_Pro"));
        Assert.assertEquals(fi.getUsedFonts().size(), 2);
        msg = writeText(fi, TTSubset1);
        Assert.assertTrue(msg, msg.contains("<74>-0.168 <65>-0.1523 <73>0.1528 <74>277.832"));
        msg = writeText(fi, TTSubset2);
        Assert.assertTrue(msg, msg.contains("(t)-0.168 (e)-0.1523 (s)0.1528 (t)"));
        msg = writeText(fi, TTSubset3);
        Assert.assertTrue(msg, msg.contains("[<0001>3 <0002>-7 <0003>] TJ"));
        msg = writeText(fi, TTSubset5);
        Assert.assertTrue(msg, msg.contains("[<0003>2 <0004>-7 <0007>] TJ"));
        msg = writeText(fi, TTCID1);
        Assert.assertTrue(msg, msg.contains("<0028003B0034003000420034>"));
        msg = writeText(fi, TTCID2);
        Assert.assertTrue(msg, msg.contains("<000F00100001002A0034003F00430034003C00310034004100010010000E000F0011>"));
        msg = writeText(fi, CFFCID1);
        Assert.assertTrue(msg, msg.contains("/Fm01700251251 Do"));
        msg = writeText(fi, CFFCID2);
        Assert.assertTrue(msg, msg.contains("/Fm01701174772 Do"));
        msg = writeText(fi, Type1Subset1);
        Assert.assertTrue(msg, msg.contains("/Verdana_Type1"));
        msg = writeText(fi, Type1Subset2);
        Assert.assertTrue(msg, msg.contains("[(2nd example)] TJ"));
        msg = writeText(fi, Type1Subset3);
        Assert.assertTrue(msg, msg.contains("/URWChanceryL-MediItal_Type1 20 Tf"));
        msg = writeText(fi, Type1Subset4);
        Assert.assertTrue(msg, msg.contains("/F15-1521012718 40 Tf"));
        parseFonts(fi);
    }

    @Test
    public void testMergeTTCFF() throws IOException {
        FontInfo fi = new FontInfo();
        writeText(fi, TYPE0TT);
        writeText(fi, TYPE0CFF);
        parseFonts(fi);
    }

    @Test
    public void testMergeTT() throws IOException {
        PDDocument doc = PDDocument.load(new File(TYPE0TT));
        PDType0Font type0Font = (PDType0Font) doc.getPage(0).getResources().getFont(COSName.getPDFName("C2_0"));
        PDCIDFontType2 ttf = (PDCIDFontType2) type0Font.getDescendantFont();
        InputStream originalData = ttf.getTrueTypeFont().getOriginalData();
        byte[] originalDataBytes = IOUtils.toByteArray(originalData);
        doc.close();

        MergeTTFonts mergeTTFonts = new MergeTTFonts(null);
        Map<Integer, Integer> map = new HashMap<Integer, Integer>();
        map.put(0, 0);
        mergeTTFonts.readFont(new ByteArrayInputStream(originalDataBytes), null, null, map, true);
        byte[] mergedData = mergeTTFonts.getMergedFontSubset();
        Assert.assertArrayEquals(mergedData, originalDataBytes);
    }

    private void parseFonts(FontInfo fi) throws IOException {
        for (Typeface font : fi.getUsedFonts().values()) {
            InputStream is = ((CustomFont) font).getInputStream();
            if (font.getFontType() == FontType.TYPE1C || font.getFontType() == FontType.CIDTYPE0) {
                byte[] data = IOUtils.toByteArray(is);
                CFFParser p = new CFFParser();
                p.parse(data);
            } else if (font.getFontType() == FontType.TRUETYPE) {
                TTFParser parser = new TTFParser();
                parser.parse(is);
            } else if (font.getFontType() == FontType.TYPE0) {
                TTFParser parser = new TTFParser(true);
                parser.parse(is);
            } else if (font.getFontType() == FontType.TYPE1) {
                Type1Font.createWithPFB(is);
            }
            Assert.assertTrue(((CustomFont) font).isEmbeddable());
            if (font instanceof MultiByteFont) {
                Assert.assertTrue(((MultiByteFont) font).getWidthsMap() != null);
            } else {
                Assert.assertFalse(((CustomFont)font).isSymbolicFont());
            }
        }
    }

    private String writeText(FontInfo fi, String pdf) throws IOException {
        PDDocument doc = PDDocument.load(new File(pdf));
        PDPage page = doc.getPage(0);
        AffineTransform at = new AffineTransform();
        String c = (String) getPDFBoxAdapter(true, false)
                .createStreamFromPDFBoxPage(doc, page, pdf, at, fi, new Rectangle());
        doc.close();
        return c;
    }

    @Test
    public void testTaggedPDFWriter() throws IOException {
        PDFBoxAdapter adapter = getPDFBoxAdapter(false, false);
        adapter.setCurrentMCID(5);
        PDDocument doc = PDDocument.load(new File(HELLOTagged));
        PDPage page = doc.getPage(0);
        AffineTransform at = new AffineTransform();
        Rectangle r = new Rectangle(0, 1650, 842000, 595000);
        String stream = (String) adapter.createStreamFromPDFBoxPage(doc, page, "key", at, null, r);
        Assert.assertTrue(stream, stream.contains("/P <</MCID 5 >>BDC"));
        doc.close();
    }

    @Test
    public void testAnnot() throws Exception {
        PDFDocument pdfdoc = new PDFDocument("");
        PDFPage pdfpage = getPDFPage(pdfdoc);
        pdfpage.setDocument(pdfdoc);
        pdfpage.setObjectNumber(1);
        PDFBoxAdapter adapter = new PDFBoxAdapter(pdfpage, new HashMap(), new HashMap<Integer, PDFArray>());
        PDDocument doc = PDDocument.load(new File(ANNOT));
        PDPage page = doc.getPage(0);
        AffineTransform at = new AffineTransform();
        Rectangle r = new Rectangle(0, 1650, 842000, 595000);
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        pdfdoc.output(os);
        os.reset();
        adapter.createStreamFromPDFBoxPage(doc, page, "key", at, null, r);
        pdfdoc.outputTrailer(os);
        Assert.assertTrue(os.toString("UTF-8").contains("/Fields ["));
        doc.close();
    }

    @Test
    public void testAnnot2() throws Exception {
        PDFBoxAdapter adapter = getPDFBoxAdapter(false, false);
        PDDocument doc = PDDocument.load(new File(ANNOT));
        PDPage page = doc.getPage(0);
        COSArray annots = (COSArray) page.getCOSObject().getDictionaryObject(COSName.ANNOTS);
        COSDictionary dict = (COSDictionary) ((COSObject)annots.get(0)).getObject();
        dict.setItem(COSName.PARENT, COSInteger.ONE);

        AffineTransform at = new AffineTransform();
        Rectangle r = new Rectangle(0, 1650, 842000, 595000);
        adapter.createStreamFromPDFBoxPage(doc, page, "key", at, null, r);
        doc.close();
    }


    @Test
    public void testAnnot3() throws Exception {
        PDFDocument pdfdoc = new PDFDocument("");
        PDFPage pdfpage = getPDFPage(pdfdoc);
        pdfpage.setDocument(pdfdoc);
        pdfpage.setObjectNumber(1);
        PDFBoxAdapter adapter = new PDFBoxAdapter(pdfpage, new HashMap(), new HashMap<Integer, PDFArray>());
        PDDocument doc = PDDocument.load(new File(ACCESSIBLERADIOBUTTONS));
        PDPage page = doc.getPage(0);
        AffineTransform at = new AffineTransform();
        Rectangle r = new Rectangle(0, 1650, 842000, 595000);
        adapter.createStreamFromPDFBoxPage(doc, page, "key", at, null, r);
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        pdfdoc.output(os);
        String out = os.toString("UTF-8");
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
        PDFBoxAdapter adapter = new PDFBoxAdapter(pdfpage, new HashMap(), new HashMap<Integer, PDFArray>());
        PDDocument doc = PDDocument.load(new File(ACCESSIBLERADIOBUTTONS));
        COSArray fields = (COSArray)
                doc.getDocumentCatalog().getAcroForm().getCOSObject().getDictionaryObject(COSName.FIELDS);
        fields.remove(0);
        PDPage page = doc.getPage(0);
        AffineTransform at = new AffineTransform();
        Rectangle r = new Rectangle(0, 1650, 842000, 595000);
        adapter.createStreamFromPDFBoxPage(doc, page, "key", at, null, r);
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        pdfdoc.outputTrailer(os);
        Assert.assertTrue(os.toString("UTF-8").contains("/Fields []"));
        doc.close();
    }

    @Test
    public void testLink() throws Exception {
        PDFDocument pdfdoc = new PDFDocument("");
        PDFPage pdfpage = getPDFPage(pdfdoc);
        pdfpage.setDocument(pdfdoc);
        pdfpage.setObjectNumber(1);
        Map<Integer, PDFArray> pageNumbers = new HashMap<Integer, PDFArray>();
        PDFBoxAdapter adapter = new PDFBoxAdapter(pdfpage, new HashMap(), pageNumbers);
        PDDocument doc = PDDocument.load(new File(LINK));
        PDPage page = doc.getPage(0);
        AffineTransform at = new AffineTransform();
        Rectangle r = new Rectangle(0, 1650, 842000, 595000);
        String stream = (String) adapter.createStreamFromPDFBoxPage(doc, page, "key", at, null, r);
        Assert.assertTrue(stream.contains("/Link <</MCID 5 >>BDC"));
        Assert.assertEquals(pageNumbers.size(), 4);
        PDFAnnotList annots = (PDFAnnotList) pdfpage.get("Annots");
        Assert.assertEquals(annots.toPDFString(), "[\n1 0 R\n2 0 R\n]");
        doc.close();
    }

    @Test
    public void testXform() throws Exception {
        PDFDocument pdfdoc = new PDFDocument("");
        pdfdoc.getFilterMap().put(PDFFilterList.DEFAULT_FILTER, Collections.singletonList("null"));
        pdfdoc.setMergeFontsEnabled(true);
        PDFPage pdfpage = getPDFPage(pdfdoc);
        pdfpage.setDocument(pdfdoc);
        pdfpage.setObjectNumber(1);
        Map<Integer, PDFArray> pageNumbers = new HashMap<Integer, PDFArray>();
        PDFBoxAdapter adapter = new PDFBoxAdapter(pdfpage, new HashMap(), pageNumbers);
        PDDocument doc = PDDocument.load(new File(XFORM));
        PDPage page = doc.getPage(0);
        AffineTransform at = new AffineTransform();
        Rectangle r = new Rectangle(0, 1650, 842000, 595000);
        adapter.createStreamFromPDFBoxPage(doc, page, "key", at, new FontInfo(), r);
        doc.close();
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        pdfdoc.output(bos);
        Assert.assertFalse(bos.toString("UTF-8").contains("/W 5 /H 5 /BPC 8 /CS /RGB ID ÿÿÿ"));
    }

    @Test
    public void testPSPDFGraphics2D() throws Exception {
        ByteArrayOutputStream stream = pdfToPS(IMAGE);
        Assert.assertEquals(countString(stream.toString("UTF-8"), "%AXGBeginBitmap:"), 1);

        pdfToPS(CFF1);
        pdfToPS(CFF2);
        pdfToPS(CFF3);
        pdfToPS(TTCID1);
        pdfToPS(TTCID2);
        pdfToPS(TTSubset1);
        pdfToPS(TTSubset2);
        pdfToPS(TTSubset3);
        pdfToPS(TTSubset5);
        stream = pdfToPS(CFFCID1);
        Assert.assertEquals(countString(stream.toString("UTF-8"), "%AXGBeginBitmap:"), 2);
        pdfToPS(CFFCID2);
        pdfToPS(Type1Subset1);
        pdfToPS(Type1Subset2);
        pdfToPS(Type1Subset3);
        pdfToPS(Type1Subset4);
        pdfToPS(ROTATE);
        pdfToPS(LINK);
        pdfToPS(LOOP);
        stream = pdfToPS(LIBREOFFICE);
        Assert.assertTrue(stream.toString("UTF-8").contains("/MaskColor [ 255 255 255 ]"));

    }

    private int countString(String s, String value) {
        return s.split(value).length - 1;
    }

    @Test
    public void testPDFToPDF() throws IOException {
        FontInfo fi = new FontInfo();
        writeText(fi, CFF1);
        writeText(fi, CFF2);
        writeText(fi, CFF3);
        writeText(fi, CFFCID1);
        writeText(fi, CFFCID2);
        writeText(fi, IMAGE);
        writeText(fi, LINK);
        writeText(fi, ROTATE);
        writeText(fi, SHADING);
        writeText(fi, TTCID1);
        writeText(fi, TTCID2);
        writeText(fi, TTSubset1);
        writeText(fi, TTSubset2);
        writeText(fi, TTSubset3);
        writeText(fi, TTSubset5);
        writeText(fi, Type1Subset1);
        writeText(fi, Type1Subset2);
        writeText(fi, Type1Subset3);
        writeText(fi, Type1Subset4);
        writeText(fi, LOOP);
    }

    private ByteArrayOutputStream pdfToPS(String pdf) throws IOException, ImageException {
        ImageConverterPDF2G2D i = new ImageConverterPDF2G2D();
        ImageInfo imgi = new ImageInfo(pdf, "b");
        PDDocument doc = PDDocument.load(new File(pdf));
        org.apache.xmlgraphics.image.loader.Image img = new ImagePDF(imgi, doc);
        ImageGraphics2D ig = (ImageGraphics2D)i.convert(img, null);
        GeneralGraphics2DImagePainter g = (GeneralGraphics2DImagePainter) ig.getGraphics2DImagePainter();
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        PSPDFGraphics2D g2d = (PSPDFGraphics2D) g.getGraphics(true, new FOPPSGeneratorImpl(stream));
        Rectangle2D rect = new Rectangle2D.Float(0, 0, 100, 100);
        GraphicContext gc = new GraphicContext();
        g2d.setGraphicContext(gc);
        ig.getGraphics2DImagePainter().paint(g2d, rect);
        doc.close();
        return stream;
    }

    @Test
    public void testSmask() throws IOException, ImageException {
        ByteArrayOutputStream ps = pdfToPS(SMASK);
        Assert.assertTrue(ps.toString("UTF-8").contains("/Pattern"));
        Assert.assertTrue(ps.toString("UTF-8").contains("{<\nf1f1f1"));
    }

    @Test
    public void testPCL() throws IOException, ImageException {
        String ex = "";
        try {
            pdfToPCL(SHADING);
        } catch (UnsupportedOperationException e) {
            ex = e.getMessage();
        }
        Assert.assertTrue(ex.contains("Clipping is not supported."));
    }

    private void pdfToPCL(String pdf) throws IOException, ImageException {
        ImageConverterPDF2G2D i = new ImageConverterPDF2G2D();
        ImageInfo imgi = new ImageInfo(pdf, "b");
        PDDocument doc = PDDocument.load(new File(pdf));
        org.apache.xmlgraphics.image.loader.Image img = new ImagePDF(imgi, doc);
        ImageGraphics2D ig = (ImageGraphics2D)i.convert(img, null);
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        PCLGraphics2D g2d = new PCLGraphics2D(new PCLGenerator(stream));
        Rectangle2D rect = new Rectangle2D.Float(0, 0, 100, 100);
        GraphicContext gc = new GraphicContext();
        g2d.setGraphicContext(gc);
        ig.getGraphics2DImagePainter().paint(g2d, rect);
        doc.close();
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

    @Test
    public void testPDFBoxImageHandler() throws Exception {
        ImageInfo imgi = new ImageInfo("a", "b");
        PDDocument doc = PDDocument.load(new File(SHADING));
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
        new PDFBoxImageHandler().handleImage(c, img, new Rectangle());
        PDFResources res = c.getPage().getPDFResources();
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        res.output(bos);
        Assert.assertTrue(bos.toString("UTF-8").contains("/ExtGState << /GS1"));
    }

    @Test
    public void testPDFCache() throws IOException {
        LoadPDFWithCache loadPDFWithCache = new LoadPDFWithCache();
        loadPDFWithCache.run(LOOP);

        Object item = loadPDFWithCache.pdfCache.values().iterator().next();
        Assert.assertEquals(item.getClass(), PDFStream.class);
        item = loadPDFWithCache.pdfCache.keySet().iterator().next();
        Assert.assertEquals(item.getClass(), Integer.class);
        Assert.assertEquals(loadPDFWithCache.pdfCache.size(), 12);

        Iterator<Object> iterator = loadPDFWithCache.objectCachePerFile.values().iterator();
        iterator.next();
        item = iterator.next();
        Assert.assertEquals(item.getClass(), PDFDictionary.class);
        item = loadPDFWithCache.objectCachePerFile.keySet().iterator().next();
        Assert.assertEquals(item.getClass(), String.class);
        Assert.assertEquals(loadPDFWithCache.objectCachePerFile.size(), 46);
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
                    pdfpage, objectCachePerFile, new HashMap<Integer, PDFArray>(), pdfCache);
            PDDocument doc = PDDocument.load(new File(pdf));
            PDPage page = doc.getPage(0);
            String stream = (String) adapter.createStreamFromPDFBoxPage(
                    doc, page, pdf, new AffineTransform(), null, new Rectangle());
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
        Assert.assertEquals(loadPDFWithCache.pdfCache.size(), 2);
        Assert.assertTrue(bos.size() <= 6418);
    }

    @Test
    public void testErrorMsgToPS() throws IOException, ImageException {
        String msg = "";
        try {
            pdfToPS(ERROR);
        } catch (RuntimeException e) {
            msg = e.getMessage();
        }
        Assert.assertTrue(msg.startsWith("Error while painting PDF page: " + ERROR));
    }

    @Test
    public void testErrorMsgToPDF() throws IOException {
        String msg = "";
        PDFRenderingContext context = new PDFRenderingContext(null, null, null, null);
        ImagePDF imagePDF = new ImagePDF(new ImageInfo(ERROR, null), null);
        try {
            new PDFBoxImageHandler().handleImage(context, imagePDF, null);
        } catch (RuntimeException e) {
            msg = e.getMessage();
        }
        Assert.assertTrue(msg.startsWith("Error on PDF page: " + ERROR));
    }

    @Test
    public void testNoPageResource() throws IOException {
        PDDocument doc = PDDocument.load(new File(CFF1));
        PDPage page = doc.getPage(0);
        page.setResources(null);
        AffineTransform at = new AffineTransform();
        getPDFBoxAdapter(false, false).createStreamFromPDFBoxPage(doc, page, CFF1, at, new FontInfo(), new Rectangle());
        doc.close();
    }

    @Test
    public void testPDFBoxImageHandlerAccessibilityEnabled() throws Exception {
        ImageInfo imgi = new ImageInfo("a", "b");
        PDDocument doc = PDDocument.load(new File(SHADING));
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
    public void testMergeFontsAndFormXObject() throws IOException {
        String msg = "";
        PDDocument doc = PDDocument.load(new File(IMAGE));
        PDPage page = doc.getPage(0);
        AffineTransform at = new AffineTransform();
        try {
            getPDFBoxAdapter(true, true)
                    .createStreamFromPDFBoxPage(doc, page, IMAGE, at, new FontInfo(), new Rectangle());
        } catch (RuntimeException e) {
            msg = e.getMessage();
        }
        doc.close();
        Assert.assertEquals(msg, "merge-fonts and form-xobject can't both be enabled");
    }

    @Test
    public void testFormXObject() throws IOException {
        PDDocument doc = PDDocument.load(new File(IMAGE));
        PDPage page = doc.getPage(0);
        AffineTransform at = new AffineTransform();
        PDFFormXObject formXObject = (PDFFormXObject) getPDFBoxAdapter(false, true)
                .createStreamFromPDFBoxPage(doc, page, IMAGE, at, new FontInfo(), new Rectangle());
        doc.close();
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        formXObject.output(bos);
        Assert.assertTrue(bos.toString("UTF-8").contains("/Type /XObject"));
    }

    @Test
    public void testRewriteOfForms() throws Exception {
        PDFDocument pdfdoc = new PDFDocument("");
        PDFPage pdfpage = getPDFPage(pdfdoc);
        pdfpage.setDocument(pdfdoc);
        pdfpage.setObjectNumber(1);
        PDFBoxAdapter adapter = new PDFBoxAdapter(pdfpage, new HashMap(), new HashMap<Integer, PDFArray>());
        PDDocument doc = PDDocument.load(new File(ACCESSIBLERADIOBUTTONS));
        PDPage page = doc.getPage(0);
        AffineTransform at = new AffineTransform();
        Rectangle r = new Rectangle(0, 1650, 842000, 595000);
        adapter.createStreamFromPDFBoxPage(doc, page, "key", at, null, r);
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        Map<String, List<String>> filterMap = new HashMap<String, List<String>>();
        List<String> filterList = new ArrayList<String>();
        filterList.add("null");
        filterMap.put("default", filterList);
        pdfdoc.setFilterMap(filterMap);
        pdfdoc.output(os);
        Assert.assertTrue(os.toString("UTF-8").contains("/F15106079 12 Tf"));
        doc.close();
    }
}
