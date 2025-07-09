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
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Assert;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

import org.apache.commons.io.IOUtils;
import org.apache.fontbox.cff.CFFCharset;
import org.apache.fontbox.cff.CFFParser;
import org.apache.fontbox.cff.CFFType1Font;
import org.apache.fontbox.ttf.GlyphData;
import org.apache.fontbox.ttf.TTFParser;
import org.apache.fontbox.ttf.TrueTypeFont;
import org.apache.fontbox.type1.Type1Font;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.io.RandomAccessReadBuffer;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.font.PDCIDFontType2;
import org.apache.pdfbox.pdmodel.font.PDType0Font;

import org.apache.fop.events.DefaultEventBroadcaster;
import org.apache.fop.fonts.CustomFont;
import org.apache.fop.fonts.FontInfo;
import org.apache.fop.fonts.FontType;
import org.apache.fop.fonts.MultiByteFont;
import org.apache.fop.fonts.Typeface;
import org.apache.fop.pdf.PDFArray;
import org.apache.fop.pdf.PDFDocument;
import org.apache.fop.pdf.PDFFilterList;
import org.apache.fop.pdf.PDFMergeFontsParams;
import org.apache.fop.pdf.PDFPage;

public class FontMergeTestCase {
    protected static final String CFF1 = "2fonts.pdf";
    protected static final String CFF2 = "2fonts2.pdf";
    protected static final String CFF3 = "simpleh.pdf";
    protected static final String CFFSUBRS = "cffsubrs.pdf";
    protected static final String CFFSUBRS2 = "cffsubrs2.pdf";
    protected static final String CFFSUBRS3 = "cffsubrs3.pdf";
    protected static final String CFFSUBRS4 = "cffsubrs4.pdf";
    protected static final String TTCID1 = "ttcid1.pdf";
    protected static final String TTCID2 = "ttcid2.pdf";
    protected static final String TTSubset1 = "ttsubset.pdf";
    protected static final String TTSubset2 = "ttsubset2.pdf";
    protected static final String TTSubset3 = "ttsubset3.pdf";
    protected static final String TTSubset5 = "ttsubset5.pdf";
    protected static final String TTSubset6 = "ttsubset6.pdf";
    protected static final String TTSubset7 = "ttsubset7.pdf";
    protected static final String TTSubset8 = "ttsubset8.pdf";
    protected static final String TTSubset9 = "ttsubset9.pdf";
    protected static final String TTSubset10 = "ttsubset10.pdf";
    protected static final String TTSubset14 = "ttsubset14.pdf";
    protected static final String TTSubset15 = "ttsubset15.pdf";
    protected static final String CFFCID1 = "cffcid1.pdf";
    protected static final String CFFCID2 = "cffcid2.pdf";
    protected static final String Type1Subset1 = "t1subset.pdf";
    protected static final String Type1Subset2 = "t1subset2.pdf";
    protected static final String Type1Subset3 = "t1subset3.pdf";
    protected static final String Type1Subset4 = "t1subset4.pdf";
    protected static final String XFORM = "xform.pdf";
    protected static final String TYPE0TT = "type0tt.pdf";
    protected static final String TYPE0CFF = "type0cff.pdf";

    protected static String writeText(FontInfo fi, String pdf) throws IOException {
        PDDocument doc = PDFBoxAdapterTestCase.load(pdf);
        PDPage page = doc.getPage(0);
        AffineTransform pageAdjust = new AffineTransform();
        String c = (String) PDFBoxAdapterTestCase.getPDFBoxAdapter(true, false)
                .createStreamFromPDFBoxPage(doc, page, pdf, pageAdjust, fi, new Rectangle(), new AffineTransform());
        doc.close();
        return c;
    }

    @Test
    public void testMergeFontsAndFormXObject() throws IOException {
        PDDocument doc = PDFBoxAdapterTestCase.load(PDFBoxAdapterTestCase.IMAGE);
        PDPage page = doc.getPage(0);
        AffineTransform pageAdjust = new AffineTransform();
        RuntimeException ex = Assert.assertThrows(RuntimeException.class, () ->
                PDFBoxAdapterTestCase.getPDFBoxAdapter(true, true).createStreamFromPDFBoxPage(
                      doc, page, PDFBoxAdapterTestCase.IMAGE, pageAdjust, new FontInfo(), new Rectangle(), pageAdjust));
        doc.close();
        assertEquals(ex.getMessage(), "merge-fonts and form-xobject can't both be enabled");
    }

