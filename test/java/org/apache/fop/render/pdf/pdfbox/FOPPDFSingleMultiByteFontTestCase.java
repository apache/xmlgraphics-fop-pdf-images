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

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.junit.Assert;
import org.junit.Test;

import org.apache.commons.io.IOUtils;
import org.apache.fontbox.cff.CFFFont;
import org.apache.fontbox.cff.CFFParser;
import org.apache.fontbox.ttf.CmapTable;
import org.apache.fontbox.ttf.GlyphData;
import org.apache.fontbox.ttf.GlyphTable;
import org.apache.fontbox.ttf.TTFParser;
import org.apache.fontbox.ttf.TTFTable;
import org.apache.fontbox.ttf.TrueTypeFont;
import org.apache.fontbox.type1.Type1Font;
import org.apache.pdfbox.cos.COSArray;
import org.apache.pdfbox.cos.COSDictionary;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.cos.COSNumber;
import org.apache.pdfbox.cos.COSObject;
import org.apache.pdfbox.io.RandomAccessReadBuffer;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.font.PDCIDFontType2;
import org.apache.pdfbox.pdmodel.font.PDType0Font;

import org.apache.fop.fonts.FontInfo;

public class FOPPDFSingleMultiByteFontTestCase {
    private COSDictionary getFont(PDDocument doc, String internalname) {
        PDPage page = doc.getPage(0);
        PDResources sourcePageResources = page.getResources();
        COSDictionary fonts = (COSDictionary)sourcePageResources.getCOSObject().getDictionaryObject(COSName.FONT);
        return (COSDictionary) fonts.getDictionaryObject(internalname);
    }

    @Test
    public void testCFF() throws Exception {
        PDDocument doc = PDFBoxAdapterTestCase.load(PDFBoxAdapterTestCase.CFF1);
        FOPPDFSingleByteFont sbfont = new FOPPDFSingleByteFont(getFont(doc, "R11"),
                "MyriadPro-Regular_Type1f0encstdcs");

        Assert.assertTrue(Arrays.asList(sbfont.getEncoding().getCharNameMap()).contains("bracketright"));
        Assert.assertTrue(!Arrays.asList(sbfont.getEncoding().getCharNameMap()).contains("A"));
        Assert.assertTrue(!Arrays.toString(sbfont.getEncoding().getUnicodeCharMap()).contains("A"));
        Assert.assertEquals(sbfont.mapChar('A'), 0);
        Assert.assertEquals(sbfont.getWidths().length, 28);
        Assert.assertEquals(sbfont.getFirstChar(), 87);
        Assert.assertEquals(sbfont.getLastChar(), 114);

        PDDocument doc2 = PDFBoxAdapterTestCase.load(PDFBoxAdapterTestCase.CFF2);
        String name = sbfont.addFont(getFont(doc2, "R11"));
        Assert.assertTrue(name.contains("MyriadPro"));

        Assert.assertEquals(sbfont.getFontName(), "MyriadPro-Regular_Type1f0encstdcs");
        Assert.assertEquals(sbfont.getEncodingName(), "WinAnsiEncoding");
        Assert.assertEquals(sbfont.mapChar('W'), 'W');
        String x = IOUtils.toString(sbfont.getInputStream(), StandardCharsets.UTF_8);
        Assert.assertTrue(x, x.contains("Adobe Systems"));
        Assert.assertEquals(sbfont.getEncoding().getName(), "FOPPDFEncoding");
        Assert.assertTrue(Arrays.asList(sbfont.getEncoding().getCharNameMap()).contains("A"));
        Assert.assertEquals(sbfont.getWidths().length, 65);
        Assert.assertEquals(sbfont.getFirstChar(), 50);
        Assert.assertEquals(sbfont.getLastChar(), 114);

        Assert.assertEquals(sbfont.addFont(getFont(doc2, "R13")), null);

        doc.close();
        doc2.close();
    }

