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

package org.apache.fop.render.pdf;

import java.awt.Rectangle;
import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D;
import java.io.File;
import java.io.IOException;

import org.junit.Assert;
import org.junit.Test;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;

import org.apache.xmlgraphics.image.loader.Image;
import org.apache.xmlgraphics.image.loader.ImageInfo;
import org.apache.xmlgraphics.image.loader.impl.ImageGraphics2D;
import org.apache.xmlgraphics.java2d.GraphicContext;
import org.apache.xmlgraphics.java2d.ps.PSGraphics2D;

import org.apache.fop.render.pdf.pdfbox.ImageConverterPDF2G2D;
import org.apache.fop.render.pdf.pdfbox.ImagePDF;
import org.apache.fop.render.pdf.pdfbox.PDFBoxAdapter;
import org.apache.fop.render.pdf.pdfbox.PSPDFGraphics2D;



public class PDFRotateTestCase {

    @Test
    public void test() throws Exception {
        ImageConverterPDF2G2D i = new ImageConverterPDF2G2D();
        ImageInfo imgi = new ImageInfo("a", "b");
        PDDocument doc = new PDDocument();
        PDPage page = new PDPage();
        page.setRotation(90);
        doc.addPage(page);
        Image img = new ImagePDF(imgi, doc);
        ImageGraphics2D ig = (ImageGraphics2D)i.convert(img, null);
        Rectangle2D rect = new Rectangle2D.Float(0, 0, 100, 100);

        PSGraphics2D g2d = new PSPDFGraphics2D(true);
        GraphicContext gc = new GraphicContext();
        g2d.setGraphicContext(gc);
        ig.getGraphics2DImagePainter().paint(g2d, rect);
        Assert.assertEquals(g2d.getTransform().getShearX(), 0.16339869281045752, 0);
    }

    @Test
    public void testAngle() throws IOException {
        Assert.assertEquals(getTransform(90), new AffineTransform(0, 1, 1, 0, 0, 0));
        Assert.assertEquals(getTransform(270), new AffineTransform(0, -1, -1, 0, 842, 595));
        AffineTransform at = getTransform(180);
        Assert.assertEquals((int)at.getTranslateX(), 842);
        Assert.assertEquals(at.getTranslateY(), 0.0, 0);
    }

    private AffineTransform getTransform(int angle) throws IOException {
        PDFBoxAdapter adapter = PDFBoxAdapterTestCase.getPDFBoxAdapter(false, false);
        PDDocument doc = PDDocument.load(new File(PDFBoxAdapterTestCase.ROTATE));
        PDPage page = doc.getPage(0);
        page.setRotation(angle);
        AffineTransform at = new AffineTransform();
        Rectangle r = new Rectangle(0, 1650, 842000, 595000);
        String stream = (String) adapter.createStreamFromPDFBoxPage(doc, page, "key", at, null, r);
        Assert.assertTrue(stream.contains("/GS0106079 gs"));
        Assert.assertTrue(stream.contains("/TT0106079 1 Tf"));
        doc.close();
        return at;
    }
}