    @Test
    public void testPDFToPDF() throws IOException {
        FontInfo fi = new FontInfo();
        writeText(fi, CFF1);
        writeText(fi, CFF2);
        writeText(fi, CFF3);
        writeText(fi, CFFCID1);
        writeText(fi, CFFCID2);
        writeText(fi, PDFBoxAdapterTestCase.IMAGE);
        writeText(fi, PDFBoxAdapterTestCase.LINK);
        writeText(fi, PDFBoxAdapterTestCase.ROTATE);
        writeText(fi, PDFBoxAdapterTestCase.SHADING);
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
        writeText(fi, PDFBoxAdapterTestCase.LOOP);
    }

    @Test
    public void testPDFWriter() throws Exception {
        FontInfo fi = new FontInfo();
        String msg = writeText(fi, CFF3);
        Assert.assertTrue(msg, msg.contains("/Myriad_Pro"));
        assertEquals(fi.getUsedFonts().size(), 2);
        msg = writeText(fi, TTSubset1);
        Assert.assertTrue(msg, msg.contains("<74>-0.168 <65>-0.1523 <73>0.1528 <74>277.832"));
        msg = writeText(fi, TTSubset2);
        Assert.assertTrue(msg, msg.contains("(t)-0.168 (e)-0.1523 (s)0.1528 (t)"));
        msg = writeText(fi, TTSubset3);
        Assert.assertTrue(msg, msg.contains("[<01>3 <02>-7 <03>] TJ"));
        msg = writeText(fi, TTSubset5);
        Assert.assertTrue(msg, msg.contains("[(\u0001)2 (\u0002)-7 (\u0003)] TJ"));
        msg = writeText(fi, TTCID1);
        Assert.assertTrue(msg, msg.contains("<0031001100110011001800120012001300140034>"));
        msg = writeText(fi, TTCID2);
        Assert.assertTrue(msg, msg.contains("<0031001100110011001800120012001300120034>"));
        msg = writeText(fi, CFFCID1);
        Assert.assertTrue(msg, msg.contains("/Fm0-1998009062 Do"));
        msg = writeText(fi, CFFCID2);
        Assert.assertTrue(msg, msg.contains("/Fm0-1997085541 Do"));
        msg = writeText(fi, Type1Subset1);
        Assert.assertTrue(msg, msg.contains("/Verdana_Type1"));
        msg = writeText(fi, Type1Subset2);
        Assert.assertTrue(msg, msg.contains("[(2nd example)] TJ"));
        msg = writeText(fi, Type1Subset3);
        Assert.assertTrue(msg, msg.contains("/URWChanceryL-MediItal_Type1 20 Tf"));
        msg = writeText(fi, Type1Subset4);
        Assert.assertTrue(msg, msg.contains("/F15_1683747577 40 Tf"));
        parseFonts(fi);
    }