    @Test
    public void testCFF2() throws Exception {
        PDDocument doc = PDFBoxAdapterTestCase.load(PDFBoxAdapterTestCase.CFF3);
        FOPPDFSingleByteFont sbfont = new FOPPDFSingleByteFont(getFont(doc, "T1_0"),
                "Myriad_Pro_Type1f0encf1cs");
        Assert.assertTrue(Arrays.asList(sbfont.getEncoding().getCharNameMap()).contains("uni004E"));
        Assert.assertEquals(sbfont.getFontName(), "Myriad_Pro_Type1f0encf1cs");
        Assert.assertEquals(sbfont.getEncodingName(), null);
        byte[] is = IOUtils.toByteArray(sbfont.getInputStream());

        CFFParser p = new CFFParser();
        CFFFont ff = p.parse(new RandomAccessReadBuffer(is)).get(0);
        Assert.assertEquals(ff.getName(), "MNEACN+Myriad_Pro");
//        Assert.assertEquals(ff.getCharset().getEntries().get(0).getSID(), 391);

        doc.close();
    }

    @Test
    public void testTTCID() throws Exception {
        PDDocument doc = PDFBoxAdapterTestCase.load(PDFBoxAdapterTestCase.TTCID1);
        final String font = "ArialMT_Type0";
        FOPPDFMultiByteFont mbfont = new FOPPDFMultiByteFont(getFont(doc, "C2_0"), font);
        mbfont.addFont(getFont(doc, "C2_0"));
        Assert.assertEquals(mbfont.mapChar('t'), 85);

        PDDocument doc2 = PDFBoxAdapterTestCase.load(PDFBoxAdapterTestCase.TTCID2);
        String name = mbfont.addFont(getFont(doc2, "C2_0"));
        Assert.assertEquals(name, font);
        Assert.assertEquals(mbfont.getFontName(), font);
        byte[] is = IOUtils.toByteArray(mbfont.getInputStream());
        Assert.assertEquals(is.length, 59168);
        doc.close();
        doc2.close();
    }

    @Test
    public void testTTSubset() throws Exception {
        final String font = "TimesNewRomanPSMT_TrueType";
        PDDocument doc = PDFBoxAdapterTestCase.load(PDFBoxAdapterTestCase.TTSubset1);
        FOPPDFSingleByteFont mbfont = new FOPPDFSingleByteFont(getFont(doc, "R9"), font);
        mbfont.addFont(getFont(doc, "R9"));
//        Assert.assertEquals(mbfont.mapChar('t'), 116);

        PDDocument doc2 = PDFBoxAdapterTestCase.load(PDFBoxAdapterTestCase.TTSubset2);
        String name = mbfont.addFont(getFont(doc2, "R9"));
        Assert.assertEquals(name, font);
        Assert.assertEquals(mbfont.getFontName(), font);
        byte[] is = IOUtils.toByteArray(mbfont.getInputStream());
        Assert.assertEquals(is.length, 41104);

        TrueTypeFont trueTypeFont = new TTFParser().parse(new RandomAccessReadBuffer(is));
        TTFTable ttfTable = trueTypeFont.getTableMap().get("cmap");
        ByteArrayInputStream bis = new ByteArrayInputStream(is);
        bis.skip(ttfTable.getOffset() + 21);
        Assert.assertEquals(bis.read(), 4); //subtableFormat

        doc.close();
        doc2.close();
    }

