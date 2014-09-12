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
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import javax.imageio.ImageIO;

import org.apache.fop.pdf.PDFAnnotList;
import org.apache.fop.pdf.PDFArray;
import org.apache.fop.pdf.PDFGState;
import org.apache.fop.render.pdf.pdfbox.FOPPDFMultiByteFont;
import org.apache.fop.render.pdf.pdfbox.FOPPDFSingleByteFont;
import org.apache.fop.render.pdf.pdfbox.ImagePDF;
import org.apache.fop.render.pdf.pdfbox.PDFBoxImageHandler;
import org.junit.Test;

import org.apache.commons.io.IOUtils;
import org.apache.fontbox.cff.CFFFont;
import org.apache.fontbox.cff.CFFParser;
import org.apache.fontbox.ttf.TTFParser;
import org.apache.fontbox.type1.Type1Font;
import org.apache.pdfbox.cos.COSDictionary;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDResources;

import org.apache.xmlgraphics.image.loader.ImageInfo;
import org.apache.xmlgraphics.image.loader.ImageSource;
import org.apache.xmlgraphics.image.loader.impl.DefaultImageContext;

import org.apache.fop.fonts.CustomFont;
import org.apache.fop.fonts.FontInfo;
import org.apache.fop.fonts.FontType;
import org.apache.fop.fonts.MultiByteFont;
import org.apache.fop.fonts.Typeface;
import org.apache.fop.pdf.PDFDocument;
import org.apache.fop.pdf.PDFPage;
import org.apache.fop.pdf.PDFResources;
import org.apache.fop.render.pdf.pdfbox.PDFBoxAdapter;
import org.apache.fop.render.pdf.pdfbox.PreloaderPDF;

import junit.framework.Assert;

public class PDFBoxAdapterTestCase {
    private Rectangle2D r = new Rectangle2D.Double();
    private PDFPage pdfpage = new PDFPage(new PDFResources(0), 0, r, r, r, r);
    private static final String CFF1 = "test/resources/2fonts.pdf";
    private static final String CFF2 = "test/resources/2fonts2.pdf";
    private static final String CFF3 = "test/resources/simpleh.pdf";
    private static final String TTCID1 = "test/resources/ttcid1.pdf";
    private static final String TTCID2 = "test/resources/ttcid2.pdf";
    private static final String TTSubset1 = "test/resources/ttsubset.pdf";
    private static final String TTSubset2 = "test/resources/ttsubset2.pdf";
    private static final String TTSubset3 = "test/resources/ttsubset3.pdf";
    private static final String TTSubset5 = "test/resources/ttsubset5.pdf";
    private static final String CFFCID1 = "test/resources/cffcid1.pdf";
    private static final String CFFCID2 = "test/resources/cffcid2.pdf";
    private static final String Type1Subset1 = "test/resources/t1subset.pdf";
    private static final String Type1Subset2 = "test/resources/t1subset2.pdf";
    private static final String Type1Subset3 = "test/resources/t1subset3.pdf";
    private static final String Type1Subset4 = "test/resources/t1subset4.pdf";
    private static final String ROTATE = "test/resources/rotate.pdf";
    private static final String SHADING = "test/resources/shading.pdf";
    private static final String LINK = "test/resources/link.pdf";

