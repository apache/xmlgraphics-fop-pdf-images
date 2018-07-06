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

/* $Id: ImageConverterPDF2G2D.java 1808727 2017-09-18 15:02:56Z ssteiner $ */
package org.apache.fop.render.pdf;

import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;

import org.junit.Assert;
import org.junit.Test;

import org.apache.pdfbox.pdmodel.PDDocument;

import org.apache.xmlgraphics.image.loader.ImageException;
import org.apache.xmlgraphics.image.loader.ImageInfo;
import org.apache.xmlgraphics.image.loader.impl.ImageGraphics2D;
import org.apache.xmlgraphics.java2d.GeneralGraphics2DImagePainter;
import org.apache.xmlgraphics.java2d.GraphicContext;

import org.apache.fop.fonts.EmbedFontInfo;
import org.apache.fop.fonts.EmbeddingMode;
import org.apache.fop.fonts.FontUris;
import org.apache.fop.fonts.LazyFont;
import org.apache.fop.fonts.MultiByteFont;
import org.apache.fop.fonts.Typeface;
import org.apache.fop.render.pdf.pdfbox.ImageConverterPDF2G2D;
import org.apache.fop.render.pdf.pdfbox.ImagePDF;
import org.apache.fop.render.pdf.pdfbox.PSPDFGraphics2D;

public class ImageConverterPDF2G2DTestCase {
    private static final String FONTSNOTEMBEDDED = "test/resources/fontsnotembedded.pdf";
    private static final String FONTSNOTEMBEDDEDCID = "test/resources/fontsnotembeddedcid.pdf";

    @Test
    public void testFontsNotEmbedded() throws IOException, ImageException {
        Assert.assertTrue(pdfToPS(FONTSNOTEMBEDDED, "Helvetica-Bold"));
        Assert.assertFalse(pdfToPS(FONTSNOTEMBEDDED, "xyz"));

        Assert.assertTrue(pdfToPS(FONTSNOTEMBEDDEDCID, "NewsMinIWA-Th"));
        Assert.assertFalse(pdfToPS(FONTSNOTEMBEDDEDCID, "xyz"));
    }

    private boolean pdfToPS(String pdf, String font) throws IOException, ImageException {
        ImageConverterPDF2G2D i = new ImageConverterPDF2G2D();
        ImageInfo imgi = new ImageInfo(pdf, "b");
        PDDocument doc = PDDocument.load(new File(pdf));
        org.apache.xmlgraphics.image.loader.Image img = new ImagePDF(imgi, doc);
        ImageGraphics2D ig = (ImageGraphics2D)i.convert(img, null);
        GeneralGraphics2DImagePainter g = (GeneralGraphics2DImagePainter) ig.getGraphics2DImagePainter();
        MyLazyFont lazyFont = new MyLazyFont();
        g.addFallbackFont(font, lazyFont);
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        PSPDFGraphics2D g2d = (PSPDFGraphics2D)
                g.getGraphics(true, new PDFBoxAdapterTestCase.FOPPSGeneratorImpl(stream));
        Rectangle2D rect = new Rectangle2D.Float(0, 0, 100, 100);
        GraphicContext gc = new GraphicContext();
        g2d.setGraphicContext(gc);
        g.paint(g2d, rect);
        doc.close();
        return lazyFont.font.fontUsed;
    }

    static class MyLazyFont extends LazyFont {
        Font font = new Font();
        MyLazyFont() {
            super(new EmbedFontInfo(new FontUris(null, null), false, false, null, ""), null, false);
        }
        public Typeface getRealFont() {
            return font;
        }
    }

    static class Font extends MultiByteFont {
        boolean fontUsed;
        public Font() {
            super(null, EmbeddingMode.AUTO);
        }
        public boolean isOTFFile() {
            fontUsed = true;
            return true;
        }
    }

    @Test
    public void testPDFToImage() throws IOException, ImageException {
        PDDocument doc = PDDocument.load(new File(FONTSNOTEMBEDDED));
        ImageInfo imgi = new ImageInfo(FONTSNOTEMBEDDED, "b");
        org.apache.xmlgraphics.image.loader.Image img = new ImagePDF(imgi, doc);
        ImageConverterPDF2G2D imageConverterPDF2G2D = new ImageConverterPDF2G2D();
        ImageGraphics2D fopGraphics2D = (ImageGraphics2D) imageConverterPDF2G2D.convert(img, null);
        BufferedImage image = new BufferedImage(100, 100, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics2D = image.createGraphics();
        fopGraphics2D.getGraphics2DImagePainter().paint(graphics2D, new Rectangle(0, 0, 1000, 1000));
        doc.close();
        Assert.assertEquals(graphics2D.getTransform().getScaleX(), 1.63, 0);
    }
}
