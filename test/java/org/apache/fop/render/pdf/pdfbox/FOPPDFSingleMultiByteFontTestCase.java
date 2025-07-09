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
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.junit.Assert;
import org.junit.Test;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.apache.commons.io.IOUtils;
import org.apache.fontbox.cff.CFFFont;
import org.apache.fontbox.cff.CFFParser;
import org.apache.fontbox.ttf.CmapSubtable;
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
import org.apache.pdfbox.pdmodel.font.PDTrueTypeFont;
import org.apache.pdfbox.pdmodel.font.PDType0Font;

import org.apache.fop.events.DefaultEventBroadcaster;
import org.apache.fop.events.EventBroadcaster;
import org.apache.fop.fonts.FontInfo;
import org.apache.fop.pdf.PDFMergeFontsParams;

public class FOPPDFSingleMultiByteFontTestCase {
    private static final String TTSubset16 = "ttsubset16.pdf";
    private static final String TTSubset17 = "ttsubset17.pdf";
    private PDFMergeFontsParams params = new PDFMergeFontsParams(true);

    private COSDictionary getFont(PDDocument doc, String internalname) {
        PDPage page = doc.getPage(0);
        PDResources sourcePageResources = page.getResources();
        COSDictionary fonts = (COSDictionary)sourcePageResources.getCOSObject().getDictionaryObject(COSName.FONT);
        return (COSDictionary) fonts.getDictionaryObject(internalname);
    }

    @Test
    public void testCFF() throws Exception {
        PDDocument doc = PDFBoxAdapterTestCase.load(FontMergeTestCase.CFF1);
        FOPPDFSingleByteFont sbfont = new FOPPDFSingleByteFont(getFont(doc, "R11"),
                "MyriadPro-Regular_Type1f0encstdcs", new DefaultEventBroadcaster(), params);

        Assert.assertTrue(Arrays.asList(sbfont.getEncoding().getCharNameMap()).contains("bracketright"));
        Assert.assertTrue(!Arrays.asList(sbfont.getEncoding().getCharNameMap()).contains("A"));
        Assert.assertTrue(!Arrays.toString(sbfont.getEncoding().getUnicodeCharMap()).contains("A"));
        Assert.assertEquals(sbfont.mapChar('A'), 0);
        Assert.assertEquals(sbfont.getWidths().length, 28);
        Assert.assertEquals(sbfont.getFirstChar(), 87);
        Assert.assertEquals(sbfont.getLastChar(), 114);

        PDDocument doc2 = PDFBoxAdapterTestCase.load(FontMergeTestCase.CFF2);
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
        PDDocument doc = PDFBoxAdapterTestCase.load(FontMergeTestCase.CFF3);
        FOPPDFSingleByteFont sbfont = new FOPPDFSingleByteFont(getFont(doc, "T1_0"),
                "Myriad_Pro_Type1f0encf1cs", new DefaultEventBroadcaster(), params);
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
        PDDocument doc = PDFBoxAdapterTestCase.load(FontMergeTestCase.TTCID1);
        final String font = "ArialMT_Type0";
        FOPPDFMultiByteFont mbfont = new FOPPDFMultiByteFont(getFont(doc, "C2_0"), font,
                new DefaultEventBroadcaster());
        mbfont.addFont(getFont(doc, "C2_0"));
        Assert.assertEquals(mbfont.mapChar('t'), 85);

        PDDocument doc2 = PDFBoxAdapterTestCase.load(FontMergeTestCase.TTCID2);
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
        PDDocument doc = PDFBoxAdapterTestCase.load(FontMergeTestCase.TTSubset1);
        FOPPDFSingleByteFont mbfont = new FOPPDFSingleByteFont(getFont(doc, "R9"), font,
                new DefaultEventBroadcaster(), params);
        mbfont.addFont(getFont(doc, "R9"));
//        Assert.assertEquals(mbfont.mapChar('t'), 116);

        PDDocument doc2 = PDFBoxAdapterTestCase.load(FontMergeTestCase.TTSubset2);
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
        PDDocument doc = PDFBoxAdapterTestCase.load(FontMergeTestCase.Type1Subset1);
        FOPPDFSingleByteFont mbfont = new FOPPDFSingleByteFont(getFont(doc, "F15"), "",
                new DefaultEventBroadcaster(), params);
        mbfont.addFont(getFont(doc, "F15"));
        PDDocument doc2 = PDFBoxAdapterTestCase.load(FontMergeTestCase.Type1Subset2);
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
        PDDocument pdf = PDFBoxAdapterTestCase.load(FontMergeTestCase.TTCID1);
        COSDictionary font = getFont(pdf, "C2_0");
        font.removeItem(COSName.TO_UNICODE);
        FOPPDFMultiByteFont multiByteFont = new FOPPDFMultiByteFont(font, null, new DefaultEventBroadcaster());
        Assert.assertTrue(multiByteFont.hadMappingOperations());
        pdf.close();
    }

