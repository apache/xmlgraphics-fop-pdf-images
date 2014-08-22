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
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;

import org.apache.xmlgraphics.image.loader.util.ImageUtil;

import org.apache.fop.apps.FOUserAgent;
import org.apache.fop.events.EventBroadcaster;
import org.apache.fop.fonts.FontInfo;
import org.apache.fop.pdf.PDFDocument;
import org.apache.fop.pdf.PDFPage;
import org.apache.fop.pdf.PDFResources;
import org.apache.fop.pdf.Version;
import org.apache.fop.render.pdf.pdfbox.Cache.ValueMaker;

/**
 * Abstract base class for implementation of FOP's image handler interfaces (old and new)
 * which can use PDFBox to parse an existing PDF file and write that to the
 * target PDF as a Form XObject.
 */
public abstract class AbstractPDFBoxHandler {

    private static final Cache.Type CACHE_TYPE = Cache.Type.valueOf(
            System.getProperty("fop.pdfbox.doc-cache", Cache.Type.WEAK.name()).toUpperCase());

    private static Cache<String, Map<Object, Object>> createDocumentCache() {
        return Cache.createCache(CACHE_TYPE);
    }

    private static final ValueMaker<Map<Object, Object>> MAP_MAKER = new ValueMaker<Map<Object, Object>>() {
        public Map<Object, Object> make() throws Exception {
            return new HashMap<Object, Object>();
        }
    };

    private static Map<Object, Cache<String, Map<Object, Object>>> objectCacheMap
        = Collections.synchronizedMap(new WeakHashMap<Object, Cache<String, Map<Object, Object>>>());

    protected String createStreamForPDF(ImagePDF image, PDFPage targetPage, FOUserAgent userAgent,
            AffineTransform at, FontInfo fontinfo, Rectangle pos) throws IOException {

        EventBroadcaster eventBroadcaster = null;
        if (userAgent != null) {
            eventBroadcaster = userAgent.getEventBroadcaster();
        }
        String originalImageUri = image.getInfo().getOriginalURI();
        final int selectedPage = ImageUtil.needPageIndexFromURI(originalImageUri);

        PDDocument pddoc = image.getPDDocument();
        float pdfVersion = pddoc.getDocument().getVersion();
        Version inputDocVersion = Version.getValueOf(String.valueOf(pdfVersion));
        PDFDocument pdfDoc = targetPage.getDocument();

        if (pdfDoc.getPDFVersion().compareTo(inputDocVersion) < 0) {
            try {
                pdfDoc.setPDFVersion(inputDocVersion);
            } catch (IllegalStateException e) {
                getEventProducer(eventBroadcaster).pdfVersionMismatch(this,
                         pdfDoc.getPDFVersionString(), String.valueOf(pdfVersion));
            }
        }

        //Encryption test
        if (pddoc.isEncrypted()) {
            getEventProducer(eventBroadcaster).encryptedPdf(this);
            return null;
        }


        //Warn about potential problems with PDF/A and PDF/X
        if (pdfDoc.getProfile().isPDFAActive()) {
            getEventProducer(eventBroadcaster).pdfAActive(this);
        }
        if (pdfDoc.getProfile().isPDFXActive()) {
            getEventProducer(eventBroadcaster).pdfXActive(this);
        }

        Map<Object, Object> objectCache = getObjectCache(originalImageUri, userAgent);

        PDPage page = (PDPage) pddoc.getDocumentCatalog().getAllPages().get(selectedPage);

        if (targetPage.getPDFResources().getParentResources() == null) {
            PDFResources res = pdfDoc.getFactory().makeResources();
            res.setParentResources(pdfDoc.getResources());
            res.addContext(targetPage);
            targetPage.put("Resources", res);
        }

        PDFBoxAdapter adapter = new PDFBoxAdapter(targetPage, objectCache);
        String stream = adapter.createStreamFromPDFBoxPage(pddoc, page, originalImageUri,
                eventBroadcaster, at, fontinfo, pos);
        return stream;
    }

    private Map<Object, Object> getObjectCache(String originalImageUri,
            Object documentScopedReference) {
        String fileUri = getImagePath(originalImageUri);
        try {
            return getDocumentCache(documentScopedReference)
                        .getValue(fileUri, MAP_MAKER);
        } catch (Exception e) {
            // We cannot recover from this
            throw new RuntimeException(e);
        }
    }

    private Cache<String, Map<Object, Object>> getDocumentCache(Object documentScopedReference) {
        Cache<String, Map<Object, Object>> documentCache = objectCacheMap.get(documentScopedReference);
        if (documentCache == null) {
            documentCache = createDocumentCache();
            objectCacheMap.put(documentScopedReference, documentCache);
        }
        return documentCache;
    }

    private String getImagePath(String originalImageUri) {
        int hashIndex = originalImageUri.indexOf('#');
        if (hashIndex > 0) {
            return originalImageUri.substring(0, hashIndex);
        } else {
            return originalImageUri;
        }
    }

    private PDFBoxEventProducer getEventProducer(EventBroadcaster eventBroadcaster) {
        return PDFBoxEventProducer.Provider.get(eventBroadcaster);
    }
}
