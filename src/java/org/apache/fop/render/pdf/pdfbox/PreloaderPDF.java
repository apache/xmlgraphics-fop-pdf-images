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
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

import javax.imageio.stream.ImageInputStream;
import javax.xml.transform.Source;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.encryption.BadSecurityHandlerException;
import org.apache.pdfbox.pdmodel.encryption.DecryptionMaterial;
import org.apache.pdfbox.pdmodel.encryption.StandardDecryptionMaterial;

import org.apache.xmlgraphics.image.loader.ImageContext;
import org.apache.xmlgraphics.image.loader.ImageException;
import org.apache.xmlgraphics.image.loader.ImageInfo;
import org.apache.xmlgraphics.image.loader.ImageSize;
import org.apache.xmlgraphics.image.loader.impl.AbstractImagePreloader;
import org.apache.xmlgraphics.image.loader.util.ImageUtil;
import org.apache.xmlgraphics.util.UnitConv;
import org.apache.xmlgraphics.util.io.SubInputStream;

import org.apache.fop.datatypes.URISpecification;
import org.apache.fop.render.pdf.pdfbox.Cache.ValueMaker;

/**
 * Image preloader for PDF images.
 */
public class PreloaderPDF extends AbstractImagePreloader {

    /** PDF header text */
    private static final String PDF_HEADER = "%PDF-";

    /** static PDDocument cache for faster multi-page processing */
    private static final Cache.Type CACHE_TYPE =Cache.Type.valueOf(
            System.getProperty("fop.pdfbox.preloader-cache", Cache.Type.WEAK.name()).toUpperCase());

    private static Map<Object, Cache<URI, PDDocument>> documentCacheMap
            = Collections.synchronizedMap(new WeakHashMap<Object, Cache<URI, PDDocument>>());
    //the cache here can cause problems because PDDocument that have been closed might still
    //be accessed. Example: java.io.IOException: The handle is invalid

    /** {@inheritDoc} */
    public ImageInfo preloadImage(String uri, Source src, ImageContext context)
                throws IOException, ImageException {
        if (!ImageUtil.hasImageInputStream(src)) {
            return null;
        }
        ImageInfo info = null;
        ImageInputStream in = ImageUtil.needImageInputStream(src);
        in.mark();
        try {
            byte[] header = getHeader(in, PDF_HEADER.length());
            String s = new String(header, "US-ASCII");

            boolean supported = PDF_HEADER.equals(s);
            if (supported) {
                info = loadPDF(uri, src, context);
            }
            return info;
        } finally {
            if (info == null) {
                in.reset(); //Error detected or not a PDF file
            }
        }
    }

    private static URI deriveDocumentURI(String uri) throws ImageException {
        try {
            URI originalURI = new URI(URISpecification.escapeURI(uri));
            URI tempURI = new URI(originalURI.getScheme(),
                    originalURI.getSchemeSpecificPart(), null);
            return tempURI;
        } catch (URISyntaxException e) {
            throw new ImageException("Problem processing URI", e);
        }
    }

    private ImageInfo loadPDF(String uri, Source src, ImageContext context) throws IOException,
            ImageException {
        InputStream in = ImageUtil.needInputStream(src);

        int selectedPage = ImageUtil.needPageIndexFromURI(uri);

        URI docURI = deriveDocumentURI(src.getSystemId());

        PDDocument pddoc = getDocument(context, docURI, src);
        pddoc = Interceptors.getInstance().interceptOnLoad(pddoc, docURI);

        //Disable the warning about a missing close since we rely on the GC to decide when
        //the cached PDF shall be disposed off.
        pddoc.getDocument().setWarnMissingClose(false);

        if (pddoc.isEncrypted()) {
            //Try decrypting with an empty password
            DecryptionMaterial dec = new StandardDecryptionMaterial("");
            try {
                pddoc.openProtection(dec);
            } catch (org.apache.pdfbox.exceptions.CryptographyException e) {
                notifyCouldNotDecrypt(e);
            } catch (BadSecurityHandlerException e) {
                notifyCouldNotDecrypt(e);
            }
        }

        int pageCount = pddoc.getNumberOfPages();
        if (selectedPage < 0 || selectedPage >= pageCount) {
            throw new ImageException("Selected page (index: " + selectedPage
                    + ") does not exist in the PDF file. The document has "
                    + pddoc.getNumberOfPages() + " pages.");
        }
        PDPage page = (PDPage)pddoc.getDocumentCatalog().getAllPages().get(selectedPage);
        PDRectangle mediaBox = page.findMediaBox();
        PDRectangle cropBox = page.findCropBox();
        PDRectangle viewBox = (cropBox != null ? cropBox : mediaBox);
        int w = Math.round(viewBox.getWidth() * 1000);
        int h = Math.round(viewBox.getHeight() * 1000);

        //Handle the /Rotation entry on the page dict
        int rotation = PDFUtil.getNormalizedRotation(page);
        if (rotation == 90 || rotation == 270) {
            //Swap width and height
            int exch = w;
            w = h;
            h = exch;
        }

        ImageSize size = new ImageSize();
        float ratioToDefault = context.getSourceResolution() / UnitConv.IN2PT;
        size.setSizeInMillipoints((int)(w * ratioToDefault), (int)(h * ratioToDefault));
        size.setResolution(context.getSourceResolution());
        size.calcPixelsFromSize();

        ImageInfo info = new ImageInfo(uri, ImagePDF.MIME_PDF);
        info.setSize(size);
        info.getCustomObjects().put(ImageInfo.ORIGINAL_IMAGE, new ImagePDF(info, pddoc));

        int lastPageIndex = pddoc.getNumberOfPages() - 1;
        if (selectedPage < lastPageIndex) {
            info.getCustomObjects().put(ImageInfo.HAS_MORE_IMAGES, Boolean.TRUE);
        }

        return info;
    }

    private void notifyCouldNotDecrypt(Exception e)
            throws ImageException {
        throw new ImageException("Error decrypting PDF: "
                + e.getMessage()
                + "\nPlease use an OnLoadInterceptor to provide "
                + "suitable decryption material (ex. a password).", e);
    }

    private PDDocument getDocument(Object context, URI uri, Source src)
            throws IOException {
        try {
            return getDocumentCache(context).getValue(uri, createDocumentMaker(src, uri));
        } catch (IOException ioe) {
            throw ioe;
        } catch (Exception e) {
            // We cannot recover from this
            throw new RuntimeException(e);
        }
    }

    private Cache<URI, PDDocument> getDocumentCache(Object context) {
        Cache<URI, PDDocument> documentCache = documentCacheMap.get(context);

        if (documentCache == null) {
            documentCache = Cache.createCache(CACHE_TYPE);
            documentCacheMap.put(context, documentCache);
        }
        return documentCache;
    }

    private ValueMaker<PDDocument> createDocumentMaker(final Source src, final URI docURI) {
        return new ValueMaker<PDDocument>() {
            public PDDocument make() throws Exception {
                final InputStream in = ImageUtil.needInputStream(src);
                try {
                    PDDocument pddoc = PDDocument.load(new SubInputStream(in, Integer.MAX_VALUE));
                    return Interceptors.getInstance().interceptOnLoad(pddoc, docURI);
                } finally {
                    ImageUtil.closeQuietly(src);
                }
            }
        };
    }
}
