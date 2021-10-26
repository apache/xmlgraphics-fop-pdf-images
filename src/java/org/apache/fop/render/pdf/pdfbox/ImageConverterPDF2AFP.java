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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Map;

import org.apache.pdfbox.multipdf.Splitter;
import org.apache.pdfbox.pdmodel.PDDocument;

import org.apache.xmlgraphics.image.loader.Image;
import org.apache.xmlgraphics.image.loader.ImageFlavor;
import org.apache.xmlgraphics.image.loader.MimeEnabledImageFlavor;
import org.apache.xmlgraphics.image.loader.impl.AbstractImageConverter;
import org.apache.xmlgraphics.image.loader.impl.ImageRawStream;
import org.apache.xmlgraphics.image.loader.util.ImageUtil;

public class ImageConverterPDF2AFP extends AbstractImageConverter {
    public Image convert(Image src, Map hints) throws IOException {
        if (!(src instanceof ImagePDF)) {
            return null;
        }
        ImagePDF imgPDF = (ImagePDF)src;
        int selectedPage = ImageUtil.needPageIndexFromURI(src.getInfo().getOriginalURI());
        PDDocument pdDocument = imgPDF.getPDDocument();
        Splitter splitter = new Splitter();
        splitter.setStartPage(selectedPage + 1);
        splitter.setEndPage(selectedPage + 1);
        pdDocument = splitter.split(pdDocument).get(0);
        ByteArrayOutputStream pdf = new ByteArrayOutputStream();
        pdDocument.save(pdf);
        MimeEnabledImageFlavor imageFlavor = new MimeEnabledImageFlavor(src.getFlavor(), ImagePDF.MIME_PDF);
        return new ImageRawStream(src.getInfo(), imageFlavor, new ByteArrayInputStream(pdf.toByteArray()));
    }

    public ImageFlavor getSourceFlavor() {
        return ImagePDF.PDFBOX_IMAGE;
    }

    public ImageFlavor getTargetFlavor() {
        return ImageFlavor.RAW;
    }

    public int getConversionPenalty() {
        return 100;
    }
}
