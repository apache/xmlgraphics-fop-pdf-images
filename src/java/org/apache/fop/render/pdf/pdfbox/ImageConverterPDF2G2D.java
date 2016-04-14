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

import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D;
import java.io.IOException;
import java.util.Map;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.rendering.PDFRenderer;

import org.apache.xmlgraphics.image.loader.Image;
import org.apache.xmlgraphics.image.loader.ImageException;
import org.apache.xmlgraphics.image.loader.ImageFlavor;
import org.apache.xmlgraphics.image.loader.impl.AbstractImageConverter;
import org.apache.xmlgraphics.image.loader.impl.ImageGraphics2D;
import org.apache.xmlgraphics.image.loader.util.ImageUtil;
import org.apache.xmlgraphics.java2d.GeneralGraphics2DImagePainter;
import org.apache.xmlgraphics.java2d.Graphics2DImagePainter;
import org.apache.xmlgraphics.ps.PSGenerator;

/**
 * Image converter implementation to convert PDF pages into Java2D images.
 */
public class ImageConverterPDF2G2D extends AbstractImageConverter {

    /** {@inheritDoc} */
    public Image convert(Image src, Map hints) throws ImageException,
            IOException {
        checkSourceFlavor(src);
        assert src instanceof ImagePDF;
        ImagePDF imgPDF = (ImagePDF)src;

        final int selectedPage = ImageUtil.needPageIndexFromURI(
                src.getInfo().getOriginalURI());

        PDDocument pddoc = imgPDF.getPDDocument();

        Graphics2DImagePainter painter = new Graphics2DImagePainterPDF(pddoc, selectedPage);

        ImageGraphics2D g2dImage = new ImageGraphics2D(src.getInfo(), painter);
        return g2dImage;
    }

    /** {@inheritDoc} */
    public ImageFlavor getSourceFlavor() {
        return ImagePDF.PDFBOX_IMAGE;
    }

    /** {@inheritDoc} */
    public ImageFlavor getTargetFlavor() {
        return ImageFlavor.GRAPHICS2D;
    }

    /** {@inheritDoc} */
    @Override
    public int getConversionPenalty() {
        return 1000; //Use only if no native embedding is possible
    }

    private static class Graphics2DImagePainterPDF implements GeneralGraphics2DImagePainter {

        private final PDPage page;
        private final PDDocument pdDocument;
        private int selectedPage;

        public Graphics2DImagePainterPDF(PDDocument pddoc, int selectedPage) {
            pdDocument = pddoc;
            this.selectedPage = selectedPage;
            page = pdDocument.getPage(selectedPage);
        }

        /** {@inheritDoc} */
        public Dimension getImageSize() {
            PDRectangle mediaBox = page.getMediaBox();
            int wmpt = (int)Math.ceil(mediaBox.getWidth() * 1000);
            int hmpt = (int)Math.ceil(mediaBox.getHeight() * 1000);
            return new Dimension(wmpt, hmpt);
        }

        /** {@inheritDoc} */
        public void paint(Graphics2D g2d, Rectangle2D area) {
            try {
                PDRectangle mediaBox = page.getCropBox();
                AffineTransform at = new AffineTransform();
                int rotation = page.getRotation();
                if (rotation == 90 || rotation == 270) {
                    at.scale(area.getWidth() / area.getHeight(), area.getHeight() / area.getWidth());
                }
                at.translate(area.getX(), area.getY());
                at.scale(area.getWidth() / mediaBox.getWidth(),
                        area.getHeight() / mediaBox.getHeight());
                g2d.transform(at);
                new PDFRenderer(pdDocument).renderPageToGraphics(selectedPage, g2d);
            } catch (IOException ioe) {
                //TODO Better exception handling
                throw new RuntimeException("I/O error while painting PDF page", ioe);
            }
        }

        public Graphics2D getGraphics(boolean textAsShapes, PSGenerator gen) {
            PSPDFGraphics2D graphics = new PSPDFGraphics2D(textAsShapes, gen);
            return graphics;
        }
    }

}
