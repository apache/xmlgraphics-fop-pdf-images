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

import java.awt.Rectangle;
import java.awt.geom.AffineTransform;
import java.io.IOException;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.apache.xmlgraphics.image.loader.Image;
import org.apache.xmlgraphics.image.loader.ImageFlavor;

import org.apache.fop.fonts.FontInfo;
import org.apache.fop.pdf.PDFXObject;
import org.apache.fop.render.ImageHandler;
import org.apache.fop.render.RenderingContext;
import org.apache.fop.render.pdf.PDFContentGenerator;
import org.apache.fop.render.pdf.PDFRenderingContext;

/**
 * Implementation of the {@link ImageHandler} interfaces
 * which can use PDFBox to parse an existing PDF file and write that to the
 * target PDF as a Form XObject.
 */
public class PDFBoxImageHandler extends AbstractPDFBoxHandler implements ImageHandler {

    /** logging instance */
    protected static final Log log = LogFactory.getLog(PDFBoxImageHandler.class);

    private static final ImageFlavor[] FLAVORS = new ImageFlavor[] {
        ImagePDF.PDFBOX_IMAGE
    };

    public void handleImage(RenderingContext context, Image image, Rectangle pos) throws IOException {
        assert context instanceof PDFRenderingContext;
        PDFRenderingContext pdfContext = (PDFRenderingContext)context;
        PDFContentGenerator generator = pdfContext.getGenerator();
        assert image instanceof ImagePDF;
        ImagePDF pdfImage = (ImagePDF)image;
        try {
            float x = (float)pos.getX() / 1000f;
            float y = (float)pos.getY() / 1000f;
            float w = (float)pos.getWidth() / 1000f;
            float h = (float)pos.getHeight() / 1000f;

            AffineTransform pageAdjust = new AffineTransform();
            AffineTransform at = generator.getAffineTransform();
            if (at != null) {
                pageAdjust.setToTranslation(
                    (float)(generator.getState().getTransform().getTranslateX()),
                    (float)(generator.getState().getTransform().getTranslateY() - h - y));
            }
            FontInfo fontinfo = (FontInfo)context.getHint("fontinfo");
            Object stream = createStreamForPDF(pdfImage, pdfContext.getPage(), pdfContext.getUserAgent(),
                    pageAdjust, fontinfo, pos, pdfContext.getPageNumbers(),
                    pdfContext.getPdfLogicalStructureHandler(), pdfContext.getCurrentSessionStructElem());

            if (stream == null) {
                return;
            }
            if (stream instanceof String) {
                if (pageAdjust.getScaleX() != 0) {
                    pageAdjust.translate(x * (1 / pageAdjust.getScaleX()), -y * (1 / -pageAdjust.getScaleY()));
                }
                generator.placeImage(pageAdjust, (String) stream);
            } else {
                generator.placeImage(x, y, w, h, (PDFXObject) stream);
            }
            pdfImage.close();
        } catch (Throwable t) {
            throw new RuntimeException(
                    "Error on PDF page: " + pdfImage.getInfo().getOriginalURI() + " " + t.getMessage(), t);
        }
    }

    /** {@inheritDoc} */
    public boolean isCompatible(RenderingContext targetContext, Image image) {
        return (image == null || image instanceof ImagePDF)
                && targetContext instanceof PDFRenderingContext;
    }

    /** {@inheritDoc} */
    public int getPriority() {
        //Before built-in handlers in case someone implements a PDF -> Graphics2D converter
        return 50;
    }

    /** {@inheritDoc} */
    public Class getSupportedImageClass() {
        return ImagePDF.class;
    }

    /** {@inheritDoc} */
    public ImageFlavor[] getSupportedImageFlavors() {
        return FLAVORS.clone();
    }

}
