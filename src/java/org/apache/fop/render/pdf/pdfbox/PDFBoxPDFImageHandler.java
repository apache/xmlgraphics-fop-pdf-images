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

import java.awt.Point;
import java.awt.Rectangle;
import java.io.IOException;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.apache.xmlgraphics.image.loader.Image;
import org.apache.xmlgraphics.image.loader.ImageFlavor;

import org.apache.fop.pdf.PDFFormXObject;
import org.apache.fop.pdf.PDFPage;
import org.apache.fop.pdf.PDFXObject;
import org.apache.fop.render.RendererContext;
import org.apache.fop.render.pdf.PDFImageHandler;
import org.apache.fop.render.pdf.PDFRenderer;
import org.apache.fop.render.pdf.PDFRendererContextConstants;

/**
 * Implementation of the {@link PDFImageHandler} interface
 * which can use PDFBox to parse an existing PDF file and write that to the
 * target PDF as a Form XObject.
 */
public class PDFBoxPDFImageHandler extends AbstractPDFBoxHandler implements PDFImageHandler {

    /** logging instance */
    protected static Log log = LogFactory.getLog(PDFBoxPDFImageHandler.class);

    private static final ImageFlavor[] FLAVORS = new ImageFlavor[] {
        ImagePDF.PDFBOX_IMAGE,
    };

    /** {@inheritDoc} */
    public PDFXObject generateImage(RendererContext context, Image image,
            Point origin, Rectangle pos)
            throws IOException {
        PDFRenderer renderer = (PDFRenderer)context.getRenderer();
        ImagePDF pdfImage = (ImagePDF)image;
        PDFPage pdfPage = (PDFPage)context.getProperty(
                PDFRendererContextConstants.PDF_PAGE);

        PDFFormXObject form = createFormForPDF(pdfImage, pdfPage);
        if (form == null) {
            return null;
        }

        float x = (float)pos.getX() / 1000f;
        float y = (float)pos.getY() / 1000f;
        float w = (float)pos.getWidth() / 1000f;
        float h = (float)pos.getHeight() / 1000f;
        renderer.placeImage(x, y, w, h, form);

        return form;
    }

    /** {@inheritDoc} */
    public int getPriority() {
        //Before built-in handlers in case someone implements a PDF -> Graphics2D converter
        return 50;
    }

    /** {@inheritDoc} */
    public Class<?> getSupportedImageClass() {
        return ImagePDF.class;
    }

    public ImageFlavor[] getSupportedImageFlavors() {
        return FLAVORS;
    }

}