    @Test
    public void testType1Subset() throws Exception {
        PDDocument doc = PDFBoxAdapterTestCase.load(PDFBoxAdapterTestCase.Type1Subset1);
        FOPPDFSingleByteFont mbfont = new FOPPDFSingleByteFont(getFont(doc, "F15"), "");
        mbfont.addFont(getFont(doc, "F15"));
        PDDocument doc2 = PDFBoxAdapterTestCase.load(PDFBoxAdapterTestCase.Type1Subset2);
        mbfont.addFont(getFont(doc2, "F15"));
        Type1Font f = Type1Font.createWithPFB(mbfont.getInputStream());
        Set<String> csDict = new TreeSet<String>(f.getCharStringsDict().keySet());
        Assert.assertEquals(csDict.toString(), "[.notdef, a, d, e, hyphen, l, m, n, p, s, space, t, two, x]");
        Assert.assertEquals(f.getSubrsArray().size(), 518);
        Assert.assertEquals(f.getFamilyName(), "Verdana");
        doc.close();
        doc2.close();
    }

    @Test
    public void testHadMappingOperations() throws IOException {
        PDDocument pdf = PDFBoxAdapterTestCase.load(PDFBoxAdapterTestCase.TTCID1);
        COSDictionary font = getFont(pdf, "C2_0");
        font.removeItem(COSName.TO_UNICODE);
        FOPPDFMultiByteFont multiByteFont = new FOPPDFMultiByteFont(font, null);
        Assert.assertTrue(multiByteFont.hadMappingOperations());
        pdf.close();
    }

    @Test
    public void testMappingNotFound() throws IOException {
        PDDocument pdf = PDFBoxAdapterTestCase.load(PDFBoxAdapterTestCase.TTCID1);
        final COSDictionary fontDict = getFont(pdf, "C2_0");
        MyFOPPDFMultiByteFont multiByteFont = new MyFOPPDFMultiByteFont(fontDict, null);
        PDType0Font font = (PDType0Font) multiByteFont.getFontContainer().getFont();
        GlyphTable glyphTable = ((PDCIDFontType2)font.getDescendantFont()).getTrueTypeFont().getGlyph();
        glyphTable.setGlyphs(
                new GlyphData[((PDCIDFontType2) font.getDescendantFont()).getTrueTypeFont().getNumberOfGlyphs() - 1]);
        IOException ex = Assert.assertThrows(IOException.class, () -> multiByteFont.addFont(fontDict));
        Assert.assertEquals(ex.getMessage(), "Mapping not found in glyphData");
        pdf.close();
    }

    private static class MyFOPPDFMultiByteFont extends FOPPDFMultiByteFont {
        COSDictionary fontData;
        MyFOPPDFMultiByteFont(COSDictionary fontData, String name) throws IOException {
            super(fontData, name);
            this.fontData = fontData;
        }
        FontContainer getFontContainer() throws IOException {
            return getFont(fontData);
        }
    }

    @Test
    public void testBBox() throws IOException {
        COSDictionary dict = new COSDictionary();
        COSArray array = new COSArray();
        array.add(COSNumber.get("1"));
        dict.setItem(COSName.FONT_BBOX, new COSObject(array));
        FOPPDFMultiByteFont multiByteFont = new NoAddFontFOPPDFMultiByteFont(dict, "");
        Assert.assertEquals(multiByteFont.getFontBBox()[0], 1);
    }

    private static class NoAddFontFOPPDFMultiByteFont extends FOPPDFMultiByteFont {
        NoAddFontFOPPDFMultiByteFont(COSDictionary fontData, String name) throws IOException {
            super(fontData, name);
        }
        public String addFont(COSDictionary fontData) {
            return null;
        }
    }

