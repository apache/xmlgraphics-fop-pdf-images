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

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Set;
import java.util.TreeSet;

import org.junit.Assert;
import org.junit.Test;

import org.apache.commons.io.IOUtils;
import org.apache.fontbox.cff.CFFFont;
import org.apache.fontbox.cff.CFFParser;
import org.apache.fontbox.ttf.GlyphData;
import org.apache.fontbox.ttf.GlyphTable;
import org.apache.fontbox.type1.Type1Font;
import org.apache.pdfbox.cos.COSArray;
import org.apache.pdfbox.cos.COSDictionary;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.cos.COSNumber;
import org.apache.pdfbox.cos.COSObject;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.font.PDCIDFontType2;
import org.apache.pdfbox.pdmodel.font.PDType0Font;

import org.apache.fop.render.pdf.pdfbox.FOPPDFMultiByteFont;
import org.apache.fop.render.pdf.pdfbox.FOPPDFSingleByteFont;
import org.apache.fop.render.pdf.pdfbox.FontContainer;

public class FOPPDFSingleMultiByteFontTestCase {
    private COSDictionary getFont(PDDocument doc, String internalname) throws IOException {
        PDPage page = doc.getPage(0);
        PDResources sourcePageResources = page.getResources();
        COSDictionary fonts = (COSDictionary)sourcePageResources.getCOSObject().getDictionaryObject(COSName.FONT);
        return (COSDictionary) fonts.getDictionaryObject(internalname);
    }

    @Test
    public void testCFF() throws Exception {
        PDDocument doc = PDDocument.load(new File(PDFBoxAdapterTestCase.CFF1));
        FOPPDFSingleByteFont sbfont = new FOPPDFSingleByteFont(getFont(doc, "R11"),
                "MyriadPro-Regular_Type1f0encstdcs");

        Assert.assertTrue(Arrays.asList(sbfont.getEncoding().getCharNameMap()).contains("bracketright"));
        Assert.assertTrue(!Arrays.asList(sbfont.getEncoding().getCharNameMap()).contains("A"));
        Assert.assertTrue(!Arrays.toString(sbfont.getEncoding().getUnicodeCharMap()).contains("A"));
        Assert.assertEquals(sbfont.mapChar('A'), 0);
        Assert.assertEquals(sbfont.getWidths().length, 28);
        Assert.assertEquals(sbfont.getFirstChar(), 87);
        Assert.assertEquals(sbfont.getLastChar(), 114);

        PDDocument doc2 = PDDocument.load(new File(PDFBoxAdapterTestCase.CFF2));
        String name = sbfont.addFont(getFont(doc2, "R11"));
        Assert.assertTrue(name.contains("MyriadPro"));

        Assert.assertEquals(sbfont.getFontName(), "MyriadPro-Regular_Type1f0encstdcs");
        Assert.assertEquals(sbfont.getEncodingName(), "WinAnsiEncoding");
        Assert.assertEquals(sbfont.mapChar('W'), 'W');
        String x = IOUtils.toString(sbfont.getInputStream());
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
        PDDocument doc = PDDocument.load(new File(PDFBoxAdapterTestCase.CFF3));
        FOPPDFSingleByteFont sbfont = new FOPPDFSingleByteFont(getFont(doc, "T1_0"),
                "Myriad_Pro_Type1f0encf1cs");
        Assert.assertTrue(Arrays.asList(sbfont.getEncoding().getCharNameMap()).contains("uni004E"));
        Assert.assertEquals(sbfont.getFontName(), "Myriad_Pro_Type1f0encf1cs");
        Assert.assertEquals(sbfont.getEncodingName(), null);
        byte[] is = IOUtils.toByteArray(sbfont.getInputStream());

        CFFParser p = new CFFParser();
        CFFFont ff = p.parse(is).get(0);
        Assert.assertEquals(ff.getName(), "MNEACN+Myriad_Pro");
//        Assert.assertEquals(ff.getCharset().getEntries().get(0).getSID(), 391);

        doc.close();
    }