    private PDFBoxAdapter getPDFBoxAdapter() {
        PDFDocument doc = new PDFDocument("");
        doc.setMergeFontsEnabled(true);
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

        for (Typeface font : fi.getUsedFonts().values()) {
            InputStream is = ((CustomFont) font).getInputStream();
            if (font.getFontType() == FontType.TYPE1C || font.getFontType() == FontType.CIDTYPE0) {
                byte[] data = IOUtils.toByteArray(is);
                CFFParser p = new CFFParser();
                p.parse(data).get(0);
            } else if (font.getFontType() == FontType.TRUETYPE) {
                TTFParser parser = new TTFParser();
                parser.parseTTF(is);
            } else if (font.getFontType() == FontType.TYPE0) {
                TTFParser parser = new TTFParser(true);
                parser.parseTTF(is);
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
        PDDocument doc = PDDocument.load(pdf);
        PDPage page = (PDPage) doc.getDocumentCatalog().getAllPages().get(0);
        AffineTransform at = new AffineTransform();
        String c = getPDFBoxAdapter().createStreamFromPDFBoxPage(doc, page, pdf, at, fi, new Rectangle());
//        PDResources sourcePageResources = page.findResources();
//        COSDictionary fonts = (COSDictionary)sourcePageResources.getCOSDictionary().getDictionaryObject(COSName.FONT);
//        PDFBoxAdapter.PDFWriter w = adapter. new MergeFontsPDFWriter(fonts, fi, "", new ArrayList<COSName>());
//        String c = w.writeText(page.getContents());
        doc.close();
        return c;
    }

    private COSDictionary getFont(PDDocument doc, String internalname) throws IOException {
        PDPage page = (PDPage) doc.getDocumentCatalog().getAllPages().get(0);
        PDResources sourcePageResources = page.findResources();
        COSDictionary fonts = (COSDictionary)sourcePageResources.getCOSDictionary().getDictionaryObject(COSName.FONT);
        return (COSDictionary) fonts.getDictionaryObject(internalname);
    }

    @Test
    public void testCFF() throws Exception {
        PDDocument doc = PDDocument.load(CFF1);
        FOPPDFSingleByteFont sbfont = new FOPPDFSingleByteFont(getFont(doc, "R11"),
                "MyriadPro-Regular_Type1f0encstdcs");

        Assert.assertTrue(Arrays.asList(sbfont.getEncoding().getCharNameMap()).contains("bracketright"));
        Assert.assertTrue(!Arrays.asList(sbfont.getEncoding().getCharNameMap()).contains("A"));
        Assert.assertTrue(!Arrays.toString(sbfont.getEncoding().getUnicodeCharMap()).contains("A"));
        Assert.assertEquals(sbfont.mapChar('A'), 0);
        Assert.assertEquals(sbfont.getWidths().length, 28);
        Assert.assertEquals(sbfont.getFirstChar(), 87);
        Assert.assertEquals(sbfont.getLastChar(), 114);

        PDDocument doc2 = PDDocument.load(CFF2);
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
        PDDocument doc = PDDocument.load(CFF3);
        FOPPDFSingleByteFont sbfont = new FOPPDFSingleByteFont(getFont(doc, "T1_0"),
                "Myriad_Pro_Type1f0encf1cs");
        Assert.assertTrue(Arrays.asList(sbfont.getEncoding().getCharNameMap()).contains("uni004E"));
        Assert.assertEquals(sbfont.getFontName(), "Myriad_Pro_Type1f0encf1cs");
        Assert.assertEquals(sbfont.getEncodingName(), null);
        byte[] is = IOUtils.toByteArray(sbfont.getInputStream());

        CFFParser p = new CFFParser();
        CFFFont ff = p.parse(is).get(0);
        Assert.assertEquals(ff.getName(), "MNEACN+Myriad_Pro");
        Assert.assertEquals(ff.getCharset().getEntries().get(0).getSID(), 391);

        doc.close();
    }

    @Test
    public void testTTCID() throws Exception {
        PDDocument doc = PDDocument.load(TTCID1);
        FOPPDFMultiByteFont mbfont = new FOPPDFMultiByteFont(getFont(doc, "C2_0"),
                "ArialMT_Type0");
        mbfont.addFont(getFont(doc, "C2_0"));
        Assert.assertEquals(mbfont.mapChar('t'), 67);

        PDDocument doc2 = PDDocument.load(TTCID2);
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
        PDDocument doc = PDDocument.load(TTSubset1);
        FOPPDFSingleByteFont mbfont = new FOPPDFSingleByteFont(getFont(doc, "R9"),
                "TimesNewRomanPSMT_TrueType");
        mbfont.addFont(getFont(doc, "R9"));
        Assert.assertEquals(mbfont.mapChar('t'), 116);

        PDDocument doc2 = PDDocument.load(TTSubset2);
        String name = mbfont.addFont(getFont(doc2, "R9"));
        Assert.assertEquals(name, "TimesNewRomanPSMT_TrueType");
        Assert.assertEquals(mbfont.getFontName(), "TimesNewRomanPSMT_TrueType");
        byte[] is = IOUtils.toByteArray(mbfont.getInputStream());
        Assert.assertEquals(is.length, 34264);
        doc.close();
        doc2.close();
    }

    @Test
    public void testType1Subset() throws Exception {
        PDDocument doc = PDDocument.load(Type1Subset1);
        FOPPDFSingleByteFont mbfont = new FOPPDFSingleByteFont(getFont(doc, "F15"), "");
        mbfont.addFont(getFont(doc, "F15"));
        PDDocument doc2 = PDDocument.load(Type1Subset2);
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
    public void testStream() throws Exception {
        pdfpage.setDocument(new PDFDocument(""));
        PDFBoxAdapter adapter = new PDFBoxAdapter(pdfpage, new HashMap(), new HashMap<Integer, PDFArray>());
        PDDocument doc = PDDocument.load(ROTATE);
        PDPage page = (PDPage) doc.getDocumentCatalog().getAllPages().get(0);
        AffineTransform at = new AffineTransform();
        Rectangle r = new Rectangle(0, 1650, 842000, 595000);
        String stream = adapter.createStreamFromPDFBoxPage(doc, page, "key", at, null, r);
        Assert.assertEquals(at, new AffineTransform(-0.0, 1.0000000554888686, 1.0000000554888686, 0.0, 0.0,
                -2.0742416381835938E-5));
        Assert.assertTrue(stream.contains("/GS0106079 gs"));
        Assert.assertTrue(stream.contains("/TT0106079 1 Tf"));
        doc.close();
    }

    @Test
    public void testLink() throws Exception {
        pdfpage.setObjectNumber(1);
        PDFDocument pdfdoc = new PDFDocument("");
        pdfpage.setDocument(pdfdoc);
        Map<Integer, PDFArray> pageNumbers = new HashMap<Integer, PDFArray>();
        PDFBoxAdapter adapter = new PDFBoxAdapter(pdfpage, new HashMap(), pageNumbers);
        PDDocument doc = PDDocument.load(LINK);
        PDPage page = (PDPage) doc.getDocumentCatalog().getAllPages().get(0);
        AffineTransform at = new AffineTransform();
        Rectangle r = new Rectangle(0, 1650, 842000, 595000);
        String stream = adapter.createStreamFromPDFBoxPage(doc, page, "key", at, null, r);
        Assert.assertTrue(stream.contains("/Link <</MCID 5 >>BDC"));
        Assert.assertTrue(pageNumbers.size() == 4);
        PDFAnnotList annots = (PDFAnnotList) pdfpage.get("Annots");
        Assert.assertEquals(annots.toPDFString(), "[\n9 0 R\n12 0 R\n]");
//        pdfdoc.output(System.out);
        doc.close();
    }

    @Test
    public void testPreloaderPDF() throws Exception {
        ImageSource imageSource = new ImageSource(ImageIO.createImageInputStream(new File(ROTATE)), "", true);
        ImageInfo imageInfo = new PreloaderPDF().preloadImage("", imageSource, new DefaultImageContext());
        Assert.assertEquals(imageInfo.getMimeType(), "application/pdf");
    }


    @Test
    public void testPDFBoxImageHandler() throws Exception {
        ImageInfo imgi = new ImageInfo("a", "b");
        PDDocument doc = PDDocument.load(SHADING);
        ImagePDF img = new ImagePDF(imgi, doc);
        PDFDocument pdfdoc = new PDFDocument("");
        pdfpage.setDocument(pdfdoc);
        PDFGState g = new PDFGState();
        pdfdoc.assignObjectNumber(g);
        pdfpage.addGState(g);
        PDFContentGenerator con = new PDFContentGenerator(pdfdoc, null, null);
        PDFRenderingContext c = new PDFRenderingContext(null, con, pdfpage, null);
        c.setPageNumbers(new HashMap<Integer, PDFArray>());
        new PDFBoxImageHandler().handleImage(c, img, new Rectangle());
        PDFResources res = c.getPage().getPDFResources();
        OutputStream bos = new ByteArrayOutputStream();
        res.output(bos);
        Assert.assertTrue(bos.toString().contains("/ExtGState << /GS5"));
    }
}