    private void parseFonts(FontInfo fi) throws IOException {
        for (Typeface font : fi.getUsedFonts().values()) {
            InputStream is = ((CustomFont) font).getInputStream();
            if (font.getFontType() == FontType.TYPE1C || font.getFontType() == FontType.CIDTYPE0) {
                CFFParser p = new CFFParser();
                p.parse(new RandomAccessReadBuffer(is));
            } else if (font.getFontType() == FontType.TRUETYPE) {
                TTFParser parser = new TTFParser();
                parser.parse(new RandomAccessReadBuffer(is));
            } else if (font.getFontType() == FontType.TYPE0) {
                TTFParser parser = new TTFParser(true);
                parser.parse(new RandomAccessReadBuffer(is));
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

    @Test
    public void testXform() throws Exception {
        PDFDocument pdfdoc = new PDFDocument("");
        pdfdoc.getFilterMap().put(PDFFilterList.DEFAULT_FILTER, Collections.singletonList("null"));
        pdfdoc.setMergeFontsParams(new PDFMergeFontsParams(true));
        PDFPage pdfpage = PDFBoxAdapterTestCase.getPDFPage(pdfdoc);
        pdfpage.setDocument(pdfdoc);
        pdfpage.setObjectNumber(1);
        Map<Integer, PDFArray> pageNumbers = new HashMap<Integer, PDFArray>();
        PDFBoxAdapter adapter = new PDFBoxAdapter(pdfpage, new HashMap<>(), new HashMap<>(), pageNumbers,
                new HashMap<>(), new DefaultEventBroadcaster());
        PDDocument doc = PDFBoxAdapterTestCase.load(XFORM);
        PDPage page = doc.getPage(0);
        AffineTransform pageAdjust = new AffineTransform();
        Rectangle r = new Rectangle(0, 1650, 842000, 595000);
        adapter.createStreamFromPDFBoxPage(doc, page, "key", pageAdjust, new FontInfo(), r, pageAdjust);
        doc.close();
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        pdfdoc.output(bos);
        Assert.assertFalse(bos.toString(StandardCharsets.UTF_8.name()).contains("/W 5 /H 5 /BPC 8 /CS /RGB ID ÿÿÿ"));
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
        PDDocument doc = PDFBoxAdapterTestCase.load(TYPE0TT);
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

    @Test
    public void testCmapLengthInName() throws IOException {
        FontInfo fi = new FontInfo();
        String msg = writeText(fi, TTSubset3);
        Assert.assertTrue(msg, msg.contains("/ArialMT_TrueTypecidcmap1"));
    }

    @Test
    public void testMapChar() throws Exception {
        FontInfo fi = new FontInfo();
        writeText(fi, TTSubset6);
        String msg = writeText(fi, TTSubset7);
        Assert.assertTrue(msg, msg.contains("( )Tj"));
    }

    @Test
    public void testReorderGlyphs() throws IOException {
        FontInfo fontInfo = new FontInfo();
        writeText(fontInfo, TTSubset8);
        writeText(fontInfo, TTSubset9);
        List<Integer> compositeList = new ArrayList<>();
        for (Typeface font : fontInfo.getUsedFonts().values()) {
            InputStream inputStream = ((CustomFont) font).getInputStream();
            TTFParser parser = new TTFParser(true);
            TrueTypeFont trueTypeFont = parser.parse(new RandomAccessReadBuffer(inputStream));
            int i = 0;
            for (int gid = 0; gid < trueTypeFont.getNumberOfGlyphs(); gid++) {
                GlyphData glyphData = trueTypeFont.getGlyph().getGlyph(gid);
                if (glyphData != null && glyphData.getDescription().isComposite()) {
                    compositeList.add(i);
                }
                i++;
            }
        }
        assertEquals(compositeList, Arrays.asList(18, 19, 39, 42, 62, 63, 29));
    }

    @Test
    public void testCFFSubrs() throws Exception {
        FontInfo fontInfo = new FontInfo();
        writeText(fontInfo, CFFSUBRS);
        writeText(fontInfo, CFFSUBRS2);
        InputStream is = null;
        for (Typeface font : fontInfo.getUsedFonts().values()) {
            if ("AllianzNeo-Bold".equals(font.getEmbedFontName())) {
                is = ((CustomFont) font).getInputStream();
            }
        }
        CFFType1Font font = (CFFType1Font) new CFFParser().parse(new RandomAccessReadBuffer(is)).get(0);
        byte[][] indexData = (byte[][]) font.getPrivateDict().get("Subrs");
        assertEquals(indexData.length, 183);
    }

    @Test
    public void testCFFSubrsCharset() throws Exception {
        FontInfo fontInfo = new FontInfo();
        writeText(fontInfo, CFFSUBRS4);
        writeText(fontInfo, CFFSUBRS3);
        CustomFont typeface = (CustomFont) fontInfo.getUsedFonts().get("AllianzNeo-Light_Type1");
        InputStream is = typeface.getInputStream();
        CFFType1Font font = (CFFType1Font) new CFFParser().parse(new RandomAccessReadBuffer(is)).get(0);
        CFFCharset charset = font.getCharset();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 5; i++) {
            sb.append(charset.getNameForGID(i)).append(" ");
        }
        assertEquals(sb.toString(), ".notdef uni00A0 trademark uni003B uniFB00 ");
    }

    @Test
    public void testAscenderDoesntMatch() throws IOException {
        FontInfo fi = new FontInfo();
        writeText(fi, TTSubset6);
        String msg = writeText(fi, TTSubset7);
        Assert.assertTrue(msg, msg.contains("/C2_0745125721 12 Tf"));
    }

    @Test
    public void testMergeMacFont() throws IOException {
        String msg = writeText(new FontInfo(), TTSubset6);
        Assert.assertTrue(msg, msg.contains("/Calibri_TrueTypemac"));
    }
}