    @Test
    public void testCmapFormat() throws Exception {
        PDDocument doc = PDFBoxAdapterTestCase.load(PDFBoxAdapterTestCase.TTSubset10);
        FOPPDFSingleByteFont sbfont = new FOPPDFSingleByteFont(getFont(doc, "F1"), "test");
        byte[] bytes = IOUtils.toByteArray(sbfont.getInputStream());
        TrueTypeFont trueTypeFont = new TTFParser().parse(new RandomAccessReadBuffer(bytes));
        CmapTable ttfTable = (CmapTable) trueTypeFont.getTableMap().get("cmap");
        Assert.assertEquals(ttfTable.getCmaps()[0].getPlatformId(), 0);
        Assert.assertEquals(ttfTable.getCmaps()[1].getPlatformId(), 1);
        Assert.assertEquals(ttfTable.getCmaps()[2].getPlatformId(), 3);
        ByteArrayInputStream bis = new ByteArrayInputStream(bytes);
        bis.skip(ttfTable.getOffset() + 29);
        Assert.assertEquals(bis.read(), 12); //subtableFormat
        bis = new ByteArrayInputStream(bytes);
        bis.skip(ttfTable.getOffset() + 465);
        Assert.assertEquals(bis.read(), 4); //subtableFormat
        bis = new ByteArrayInputStream(bytes);
        bis.skip(ttfTable.getOffset() + 809);
        DataInputStream dis = new DataInputStream(bis);
        Assert.assertEquals(dis.read(), 4); //subtableFormat
        dis.skip(2 * 6);
        dis.skip(2 * 44); //endCode
        Assert.assertEquals(dis.readShort(), (short)0xFFFF);
        dis.skip(2); //reservedPad
        dis.skip(2 * 44); //startCode
        Assert.assertEquals(dis.readShort(), (short)0xFFFF);
        doc.close();
    }

    @Test
    public void testTTSubsetUniqueGlyphIndex() throws Exception {
        FontInfo fontInfo = new FontInfo();
        PDFBoxAdapterTestCase.writeText(fontInfo, "ttsubset11.pdf");
        PDFBoxAdapterTestCase.writeText(fontInfo, "ttsubset12.pdf");
        PDFBoxAdapterTestCase.writeText(fontInfo, "ttsubset13.pdf");
        FOPPDFMultiByteFont font = (FOPPDFMultiByteFont) fontInfo.getFonts().get("CIDFont+F3_Type0f3");
        Assert.assertEquals(font.getUsedGlyphs().size(), 34);
    }

    @Test
    public void testTTMergeGlyphs() throws IOException {
        PDDocument doc = PDFBoxAdapterTestCase.load(PDFBoxAdapterTestCase.TTSubset3);
        FOPPDFSingleByteFont sbFont = new FOPPDFSingleByteFont(getFont(doc, "F1"), "ArialMT_TrueTypecidcmap1");
        PDDocument doc2 = PDFBoxAdapterTestCase.load(PDFBoxAdapterTestCase.TTSubset5);
        sbFont.addFont(getFont(doc2, "F1"));
        doc.close();
        doc2.close();
        TrueTypeFont trueTypeFont = new TTFParser().parse(new RandomAccessReadBuffer(sbFont.getInputStream()));
        List<Integer> contours = new ArrayList<>();
        for (int i = 0; i < trueTypeFont.getNumberOfGlyphs(); i++) {
            GlyphData glyphData = trueTypeFont.getGlyph().getGlyph(i);
            contours.add((int)glyphData.getNumberOfContours());
        }
        Assert.assertEquals(contours, Arrays.asList(2, 4, 2, 3, 1, 3));
    }

    @Test
    public void testPerformance() throws IOException {
        PDDocument doc = PDFBoxAdapterTestCase.load(PDFBoxAdapterTestCase.TTSubset3);
        final int[] calls = {0};
        FOPPDFSingleByteFont mbfont = new FOPPDFSingleByteFont(getFont(doc, "F1"),
                "ArialMT_TrueTypecidcmap2") {
            protected void readCmapEntry(Map.Entry<Integer, Integer> entry, TrueTypeFont ttfont,
                                         MergeTTFonts.Cmap tempCmap) throws IOException {
                super.readCmapEntry(entry, ttfont, tempCmap);
                calls[0]++;
            }
        };
        PDDocument doc2 = PDFBoxAdapterTestCase.load(PDFBoxAdapterTestCase.TTSubset5);
        mbfont.addFont(getFont(doc2, "F1"));
        doc.close();
        doc2.close();
        Assert.assertEquals(calls[0], 512);
    }
}
