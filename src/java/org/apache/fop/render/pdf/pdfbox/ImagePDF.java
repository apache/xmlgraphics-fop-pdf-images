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

import java.io.IOException;

import org.apache.pdfbox.pdmodel.PDDocument;

import org.apache.xmlgraphics.image.loader.ImageFlavor;
import org.apache.xmlgraphics.image.loader.ImageInfo;
import org.apache.xmlgraphics.image.loader.impl.AbstractImage;

/**
 * Represents a PDF document as an image.
 */
public class ImagePDF extends AbstractImage {

    /** MIME type for PDF */
    public static final String MIME_PDF = "application/pdf";

    /** ImageFlavor for PDF */
    public static final ImageFlavor PDFBOX_IMAGE = new ImageFlavor("PDFBox");

    private final PDDocument pddoc;

    /**
     * Create an PDF image with the image information.
     *
     * @param info the information containing the data and bounding box
     * @param doc the PDF document
     */
    public ImagePDF(ImageInfo info, PDDocument doc) {
        super(info);
        this.pddoc = doc;
    }

    /**
     * Returns the root PDDocument instance representing the PDF image.
     * @return the root PDDocument
     */
    public PDDocument getPDDocument() {
        return this.pddoc;
    }

    /** {@inheritDoc} */
    public ImageFlavor getFlavor() {
        return PDFBOX_IMAGE;
    }

    /** {@inheritDoc} */
    public boolean isCacheable() {
        return false;
    }

    public void close() {
        try {
            pddoc.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

}