    @Test
    public void testMappingNotFound() throws IOException {
        PDDocument pdf = PDFBoxAdapterTestCase.load(FontMergeTestCase.TTCID1);
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
            super(fontData, name, new DefaultEventBroadcaster());
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
            super(fontData, name, new DefaultEventBroadcaster());
        }
        public String addFont(COSDictionary fontData) {
            return null;
        }
    }

    @Test
    public void testCmapFormat() throws Exception {
        PDDocument doc = PDFBoxAdapterTestCase.load(FontMergeTestCase.TTSubset10);
        FOPPDFSingleByteFont sbfont = new FOPPDFSingleByteFont(getFont(doc, "F1"), "test",
                new DefaultEventBroadcaster(), params);
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
        dis.skip(2 * 37); //endCode
        Assert.assertEquals(dis.readShort(), (short)0xFFFF);
        dis.skip(2); //reservedPad
        dis.skip(2 * 37); //startCode
        Assert.assertEquals(dis.readShort(), (short)0xFFFF);
        doc.close();
    }

    @Test
    public void testTTSubsetUniqueGlyphIndex() throws Exception {
        FontInfo fontInfo = new FontInfo();
        FontMergeTestCase.writeText(fontInfo, "ttsubset11.pdf");
        FontMergeTestCase.writeText(fontInfo, "ttsubset12.pdf");
        FontMergeTestCase.writeText(fontInfo, "ttsubset13.pdf");
        FOPPDFMultiByteFont font = (FOPPDFMultiByteFont) fontInfo.getFonts().get("CIDFont+F3_Type0f3");
        Assert.assertEquals(font.getUsedGlyphs().size(), 34);
    }

    @Test
    public void testTTMergeGlyphs() throws IOException {
        PDDocument doc = PDFBoxAdapterTestCase.load(FontMergeTestCase.TTSubset3);
        FOPPDFSingleByteFont sbFont = new FOPPDFSingleByteFont(getFont(doc, "F1"), "ArialMT_TrueTypecidcmap1",
                new DefaultEventBroadcaster(), params);
        PDDocument doc2 = PDFBoxAdapterTestCase.load(FontMergeTestCase.TTSubset5);
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
        PDDocument doc = PDFBoxAdapterTestCase.load(FontMergeTestCase.TTSubset3);
        final int[] calls = {0};
        FOPPDFSingleByteFont mbfont = new FOPPDFSingleByteFont(getFont(doc, "F1"),
                "ArialMT_TrueTypecidcmap2", new DefaultEventBroadcaster(), params) {
            protected void readCmapEntry(Map.Entry<Integer, Integer> entry, TrueTypeFont ttfont,
                                         MergeTTFonts.Cmap tempCmap,
                                         Map<Integer, Integer> oldToNewGIMapPerFont) throws IOException {
                super.readCmapEntry(entry, ttfont, tempCmap, oldToNewGIMapPerFont);
                calls[0]++;
            }
        };
        PDDocument doc2 = PDFBoxAdapterTestCase.load(FontMergeTestCase.TTSubset5);
        mbfont.addFont(getFont(doc2, "F1"));
        doc.close();
        doc2.close();
        Assert.assertEquals(calls[0], 512);
    }

    @Test
    public void testNoEOFOnValidation() throws IOException {
        PDDocument doc = PDFBoxAdapterTestCase.load(FontMergeTestCase.TTSubset1);
        PDDocument doc2 = PDFBoxAdapterTestCase.load(FontMergeTestCase.TTSubset5);

        PDFBoxEventProducer eventProducer = mock(PDFBoxEventProducer.class);
        FOPPDFSingleByteFont sbFont = new FOPPDFSingleByteFont(getFont(doc, "R11"), "QOYQBZ+TimesNewRomanPSMT",
                getEventBroadcaster(eventProducer), params);
        sbFont.addFont(getFont(doc2, "F1"));

        doc.close();
        doc2.close();

        sbFont.getInputStream();
        verifyNoEvents(eventProducer);
    }

    @Test
    public void testValidationMultiByteFont() throws IOException {
        PDDocument doc = PDFBoxAdapterTestCase.load("ttsubset11.pdf");
        PDDocument doc2 = PDFBoxAdapterTestCase.load("ttsubset12.pdf");

        PDFBoxEventProducer eventProducer = mock(PDFBoxEventProducer.class);

        FOPPDFMultiByteFont sbFont = new FOPPDFMultiByteFont(getFont(doc, "C2_0"), "CIDFont+F2",
                getEventBroadcaster(eventProducer));
        sbFont.addFont(getFont(doc2, "C2_1"));

        doc.close();
        doc2.close();

        sbFont.getInputStream();
        verifyNoEvents(eventProducer);
    }

    @Test
    public void testValidationSingleByteFont() throws IOException {
        validateSingleByteFont(FontMergeTestCase.TTSubset3, FontMergeTestCase.TTSubset5,
                "QOYQBZ+TimesNewRomanPSMT", "F1", true);
        validateSingleByteFont(FontMergeTestCase.TTSubset14, FontMergeTestCase.TTSubset15,
                "Montserrat-Regular", "TT1", true);
    }

    @Test
    public void testTTSubsetRemapGid() throws IOException {
        InputStream is = validateSingleByteFont(TTSubset16, TTSubset17,
                "Montserrat-Regular", "TT0", true);
        TrueTypeFont mergedTTF = new TTFParser().parse(new RandomAccessReadBuffer(is));
        List<Integer> gids = new ArrayList<>();
        for (int i = 0; i < mergedTTF.getNumberOfGlyphs(); i++) {
            if (mergedTTF.getGlyph().getGlyph(i).getNumberOfContours() != 0) {
                gids.add(i);
            }
        }
        Assert.assertEquals(124, gids.size());
        Assert.assertTrue(gids.contains(242));
        Assert.assertEquals(3, mergedTTF.getGlyph().getGlyph(167).getNumberOfContours());
        compareGlyphsUsingTTSubset16And17(mergedTTF);
    }

    private void compareGlyphsUsingTTSubset16And17(TrueTypeFont mergedTTF) throws IOException {
        Map<Integer, GlyphData> glyphsFromInput1 = getGlyphs(TTSubset16, "TT0");
        Assert.assertEquals(74, glyphsFromInput1.size());
        for (Map.Entry<Integer, GlyphData> entry : glyphsFromInput1.entrySet()) {
            compareGlyph(mergedTTF, entry.getKey(), entry.getValue());
        }
        Map<Integer, GlyphData> glyphsFromInput2 = getGlyphs(TTSubset17, "TT0");
        Assert.assertEquals(54, glyphsFromInput2.size());
        List<Integer> glyphsChecked = new ArrayList<>();
        for (Map.Entry<Integer, GlyphData> entry : glyphsFromInput2.entrySet()) {
            if (!glyphsFromInput1.containsKey(entry.getKey())) {
                compareGlyph(mergedTTF, entry.getKey(), entry.getValue());
                glyphsChecked.add(entry.getKey());
            }
        }
        Assert.assertEquals(Arrays.asList(8211, 37, 74), glyphsChecked);
    }

    private void compareGlyph(TrueTypeFont mergedTTF, int charCode, GlyphData glyphFromInput) throws IOException {
        CmapSubtable cmap = mergedTTF.getCmap().getCmaps()[0];
        int gid = cmap.getGlyphId(charCode);
        GlyphData mergedGlyph = mergedTTF.getGlyph().getGlyph(gid);
        Assert.assertEquals(mergedGlyph.getNumberOfContours(), glyphFromInput.getNumberOfContours());
        Assert.assertEquals(mergedGlyph.getBoundingBox().toString(), glyphFromInput.getBoundingBox().toString());
    }

    private Map<Integer, GlyphData> getGlyphs(String pdf, String fontName) throws IOException {
        PDDocument doc = PDFBoxAdapterTestCase.load(pdf);
        PDTrueTypeFont font = (PDTrueTypeFont) doc.getPage(0).getResources().getFont(COSName.getPDFName(fontName));
        TrueTypeFont ttf = font.getTrueTypeFont();
        Map<Integer, GlyphData> glyphs = new HashMap<>();
        for (Map.Entry<Integer, Integer> entry : FOPPDFSingleByteFont.getCharacterCodeToGlyphId(
                ttf.getCmap().getCmaps()[0]).entrySet()) {
            GlyphData glyph = ttf.getGlyph().getGlyph(entry.getValue());
            if (glyph.getNumberOfContours() != 0) {
                glyphs.put(entry.getKey(), glyph);
            }
        }
        doc.close();
        return glyphs;
    }

    @Test
    public void testTTSubsetNoRemapGid() throws IOException {
        InputStream is = validateSingleByteFont(TTSubset16, TTSubset17,
                "Montserrat", "TT0", false);
        TrueTypeFont ttf = new TTFParser().parse(new RandomAccessReadBuffer(is));
        List<Integer> gids = new ArrayList<>();
        for (int i = 0; i < ttf.getNumberOfGlyphs(); i++) {
            if (ttf.getGlyph().getGlyph(i).getNumberOfContours() != 0) {
                gids.add(i);
            }
        }
        Assert.assertEquals(123, gids.size());
        Assert.assertFalse(gids.contains(242));
    }

    private InputStream validateSingleByteFont(String firstFile, String secondFile, String fontName,
                                               String internalName, boolean remapFont) throws IOException {
        PDDocument doc = PDFBoxAdapterTestCase.load(firstFile);
        PDDocument doc2 = PDFBoxAdapterTestCase.load(secondFile);
        FOPPDFSingleByteFont sbFont = new FOPPDFSingleByteFont(getFont(doc, internalName), fontName,
                new DefaultEventBroadcaster(), new PDFMergeFontsParams(remapFont));
        sbFont.addFont(getFont(doc2, internalName));
        doc.close();
        doc2.close();
        return sbFont.getInputStream();
    }

    private EventBroadcaster getEventBroadcaster(PDFBoxEventProducer eventProducer) {
        EventBroadcaster eventBroadcaster = mock(EventBroadcaster.class);
        when(eventBroadcaster.getEventProducerFor(PDFBoxEventProducer.class)).thenReturn(eventProducer);
        return eventBroadcaster;
    }

    private void verifyNoEvents(PDFBoxEventProducer eventProducer) {
        verify(eventProducer, times(0)).duplicatedGlyph(any(), anyString(), anyInt());
        verify(eventProducer, times(0)).invalidGlyphId(any(), anyString(), anyInt());
        verify(eventProducer, times(0)).glyphDataMissing(any(), anyString(), anyInt());
        verify(eventProducer, times(0)).characterCodesSharingGlyphId(any(), anyString(),
                anyString(), anyString(), anyInt());
    }
}