    @Test
    public void testTTCID() throws Exception {
        PDDocument doc = PDDocument.load(new File(PDFBoxAdapterTestCase.TTCID1));
        FOPPDFMultiByteFont mbfont = new FOPPDFMultiByteFont(getFont(doc, "C2_0"),
                "ArialMT_Type0");
        mbfont.addFont(getFont(doc, "C2_0"));
        Assert.assertEquals(mbfont.mapChar('t'), 67);

        PDDocument doc2 = PDDocument.load(new File(PDFBoxAdapterTestCase.TTCID2));
        String name = mbfont.addFont(getFont(doc2, "C2_0"));
        Assert.assertEquals(name, "ArialMT_Type0");
        Assert.assertEquals(mbfont.getFontName(), "ArialMT_Type0");
        byte[] is = IOUtils.toByteArray(mbfont.getInputStream());
        Assert.assertEquals(is.length, 38640);
        doc.close();
        doc2.close();
    }

    @Test
    public void testTTSubset() throws Exception {
        PDDocument doc = PDDocument.load(new File(PDFBoxAdapterTestCase.TTSubset1));
        FOPPDFSingleByteFont mbfont = new FOPPDFSingleByteFont(getFont(doc, "R9"),
                "TimesNewRomanPSMT_TrueType");
        mbfont.addFont(getFont(doc, "R9"));
//        Assert.assertEquals(mbfont.mapChar('t'), 116);

        PDDocument doc2 = PDDocument.load(new File(PDFBoxAdapterTestCase.TTSubset2));
        String name = mbfont.addFont(getFont(doc2, "R9"));
        Assert.assertEquals(name, "TimesNewRomanPSMT_TrueType");
        Assert.assertEquals(mbfont.getFontName(), "TimesNewRomanPSMT_TrueType");
        byte[] is = IOUtils.toByteArray(mbfont.getInputStream());
        Assert.assertEquals(is.length, 41112);
        doc.close();
        doc2.close();
    }

    @Test
    public void testType1Subset() throws Exception {
        PDDocument doc = PDDocument.load(new File(PDFBoxAdapterTestCase.Type1Subset1));
        FOPPDFSingleByteFont mbfont = new FOPPDFSingleByteFont(getFont(doc, "F15"), "");
        mbfont.addFont(getFont(doc, "F15"));
        PDDocument doc2 = PDDocument.load(new File(PDFBoxAdapterTestCase.Type1Subset2));
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
        PDDocument pdf = PDDocument.load(new File(PDFBoxAdapterTestCase.TTCID1));
        COSDictionary font = getFont(pdf, "C2_0");
        font.removeItem(COSName.TO_UNICODE);
        FOPPDFMultiByteFont multiByteFont = new FOPPDFMultiByteFont(font, null);
        Assert.assertTrue(multiByteFont.hadMappingOperations());
        pdf.close();
    }

    @Test
    public void testMappingNotFound() throws IOException {
        PDDocument pdf = PDDocument.load(new File(PDFBoxAdapterTestCase.TTCID1));
        final COSDictionary fontDict = getFont(pdf, "C2_0");
        MyFOPPDFMultiByteFont multiByteFont = new MyFOPPDFMultiByteFont(fontDict, null);
        PDType0Font font = (PDType0Font) multiByteFont.getFontContainer().getFont();
        GlyphTable glyphTable = ((PDCIDFontType2)font.getDescendantFont()).getTrueTypeFont().getGlyph();
        glyphTable.setGlyphs(new GlyphData[glyphTable.getGlyphs().length - 1]);
        String ex = "";
        try {
            multiByteFont.addFont(fontDict);
        } catch (IOException e) {
            ex = e.getMessage();
        }
        Assert.assertEquals(ex, "Mapping not found in glyphData");
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
    };
}
