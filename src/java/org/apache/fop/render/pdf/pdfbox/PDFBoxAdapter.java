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
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Pattern;

import org.apache.commons.io.IOUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.fontbox.cff.CFFFont;
import org.apache.fontbox.cff.CFFFontROS;
import org.apache.fontbox.cff.charset.CFFCharset;
import org.apache.fontbox.cff.encoding.CFFEncoding;
import org.apache.fontbox.cmap.CMap;
import org.apache.fontbox.ttf.CMAPEncodingEntry;
import org.apache.fontbox.ttf.GlyphData;
import org.apache.fontbox.ttf.MaximumProfileTable;
import org.apache.fontbox.ttf.TrueTypeFont;
import org.apache.pdfbox.cos.COSArray;
import org.apache.pdfbox.cos.COSBase;
import org.apache.pdfbox.cos.COSBoolean;
import org.apache.pdfbox.cos.COSDictionary;
import org.apache.pdfbox.cos.COSFloat;
import org.apache.pdfbox.cos.COSInteger;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.cos.COSNull;
import org.apache.pdfbox.cos.COSNumber;
import org.apache.pdfbox.cos.COSObject;
import org.apache.pdfbox.cos.COSStream;
import org.apache.pdfbox.cos.COSString;
import org.apache.pdfbox.encoding.DictionaryEncoding;
import org.apache.pdfbox.encoding.Encoding;
import org.apache.pdfbox.encoding.EncodingManager;
import org.apache.pdfbox.pdfparser.PDFStreamParser;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentCatalog;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageNode;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.common.COSObjectable;
import org.apache.pdfbox.pdmodel.common.COSStreamArray;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.common.PDStream;
import org.apache.pdfbox.pdmodel.font.PDCIDFont;
import org.apache.pdfbox.pdmodel.font.PDCIDFontType0Font;
import org.apache.pdfbox.pdmodel.font.PDCIDFontType2Font;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDFontDescriptorDictionary;
import org.apache.pdfbox.pdmodel.font.PDFontFactory;
import org.apache.pdfbox.pdmodel.font.PDSimpleFont;
import org.apache.pdfbox.pdmodel.font.PDTrueTypeFont;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotation;
import org.apache.pdfbox.pdmodel.interactive.form.PDAcroForm;
import org.apache.pdfbox.util.operator.PDFOperator;

import org.apache.fop.events.EventBroadcaster;
import org.apache.fop.fonts.CIDFontType;
import org.apache.fop.fonts.CustomFont;
import org.apache.fop.fonts.EmbeddingMode;
import org.apache.fop.fonts.FontInfo;
import org.apache.fop.fonts.FontType;
import org.apache.fop.fonts.MultiByteFont;
import org.apache.fop.fonts.SingleByteEncoding;
import org.apache.fop.fonts.SingleByteFont;
import org.apache.fop.fonts.Typeface;
import org.apache.fop.fonts.truetype.FontFileReader;
import org.apache.fop.fonts.truetype.OTFSubSetFile;
import org.apache.fop.pdf.PDFArray;
import org.apache.fop.pdf.PDFDictionary;
import org.apache.fop.pdf.PDFDocument;
import org.apache.fop.pdf.PDFName;
import org.apache.fop.pdf.PDFNumber;
import org.apache.fop.pdf.PDFObject;
import org.apache.fop.pdf.PDFPage;
import org.apache.fop.pdf.PDFRoot;
import org.apache.fop.pdf.PDFStream;
import org.apache.fop.pdf.PDFText;
import org.apache.fop.pdf.RefPDFFont;
import org.apache.fop.util.CharUtilities;

/**
 * This class provides an adapter for transferring content from a PDFBox PDDocument to
 * FOP's PDFDocument. It is used to parse PDF using PDFBox and write content using
 * FOP's PDF library.
 */
public class PDFBoxAdapter {

    /** logging instance */
    protected static final Log log = LogFactory.getLog(PDFBoxAdapter.class);

    private static final Set FILTER_FILTER = new java.util.HashSet(
            Arrays.asList(new String[] {"Filter", "DecodeParms"}));
    private static final Pattern SUBSET_PATTERN = Pattern.compile("[A-Z][A-Z][A-Z][A-Z][A-Z][A-Z]\\+.+");

    private final PDFPage targetPage;
    private final PDFDocument pdfDoc;

    private final Map clonedVersion;

    private final Map<COSDictionary, PDSimpleFont> fontMap = new HashMap<COSDictionary, PDSimpleFont>();
    private Map<COSName, String> newXObj = new HashMap<COSName, String>();
    private Collection<String> parentFonts;

    /**
     * Creates a new PDFBoxAdapter.
     * @param targetPage The target FOP PDF page object
     * @param objectCache the object cache for reusing objects shared by multiple pages.
     */
    public PDFBoxAdapter(PDFPage targetPage, Map objectCache) {
        this.targetPage = targetPage;
        this.pdfDoc = this.targetPage.getDocument();
        this.clonedVersion = objectCache;
    }

    private Object cloneForNewDocument(Object base) throws IOException {
        return cloneForNewDocument(base, base);
    }

    private Object cloneForNewDocument(Object base, Object keyBase) throws IOException {
        return cloneForNewDocument(base, keyBase, Collections.EMPTY_LIST);
    }

    private Object cloneForNewDocument(Object base, Object keyBase, Collection exclude) throws IOException {
        if (base == null) {
            return null;
        }
        Object cached = getCachedClone(keyBase);
        if (cached != null) {
            // we are done, it has already been converted.
            return cached;
        } else if (base instanceof List) {
            PDFArray array = new PDFArray();
            cacheClonedObject(keyBase, array);
            List list = (List)base;
            for (Object o : list) {
                array.add(cloneForNewDocument(o, o, exclude));
            }
            return array;
        } else if (base instanceof COSObjectable && !(base instanceof COSBase)) {
            Object o = ((COSObjectable)base).getCOSObject();
            Object retval = cloneForNewDocument(o, o, exclude);
            return cacheClonedObject(keyBase, retval);
        } else if (base instanceof COSObject) {
            return readCOSObject((COSObject) base, exclude);
        } else if (base instanceof COSArray) {
            PDFArray newArray = new PDFArray();
            cacheClonedObject(keyBase, newArray);
            COSArray array = (COSArray)base;
            for (int i = 0; i < array.size(); i++) {
                newArray.add(cloneForNewDocument(array.get(i), array.get(i), exclude));
            }
            return newArray;
        } else if (base instanceof COSStreamArray) {
            COSStreamArray array = (COSStreamArray)base;
            PDFArray newArray = new PDFArray();
            cacheClonedObject(keyBase, newArray);
            for (int i = 0, c = array.getStreamCount(); i < c; i++) {
                newArray.add(cloneForNewDocument(array.get(i)));
            }
            return newArray;
        } else if (base instanceof COSStream) {
            return readCOSStream((COSStream) base, keyBase);
        } else if (base instanceof COSDictionary) {
            return readCOSDictionary((COSDictionary) base, keyBase, exclude);
        } else if (base instanceof COSName) {
            PDFName newName = new PDFName(((COSName)base).getName());
            return cacheClonedObject(keyBase, newName);
        } else if (base instanceof COSInteger) {
            PDFNumber number = new PDFNumber();
            number.setNumber(((COSInteger)base).longValue());
            return cacheClonedObject(keyBase, number);
        } else if (base instanceof COSFloat) {
            PDFNumber number = new PDFNumber();
            number.setNumber(((COSFloat)base).floatValue());
            return cacheClonedObject(keyBase, number);
        } else if (base instanceof COSBoolean) {
            //TODO Do we need a PDFBoolean here?
            Boolean retval = ((COSBoolean)base).getValueAsObject();
            if (keyBase instanceof COSObject) {
                return cacheClonedObject(keyBase, new PDFBoolean(retval));
            } else {
                return cacheClonedObject(keyBase, retval);
            }
        } else if (base instanceof COSString) {
            return readCOSString((COSString) base, keyBase);
        } else if (base instanceof COSNull) {
            return cacheClonedObject(keyBase, null);
        } else {
            throw new UnsupportedOperationException("NYI: " + base.getClass().getName());
        }
    }

    private PDFDictionary readCOSDictionary(COSDictionary dic, Object keyBase, Collection exclude) throws IOException {
        PDFDictionary newDict = new PDFDictionary();
        cacheClonedObject(keyBase, newDict);
        for (Map.Entry<COSName, COSBase> e : dic.entrySet()) {
            if (!exclude.contains(e.getKey())) {
                newDict.put(e.getKey().getName(), cloneForNewDocument(e.getValue(), e.getValue(), exclude));
            }
        }
        return newDict;
    }

    private Object readCOSObject(COSObject object, Collection exclude) throws IOException {
        if (log.isTraceEnabled()) {
            log.trace("Cloning indirect object: "
                    + object.getObjectNumber().longValue()
                    + " " + object.getGenerationNumber().longValue());
        }
        Object obj = cloneForNewDocument(object.getObject(), object, exclude);
        if (obj instanceof PDFObject) {
            PDFObject pdfobj = (PDFObject)obj;
            //pdfDoc.registerObject(pdfobj);
            if (!pdfobj.hasObjectNumber()) {
                throw new IllegalStateException("PDF object was not registered!");
            }
            if (log.isTraceEnabled()) {
                log.trace("Object registered: "
                        + pdfobj.getObjectNumber()
                        + " " + pdfobj.getGeneration()
                        + " for COSObject: "
                        + object.getObjectNumber().longValue()
                        + " " + object.getGenerationNumber().longValue());
            }
        }
        return obj;
    }

    private Object readCOSString(COSString string, Object keyBase) {
        //retval = ((COSString)base).getString(); //this is unsafe for binary content
        byte[] bytes = string.getBytes();
        //Be on the safe side and use the byte array to avoid encoding problems
        //as PDFBox doesn't indicate whether the string is just
        //a string (PDF 1.4, 3.2.3) or a text string (PDF 1.4, 3.8.1).
        if (keyBase instanceof COSObject) {
            return cacheClonedObject(keyBase, new PDFString(bytes));
        } else {
            if (PDFString.isUSASCII(bytes)) {
                return cacheClonedObject(keyBase, string.getString());
            } else {
                return cacheClonedObject(keyBase, bytes);
            }
        }
    }

    private Object readCOSStream(COSStream originalStream, Object keyBase) throws IOException {
        InputStream in;
        Set filter;
        if (pdfDoc.isEncryptionActive()) {
            in = originalStream.getUnfilteredStream();
            filter = FILTER_FILTER;
        } else {
            //transfer encoded data (don't reencode)
            in = originalStream.getFilteredStream();
            filter = Collections.EMPTY_SET;
        }
        PDFStream stream = new PDFStream();
        OutputStream out = stream.getBufferOutputStream();
        IOUtils.copyLarge(in, out);
        transferDict(originalStream, stream, filter);
        return cacheClonedObject(keyBase, stream);
    }

    private Object getCachedClone(Object base) {
        return clonedVersion.get(getBaseKey(base));
    }

    private Object cacheClonedObject(Object base, Object cloned) {
        Object key = getBaseKey(base);
        if (key == null) {
            return cloned;
        }
        PDFObject pdfobj = (PDFObject) cloned;
        if (!pdfobj.hasObjectNumber()) {
            pdfDoc.registerObject(pdfobj);
            if (log.isTraceEnabled()) {
                log.trace(key + ": " + pdfobj.getClass().getName() + " registered as "
                            + pdfobj.getObjectNumber() + " " + pdfobj.getGeneration());
            }
        }
        clonedVersion.put(key, cloned);
        return cloned;
    }

    private Object getBaseKey(Object base) {
        if (base instanceof COSObject) {
            COSObject obj = (COSObject)base;
            return obj.getObjectNumber().intValue() + " " + obj.getGenerationNumber().intValue();
        } else {
            return null;
        }
    }

    private void transferDict(COSDictionary orgDict, PDFStream targetDict,
            Set filter) throws IOException {
        transferDict(orgDict, targetDict, filter, false);
    }

    private void transferDict(COSDictionary orgDict, PDFStream targetDict,
            Set filter, boolean inclusive) throws IOException {
        Set<COSName> keys = orgDict.keySet();
        for (COSName key : keys) {
            if (inclusive && !filter.contains(key.getName())) {
                continue;
            } else if (!inclusive && filter.contains(key.getName())) {
                continue;
            }
            targetDict.put(key.getName(),
                    cloneForNewDocument(orgDict.getItem(key)));
        }
    }

    private String getUniqueFontName(COSDictionary fontData) throws IOException {
        PDSimpleFont font = getFont(fontData);
        String extra = "";
        String name = getName(font.getBaseFont()) + "_" + ((COSName)fontData.getItem(COSName.SUBTYPE)).getName();
        if (font instanceof PDType0Font
                && ((PDType0Font) font).getDescendantFont() instanceof PDCIDFontType0Font
                && ((PDCIDFontType0Font) ((PDType0Font) font).getDescendantFont()).getType1CFont() != null) {
            CFFFont cffFont =
                    ((PDCIDFontType0Font) ((PDType0Font) font).getDescendantFont()).getType1CFont().getCFFFont();
            if (cffFont instanceof CFFFontROS
                    && ((CFFFontROS)cffFont).getFdSelect().getClass().getName()
                    .equals("org.apache.fontbox.cff.CFFParser$Format0FDSelect")) {
                extra += "format0";
            }
            return name + extra;
        }
        if (font instanceof PDType0Font
                && font.getToUnicode() != null
                && ((PDType0Font) font).getDescendantFont() instanceof PDCIDFontType2Font) {
            if (!isSubsetFont(font.getBaseFont())) {
                extra = "f3";
            }
            return name + extra;
        }
        if (font instanceof PDTrueTypeFont && isSubsetFont(font.getBaseFont())) {
            TrueTypeFont tt = ((PDTrueTypeFont) font).getTTFFont();
            for (CMAPEncodingEntry c : tt.getCMAP().getCmaps()) {
                if (c.getGlyphId(1) > 0) {
                    extra = "cid";
                }
            }
            return name + extra;
        }
//        if (!isSubsetFont(font.getBaseFont())) {
//            return font.getBaseFont() + "_" + ((COSName)fontData.getItem(COSName.SUBTYPE)).getName();
//        }
        if (font instanceof PDType1Font) {
            if (((PDType1Font) font).getType1CFont() == null
                    || ((PDType1Font) font).getType1CFont().getCFFFont() == null) {
                if (font.getFontDescriptor() instanceof PDFontDescriptorDictionary) {
                    return name;
                }
                return null;
            }
            CFFEncoding encoding = ((PDType1Font)font).getType1CFont().getCFFFont().getEncoding();
            String eClass = encoding.getClass().getName();
            if (eClass.equals("org.apache.fontbox.cff.CFFParser$Format1Encoding")) {
                extra = "f1enc";
            } else if (eClass.equals("org.apache.fontbox.cff.CFFParser$Format0Encoding")) {
                extra = "f0enc";
            }
            CFFCharset cs = ((PDType1Font)font).getType1CFont().getCFFFont().getCharset();
            if (cs.getEntries().get(0).getSID() < OTFSubSetFile.NUM_STANDARD_STRINGS) {
                extra += "stdcs";
            }
            if (cs.getClass().getName().equals("org.apache.fontbox.cff.CFFParser$Format1Charset")) {
                extra += "f1cs";
            }
            return name + extra;
        }
        return null;
    }

    private static boolean isSubsetFont(String s) {
        return SUBSET_PATTERN.matcher(s).matches();
    }

    private static String getName(String name) {
        if (isSubsetFont(name)) {
            return name.split("\\+")[1].replace(" ", "");
        }
        return name.replace(" ", "");
    }

    interface FOPPDFFont extends RefPDFFont {
        String getFontName();
        void setRef(PDFDictionary d);
        String addFont(COSDictionary fontdata) throws IOException;
        int size();
    }

    public class FOPPDFMultiByteFont extends MultiByteFont implements FOPPDFFont {
        protected PDFDictionary ref;
        private Map<Integer, Integer> newWidth = new TreeMap<Integer, Integer>();
        private Map<String, Integer> charMapGlobal = new LinkedHashMap<String, Integer>();
        private MergeTTFonts mergeTTFonts = new MergeTTFonts();
        private MergeCFFFonts mergeCFFFonts = new MergeCFFFonts();
        private Map<String, GlyphData> glyphs = new HashMap<String, GlyphData>();

        public FOPPDFMultiByteFont(COSDictionary fontData, String name) throws IOException {
            super(null, EmbeddingMode.SUBSET);
            //this stops fop modifying font later on
            setEmbeddingMode(EmbeddingMode.FULL);
            readFontBBox(fontData);
            setFontName(name);
            addFont(fontData);
        }

        public String addFont(COSDictionary fontData) throws IOException {
            PDSimpleFont font = getFont(fontData);
            setProperties(this, font);
            PDSimpleFont mainFont = font;
            TrueTypeFont ttf = null;
            if (font instanceof PDType0Font) {
                PDCIDFont cidFont = (PDCIDFont) ((PDType0Font) font).getDescendantFont();
                setDefaultWidth((int) cidFont.getDefaultWidth());
                mainFont = cidFont;
                if (cidFont instanceof PDCIDFontType0Font) {
                    setCIDType(CIDFontType.CIDTYPE0);
                    setFontType(FontType.CIDTYPE0);
                } else {
                    ttf = ((PDCIDFontType2Font) cidFont).getTTFFont();
                }
            } else {
                ttf = ((PDTrueTypeFont) font).getTTFFont();
                setDefaultWidth(1000);
            }
            GlyphData[] glyphData = new GlyphData[0];
            if (ttf != null) {
                glyphData = ttf.getGlyph().getGlyphs();
            }
            Map<Integer, Integer> oldToNewGIMap = new HashMap<Integer, Integer>();
            if (charMapGlobal.isEmpty()) {
                oldToNewGIMap.put(0, 0); // .notdef glyph
            }
            CMap c = mainFont.getToUnicodeCMap();
            if (c == null) {
                c = font.getToUnicodeCMap();
            }
            Map<Integer, String> mapping = getMapping(mainFont, c, glyphData.length);
            if (glyphData.length > 0 && differentGlyphData(glyphData, mapping)) {
//                return null;
            }
            Map<Integer, String> gidToGlyph = new TreeMap<Integer, String>(mapping);
            if (mainFont instanceof PDTrueTypeFont) {
                CMAPEncodingEntry cmap = ttf.getCMAP().getCmaps()[0];
                gidToGlyph.clear();
                int[] gidToCode = cmap.getGlyphIdToCharacterCode();
                for (int i = 1; i < glyphData.length; i++) {
                    String mappedChar = mapping.get(gidToCode[i]);
                    gidToGlyph.put(i, mappedChar);
                }
            }
            readCharMap(font, gidToGlyph, glyphData, mainFont, oldToNewGIMap);
            FontFileReader ffr = readFontFile(mainFont);
            if (ttf != null) {
                mergeMaxp(ttf, mergeTTFonts.maxp);
                int sizeNoCompGlyphs = oldToNewGIMap.size();
                mergeTTFonts.readFont(ffr, oldToNewGIMap, true);
                if (oldToNewGIMap.size() > sizeNoCompGlyphs) {
                    cidSet.mapChar(256 * 256, (char) 0);
                }
            } else {
                mergeCFFFonts.readType1CFont(new ByteArrayInputStream(ffr.getAllBytes()), getEmbedFontName());
            }
            return getFontName();
        }

        private void readCharMap(PDSimpleFont font, Map<Integer, String> gidToGlyph, GlyphData[] glyphData,
                                 PDSimpleFont mainFont, Map<Integer, Integer> oldToNewGIMap) {
            int widthPos = font.getFirstChar() + 1;
            for (Map.Entry<Integer, String> i : gidToGlyph.entrySet()) {
                String mappedChar = i.getValue();
                int key = i.getKey();
                boolean skipWidth = (mappedChar == null) || mappedChar.length() == 0;
                if (skipWidth) {
                    mappedChar = (char)charMapGlobal.size() + "tmp";
                } else if (mappedChar.length() > 1) {
                    mappedChar = "" + (char)mappedChar.hashCode();
                }
                if (!charMapGlobal.containsKey(mappedChar)) {
                    if (glyphData.length > 0
                            && glyphData[key] == null
                            && !CharUtilities.isAdjustableSpace(mappedChar.charAt(0))) {
                        continue;
                    }
                    boolean addToEnd = charMapGlobal.containsValue(key);
                    if (addToEnd) {
                        addPrivateUseMapping(mappedChar.charAt(0), charMapGlobal.size() + 1);
                        charMapGlobal.put(mappedChar, charMapGlobal.size() + 1);
                    } else {
                        addPrivateUseMapping(mappedChar.charAt(0), key);
                        charMapGlobal.put(mappedChar, key);
                    }
                    int glyph = 0;
                    if (hasChar(mappedChar.charAt(0))) {
                        glyph = (int) mapChar(mappedChar.charAt(0));
                    }
                    oldToNewGIMap.put(key, glyph);
                    if (!skipWidth) {
                        if (!(mainFont instanceof PDTrueTypeFont)) {
                            widthPos = key;
                        }
                        float w = font.getFontWidth(widthPos);
                        if (w >= 0) {
                            if (mainFont instanceof PDCIDFontType0Font) {
                                newWidth.put(key, (int)w);
                            } else {
                                newWidth.put(glyph, (int)w);
                            }
                        }
                    }
                }
                if (!skipWidth) {
                    widthPos++;
                }
            }
        }

        private Map<Integer, String> getMapping(PDSimpleFont font, CMap c, int len) throws IOException {
            Map<Integer, String> mapping = new HashMap<Integer, String>();
            if (font instanceof PDCIDFontType0Font) {
                Collection<CFFFont.Mapping> mappings =
                        ((PDCIDFontType0Font) font).getType1CFont().getCFFFont().getMappings();
                for (CFFFont.Mapping m : mappings) {
                    String character = Encoding.getCharacterForName(m.getName());
                    mapping.put(m.getSID(), character);
                }
            }
            if (c != null) {
                int last = font.getLastChar();
                if (last == -1) {
                    last = len;
                }
                int size = 1;
                if (c.hasTwoByteMappings()) {
                    size = 2;
                }
                for (int i = font.getFirstChar(); i <= last; i++) {
                    String l = c.lookup(i, size);
                    if (l != null) {
                        mapping.put(i, l);
                    }
                }
            }
            return mapping;
        }

        private boolean differentGlyphData(GlyphData[] data, Map<Integer, String> mapping) throws IOException {
            Map<String, Integer> tmpMap = new HashMap<String, Integer>();
            for (Map.Entry<Integer, String> entry : mapping.entrySet()) {
                if (!tmpMap.containsKey(entry.getValue())) {
                    tmpMap.put(entry.getValue(), entry.getKey());
                }
            }
            mapping.clear();
            for (Map.Entry<String, Integer> entry : tmpMap.entrySet()) {
                mapping.put(entry.getValue(), entry.getKey());
            }

            for (Map.Entry<Integer, String> n : mapping.entrySet()) {
                if (data[n.getKey()] != null) {
                    if (glyphs.containsKey(n.getValue()) && !glyphs.get(n.getValue()).equals(data[n.getKey()])) {
                        return true;
                    }
                    glyphs.put(n.getValue(), data[n.getKey()]);
                }
            }
            return false;
        }

        private FontFileReader readFontFile(PDFont font) throws IOException {
            PDFontDescriptorDictionary fd = (PDFontDescriptorDictionary) font.getFontDescriptor();
            PDStream ff = fd.getFontFile3();
            if (ff == null) {
                ff = fd.getFontFile2();
                if (ff == null) {
                    ff = fd.getFontFile();
                }
            }
            InputStream is = ff.createInputStream();
            return new FontFileReader(new ByteArrayInputStream(IOUtils.toByteArray(is)));
        }

        public Map<Integer, Integer> getWidthsMap() {
            return newWidth;
        }

        public PDFDictionary getRef() {
            return ref;
        }

        public void setRef(PDFDictionary d) {
            ref = d;
        }

        public int size() {
            if (getFontType() == FontType.CIDTYPE0) {
                return 1;
            }
            return 0;
        }

        private void readFontBBox(COSBase b) throws IOException {
            if (b instanceof COSDictionary) {
                COSDictionary dict = (COSDictionary)b;
                for (Map.Entry<COSName, COSBase> n : dict.entrySet()) {
                    readFontBBox(n.getValue());
                    if (n.getKey() == COSName.FONT_BBOX) {
                        COSArray w = (COSArray)n.getValue();
                        float[] bboxf = w.toFloatArray();
                        int[] bbox = new int[bboxf.length];
                        for (int i = 0; i < bbox.length; i++) {
                            bbox[i] = (int)bboxf[i];
                        }
                        setFontBBox(bbox);
                    }
                }
            } else if (b instanceof COSObject) {
                COSObject o = (COSObject)b;
                readFontBBox(o.getObject());
            } else if (b instanceof COSArray) {
                COSArray o = (COSArray)b;
                for (int i = 0; i < o.size(); i++) {
                    readFontBBox(o.get(i));
                }
            }
        }

        public boolean isEmbeddable() {
            return true;
        }

        public InputStream getInputStream() throws IOException {
            if (getFontType() == FontType.CIDTYPE0) {
                mergeCFFFonts.writeFont();
                return new ByteArrayInputStream(mergeCFFFonts.getFontSubset());
            }
            mergeTTFonts.writeFont(null);
            return new ByteArrayInputStream(mergeTTFonts.getFontSubset());
        }
    }

    public static class Cmap {
        int platformId;
        int platformEncodingId;
        Map<Integer, Integer> glyphIdToCharacterCode = new TreeMap<Integer, Integer>();
    }

    public class FOPPDFSingleByteFont extends SingleByteFont implements FOPPDFFont {
        private int fontCount;
        private PDSimpleFont font;
        protected PDFDictionary ref;
        private Map<String, Integer> charMapGlobal = new LinkedHashMap<String, Integer>();
        private Map<Integer, Integer> newWidth = new HashMap<Integer, Integer>();
        private Map<String, byte[]> charStringsDict;
        private Cmap newCmap = new Cmap();
        private Map<Integer, String> encodingMap = new TreeMap<Integer, String>();
        private int encodingSkip;
        private MergeTTFonts mergeTTFonts = new MergeTTFonts();
        private MergeCFFFonts mergeCFFFonts = new MergeCFFFonts();
        private MergeType1Fonts mergeType1Fonts = new MergeType1Fonts();
        private String embedName;

        public FOPPDFSingleByteFont(COSDictionary fontData, String name) throws IOException {
            super(null, EmbeddingMode.FULL);
            if (fontData.getItem(COSName.SUBTYPE) == COSName.TRUE_TYPE) {
                setFontType(FontType.TRUETYPE);
            }
            width = new int[0];
            font = getFont(fontData);
            setFirstChar(font.getFirstChar());
            setLastChar(font.getLastChar());
            loadFontFile(font);
            float[] bBoxF = font.getFontBoundingBox().getCOSArray().toFloatArray();
            int[] bBox = new int[bBoxF.length];
            for (int i = 0; i < bBox.length; i++) {
                bBox[i] = (int)bBoxF[i];
            }
            setFontBBox(bBox);

            setFontName(name);
            Object cmap = getCmap(font);
            for (int i = font.getFirstChar(); i <= font.getLastChar(); i++) {
                String mappedChar = getChar(cmap, i);
                if (mappedChar != null && !charMapGlobal.containsKey(mappedChar)) {
                    charMapGlobal.put(mappedChar, i);
                }
            }
            //mark font as used
            notifyMapOperation();
            setProperties(this, font);
            if (font.getWidths() != null) {
                //if width contains 0 we cant rely on codeToNameMap
                boolean usesZero = font.getWidths().contains(0);
                Set<Integer> codeToName = getCodeToName(font.getFontEncoding()).keySet();
                for (int i = getFirstChar();
                     i <= Math.min(getLastChar(), getFirstChar() + font.getWidths().size()); i++) {
                    if (usesZero || codeToName.contains(i)) {
                        int w = font.getWidths().get(i - getFirstChar()).intValue();
                        newWidth.put(i, w);
                    } else {
                        newWidth.put(i, 0);
                    }
                }
            }
            mapping = new FOPPDFEncoding();
            encodingSkip = font.getLastChar() + 1;
            addEncoding(font);
        }

        private Map<Integer, String> getCodeToName(Encoding encoding) {
            Map<Integer, String> codeToName = new HashMap<Integer, String>();
            if (encoding != null) {
                COSBase cos = encoding.getCOSObject();
                if (cos instanceof COSDictionary) {
                    COSDictionary enc = (COSDictionary) cos;
                    COSName baseEncodingName = (COSName) enc.getDictionaryObject(COSName.BASE_ENCODING);
                    if (baseEncodingName != null) {
                        try {
                            Encoding baseEncoding = EncodingManager.INSTANCE.getEncoding(baseEncodingName);
                            codeToName.putAll(baseEncoding.getCodeToNameMap());
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    }
                    COSArray differences = (COSArray)enc.getDictionaryObject(COSName.DIFFERENCES);
                    int currentIndex = -1;
                    for (int i = 0; differences != null &&  i < differences.size(); i++) {
                        COSBase next = differences.getObject(i);
                        if (next instanceof COSNumber) {
                            currentIndex = ((COSNumber)next).intValue();
                        } else if (next instanceof COSName) {
                            COSName name = (COSName)next;
                            codeToName.put(currentIndex++, name.getName());
                        }
                    }
                } else {
                    return encoding.getCodeToNameMap();
                }
            }
            return codeToName;
        }

        private Object getCmap(PDSimpleFont font) throws IOException {
            if (font.getFontEncoding() != null) {
                return font.getFontEncoding();
            }
            return font.getToUnicodeCMap();
        }

        private PDStream readFontFile(PDSimpleFont font) throws IOException {
            PDFontDescriptorDictionary fd = (PDFontDescriptorDictionary) font.getFontDescriptor();
            setFlags(fd.getFlags());
            PDStream ff = fd.getFontFile3();
            if (ff == null) {
                ff = fd.getFontFile2();
                if (ff == null) {
                    ff = fd.getFontFile();
                }
            } else {
                setFontType(FontType.TYPE1C);
            }
            if (ff == null) {
                throw new IOException(font.getBaseFont() + " no font file");
            }
            return ff;
        }

        private void loadFontFile(PDSimpleFont font) throws IOException {
            PDStream ff = readFontFile(font);
            mergeFontFile(ff.createInputStream(), font);
            if (font instanceof PDTrueTypeFont) {
                TrueTypeFont ttfont = ((PDTrueTypeFont) font).getTTFFont();
                CMAPEncodingEntry[] cmapList = ttfont.getCMAP().getCmaps();
                for (CMAPEncodingEntry c : cmapList) {
                    newCmap.platformId = c.getPlatformId();
                    newCmap.platformEncodingId = c.getPlatformEncodingId();
                    for (int i = 0; i < 256 * 256; i++) {
                        if (c.getGlyphId(i) != 0) {
                            newCmap.glyphIdToCharacterCode.put(i, c.getGlyphId(i));
                        }
                    }
                }
                mergeMaxp(ttfont, mergeTTFonts.maxp);
            }
        }

        @Override
        public boolean hasChar(char c) {
            return charMapGlobal.containsKey(String.valueOf(c));
        }

        @Override
        public char mapChar(char c) {
            return mapping.mapChar(c);
        }

        public String getEmbedFontName() {
            if (embedName == null) {
                embedName = getName(font.getBaseFont());
            }
            return embedName;
        }

        public int[] getWidths() {
            width = new int[getLastChar() - getFirstChar() + 1];
            for (int i = getFirstChar(); i <= getLastChar(); i++) {
                if (newWidth.containsKey(i)) {
                    width[i - getFirstChar()] = newWidth.get(i);
                } else {
                    width[i - getFirstChar()] = 0;
                }
            }
            return width.clone();
        }

        public String addFont(COSDictionary fontData) throws IOException {
            PDSimpleFont font = getFont(fontData);
            if (font instanceof PDType1Font && differentGlyphData((PDType1Font) font)) {
                return null;
            }
            mergeWidths(font);
            if (font.getFirstChar() < getFirstChar()) {
                setFirstChar(font.getFirstChar());
            }
            for (int w : newWidth.keySet()) {
                if (w > getLastChar()) {
                    setLastChar(w);
                }
            }
            loadFontFile(font);
            addEncoding(font);
            return getFontName();
        }

        public int size() {
            return fontCount;
        }

        private Map<String, byte[]> getCharStringsDict(PDType1Font font) throws IOException {
            if (getFontType() == FontType.TYPE1) {
                return font.getType1Font().getCharStringsDict();
            }
            return font.getType1CFont().getCFFFont().getCharStringsDict();
        }

        private boolean differentGlyphData(PDType1Font otherFont) throws IOException {
            if (charStringsDict == null) {
                charStringsDict = getCharStringsDict((PDType1Font) font);
            }
            for (Map.Entry<String, byte[]> s : getCharStringsDict(otherFont).entrySet()) {
                if (charStringsDict.containsKey(s.getKey())) {
                    int numberDiff = 0;
                    byte[] b1 = charStringsDict.get(s.getKey());
                    byte[] b2 = s.getValue();
                    int b1Index = b1.length - 1;
                    int b2Index = b2.length - 1;
                    while (b1Index >= 0 && b2Index >= 0) {
                        if (b1[b1Index] != b2[b2Index]) {
                            numberDiff++;
                            if (numberDiff > 2) {
                                break;
                            }
                        }
                        b1Index--;
                        b2Index--;
                    }
                    if (numberDiff > 2) {
//                        log.info(getFontName() + " " + s.getKey() + " not equal " + numberdiff);
                        return true;
                    }
                }
            }
            return false;
        }

        private void mergeWidths(PDSimpleFont font) throws IOException {
            int w = 0;
            int skipGlyphIndex = getLastChar() + 1;
            Object cmap = getCmap(font);
            Set<Integer> codeToName = getCodeToName(font.getFontEncoding()).keySet();
            for (int i = font.getFirstChar(); i <= font.getLastChar(); i++) {
                boolean addedWidth = false;
                int glyphIndexPos = skipGlyphIndex;
                if (font instanceof PDTrueTypeFont) {
                    glyphIndexPos = i;
                }
                int neww = 0;
                if (font.getWidths() != null) {
                    neww = font.getWidths().get(i - font.getFirstChar()).intValue();
                    if (!newWidth.containsKey(i) || newWidth.get(i) == 0) {
                        if (getFontType() == FontType.TYPE1
                                || font instanceof PDTrueTypeFont
                                || codeToName.contains(i)) {
                            newWidth.put(i, neww);
                            glyphIndexPos = i;
                        } else {
                            newWidth.put(i, 0);
                        }
                        addedWidth = true;
                    }
                }
                String mappedChar = getChar(cmap, i);
                if (mappedChar != null && !charMapGlobal.containsKey(mappedChar)) {
                    charMapGlobal.put(mappedChar, glyphIndexPos);
                    if (!addedWidth && w < font.getWidths().size()) {
                        newWidth.put(newWidth.size() + getFirstChar(), neww);
                    }
                    skipGlyphIndex++;
                }
                w++;
            }
        }

        private String getChar(Object cmap, int i) throws IOException {
            if (cmap instanceof CMap) {
                CMap c = (CMap)cmap;
                int size = 1;
                if (c.hasTwoByteMappings()) {
                    size = 2;
                }
                return c.lookup(i, size);
            }
            Encoding enc = (Encoding)cmap;
            if (enc instanceof DictionaryEncoding) {
                return enc.getName(i);
            }
            return enc.getCharacter(i);
        }

        public String getEncodingName() {
            if (font.getFontEncoding() != null) {
                COSBase cosObject = font.getFontEncoding().getCOSObject();
                if (cosObject != null) {
                    if (cosObject instanceof COSDictionary) {
                        COSBase item = ((COSDictionary) cosObject).getItem(COSName.BASE_ENCODING);
                        if (item != null) {
                            return ((COSName)item).getName();
                        }
                    } else if (cosObject instanceof COSName) {
                        return ((COSName) cosObject).getName();
                    } else {
                        throw new RuntimeException(cosObject.toString() + " not supported");
                    }
                }
            }
            return null;
        }

        private void addEncoding(PDSimpleFont fontForEnc) {
            List<String> added = new ArrayList<String>(encodingMap.values());
            Map<Integer, String> codeToName = getCodeToName(fontForEnc.getFontEncoding());
            for (int i = fontForEnc.getFirstChar(); i <= fontForEnc.getLastChar(); i++) {
                if (codeToName.keySet().contains(i)) {
                    String s = codeToName.get(i);
                    if (!added.contains(s)) {
                        if (!encodingMap.containsKey(i)) {
                            encodingMap.put(i, s);
                        } else {
                            encodingMap.put(encodingSkip, s);
                            encodingSkip++;
                        }
                    }
                }
            }
        }

        class FOPPDFEncoding implements SingleByteEncoding {
            private boolean cmap;

            public String getName() {
                return "FOPPDFEncoding";
            }

            public char mapChar(char c) {
                if (charMapGlobal.containsKey(String.valueOf(c))) {
                    return (char)charMapGlobal.get(String.valueOf(c)).intValue();
                }
                return 0;
            }

            public String[] getCharNameMap() {
                Collection<String> v = encodingMap.values();
                return v.toArray(new String[v.size()]);
            }

            public char[] getUnicodeCharMap() {
                if (cmap) {
                    if (font.getToUnicode() == null) {
                        return new char[0];
                    }
                    List<String> cmapStrings = new ArrayList<String>();
                    Map<Integer, String> cm = new HashMap<Integer, String>();
                    for (Map.Entry<String, Integer> o : charMapGlobal.entrySet()) {
                        cm.put(o.getValue(), o.getKey());
                    }
                    for (int i = 0; i < getLastChar() + 1; i++) {
                        if (cm.containsKey(i)) {
                            cmapStrings.add(cm.get(i));
                        } else {
                            cmapStrings.add(" ");
                        }
                    }
                    return fromStringToCharArray(cmapStrings);
                }
                cmap = true;
                return toCharArray(encodingMap.keySet());
            }

            private char[] fromStringToCharArray(Collection<String> list) {
                char[] ret = new char[list.size()];
                int i = 0;
                for (String e : list) {
                    if (e.length() > 0) {
                        ret[i++] = e.charAt(0);
                    }
                }
                return ret;
            }

            private char[] toCharArray(Collection<Integer> list) {
                char[] ret = new char[list.size()];
                int i = 0;
                for (int e : list) {
                    ret[i++] = (char)e;
                }
                return ret;
            }
        }

        public PDFDictionary getRef() {
            return ref;
        }

        public void setRef(PDFDictionary d) {
            ref = d;
        }

        public boolean isEmbeddable() {
            return true;
        }

        public boolean isSymbolicFont() {
            return false;
        }

        private void mergeFontFile(InputStream ff, PDSimpleFont pdSimpleFont) throws IOException {
            if (getFontType() == FontType.TRUETYPE) {
                Map<Integer, Integer> chars = new HashMap<Integer, Integer>();
                chars.put(0, 0);
                mergeTTFonts.readFont(new FontFileReader(ff), chars, false);
            } else if (getFontType() == FontType.TYPE1) {
                mergeType1Fonts.readFont(ff, (PDType1Font) pdSimpleFont);
            } else {
                mergeCFFFonts.readType1CFont(ff, getEmbedFontName());
            }
            fontCount++;
        }

        public InputStream getInputStream() throws IOException {
            if (getFontType() == FontType.TYPE1C) {
                mergeCFFFonts.writeFont();
                return new ByteArrayInputStream(mergeCFFFonts.getFontSubset());
            }
            if (getFontType() == FontType.TRUETYPE) {
                mergeTTFonts.writeFont(newCmap);
                return new ByteArrayInputStream(mergeTTFonts.getFontSubset());
            }
            if (getFontType() == FontType.TYPE1) {
                return new ByteArrayInputStream(mergeType1Fonts.writeFont());
            }
            return null;
        }
    }

    private void setProperties(CustomFont cFont, PDSimpleFont font) {
        if (font.getFontDescriptor() != null) {
            cFont.setCapHeight((int) font.getFontDescriptor().getCapHeight());
            cFont.setAscender((int)font.getFontDescriptor().getAscent());
            cFont.setDescender((int)font.getFontDescriptor().getDescent());
            cFont.setXHeight((int)font.getFontDescriptor().getXHeight());
            cFont.setStemV((int)font.getFontDescriptor().getStemV());
        }
    }

    private void mergeMaxp(TrueTypeFont ttf, MaximumProfileTable outMaxp) {
        MaximumProfileTable mp = ttf.getMaximumProfile();
        outMaxp.setVersion(mp.getVersion());
        outMaxp.setNumGlyphs(outMaxp.getNumGlyphs() + mp.getNumGlyphs());
        outMaxp.setMaxPoints(outMaxp.getMaxPoints() + mp.getMaxPoints());
        outMaxp.setMaxContours(outMaxp.getMaxContours() + mp.getMaxContours());
        outMaxp.setMaxCompositePoints(outMaxp.getMaxCompositePoints() + mp.getMaxCompositePoints());
        outMaxp.setMaxCompositeContours(outMaxp.getMaxCompositeContours() + mp.getMaxCompositeContours());
        outMaxp.setMaxZones(outMaxp.getMaxZones() + mp.getMaxZones());
        outMaxp.setMaxTwilightPoints(outMaxp.getMaxTwilightPoints() + mp.getMaxTwilightPoints());
        outMaxp.setMaxStorage(outMaxp.getMaxStorage() + mp.getMaxStorage());
        outMaxp.setMaxFunctionDefs(outMaxp.getMaxFunctionDefs() + mp.getMaxFunctionDefs());
        outMaxp.setMaxInstructionDefs(outMaxp.getMaxInstructionDefs() + mp.getMaxInstructionDefs());
        outMaxp.setMaxStackElements(outMaxp.getMaxStackElements() + mp.getMaxStackElements());
        outMaxp.setMaxSizeOfInstructions(outMaxp.getMaxSizeOfInstructions() + mp.getMaxSizeOfInstructions());
        outMaxp.setMaxComponentElements(outMaxp.getMaxComponentElements() + mp.getMaxComponentElements());
        outMaxp.setMaxComponentDepth(outMaxp.getMaxComponentDepth() + mp.getMaxComponentDepth());
    }

    public class PDFWriter {
        protected StringBuilder s = new StringBuilder();
        private String key;
        private List<COSName> resourceNames;

        public PDFWriter(String key, List<COSName> resourceNames) {
            this.key = key;
            this.resourceNames = resourceNames;
        }

        public String writeText(PDStream pdStream) throws IOException {
            Iterator<Object> it = new PDFStreamParser(pdStream).getTokenIterator();
            List<COSBase> arguments = new ArrayList<COSBase>();
            while (it.hasNext()) {
                Object o = it.next();
                if (o instanceof PDFOperator) {
                    PDFOperator op = (PDFOperator)o;
                    readPDFArguments(op, arguments);
                    s.append(op.getOperation() + "\n");
                    arguments.clear();
                    if (op.getImageParameters() != null) {
                        for (Map.Entry<COSName, COSBase> cn : op.getImageParameters().entrySet()) {
                            arguments.add(cn.getKey());
                            arguments.add(cn.getValue());
                        }
                        readPDFArguments(op, arguments);
                        s.append("ID " + new String(op.getImageData(), "ISO-8859-1"));
                        arguments.clear();
                        s.append("EI\n");
                    }
                } else {
                    arguments.add((COSBase)o);
                }
            }
            return s.toString();
        }

        protected void readPDFArguments(PDFOperator op, Collection<COSBase> arguments) throws IOException {
            for (COSBase c : arguments) {
                processArg(op, c);
            }
        }

        protected void processArg(PDFOperator op, COSBase c) throws IOException {
            if (c instanceof COSInteger) {
                s.append(((COSInteger) c).intValue());
                s.append(" ");
            } else if (c instanceof COSFloat) {
                float f = ((COSFloat) c).floatValue();
                s.append(new DecimalFormat("#.####").format(f));
                s.append(" ");
            } else if (c instanceof COSName) {
                COSName cn = (COSName)c;
                s.append("/" + cn.getName());
                addKey(cn);
                s.append(" ");
            } else if (c instanceof COSString) {
                s.append("<" + ((COSString) c).getHexString() + ">");
            } else if (c instanceof COSArray) {
                s.append("[");
                readPDFArguments(op, (Collection<COSBase>) ((COSArray) c).toList());
                s.append("] ");
            } else if (c instanceof COSDictionary) {
                Collection<COSBase> dictArgs = new ArrayList<COSBase>();
                for (Map.Entry<COSName, COSBase> cn : ((COSDictionary)c).entrySet()) {
                    dictArgs.add(cn.getKey());
                    dictArgs.add(cn.getValue());
                }
                s.append("<<");
                readPDFArguments(op, dictArgs);
                s.append(">>");
            } else if (c instanceof COSBoolean) {
                s.append(((COSBoolean) c).getValue());
            } else {
                throw new IOException(c + " not supported");
            }
        }

        protected void addKey(COSName cn) {
            if (resourceNames.contains(cn)) {
                s.append(key);
            }
        }
    }

    public class MergeFontsPDFWriter extends PDFWriter {
        private COSDictionary fonts;
        private FontInfo fontInfo;
        private Typeface font;
        private PDSimpleFont oldFont = null;
        private Map<COSName, String> fontsToRemove = new HashMap<COSName, String>();

        public MergeFontsPDFWriter(COSDictionary fonts, FontInfo fontInfo, String key, List<COSName> resourceNames) {
            super(key, resourceNames);
            this.fonts = fonts;
            this.fontInfo = fontInfo;
        }

        public String writeText(PDStream pdStream) throws IOException {
            String txt = super.writeText(pdStream);
            if (fontsToRemove.isEmpty()) {
                return null;
            }
            for (COSName cn : fontsToRemove.keySet()) {
                fonts.removeItem(cn);
            }
            return txt;
        }

        protected void readPDFArguments(PDFOperator op, Collection<COSBase> arguments) throws IOException {
            for (COSBase c : arguments) {
                if (c instanceof COSName) {
                    COSName cn = (COSName)c;
                    COSDictionary fontData = (COSDictionary)fonts.getDictionaryObject(cn.getName());
                    String internalName = fontsToRemove.get(cn);
                    if (internalName == null && fontData != null) {
                        internalName = getNewFont(fontData, fontInfo, fontsToRemove.values());
                    }
                    if (fontData == null || internalName == null) {
                        s.append("/" + cn.getName());
                        addKey(cn);
                        if (op.getOperation().equals("Tf")) {
                            font = null;
                            oldFont = null;
                        }
                    } else {
                        s.append("/" + internalName);
                        fontsToRemove.put(cn, internalName);
                        font = fontInfo.getUsedFonts().get(internalName);
                        oldFont = getFont(fontData);
                    }
                    s.append(" ");
                } else if (c instanceof COSString && font != null && ((FOPPDFFont)font).size() != 1) {
                    List<String> word = readCOSString((COSString)c, oldFont);
                    if (word == null) {
                        s.append(PDFText.escapeString(((COSString) c).getString()));
                    } else {
                        String x = getMappedWord(word, font, ((COSString) c).getBytes());
                        if (x == null) {
                            s.append(PDFText.escapeString(((COSString) c).getString()));
                        } else {
                            s.append(x);
                        }
                    }
                } else {
                    processArg(op, c);
                }
            }
        }

        private String getMappedWord(List<String> word, Typeface font, byte[] bytes) throws IOException {
            StringBuffer newOct = new StringBuffer();
            StringBuilder newHex = new StringBuilder();
            int i = 0;
            for (String str : word) {
                Integer mapped = getMapping(bytes[i]);
                if (mapped == null) {
                    char c = str.charAt(0);
                    if (str.length() > 1) {
                        c = (char) str.hashCode();
                    }
                    if (font.hasChar(c)) {
                        mapped = (int)font.mapChar(c);
                    } else {
                        return null;
                    }
                }
                newHex.append(String.format("%1$04x", mapped & 0xFFFF).toUpperCase(Locale.getDefault()));
                PDFText.escapeStringChar((char)mapped.intValue(), newOct);
                i++;
            }
            if (font instanceof SingleByteFont) {
                return "(" + newOct.toString() + ")";
            }
            return "<" + newHex.toString() + ">";
        }

        private Integer getMapping(byte i) throws IOException {
            if (oldFont.getFontEncoding() != null && font instanceof FOPPDFSingleByteFont) {
                String name = oldFont.getFontEncoding().getName(i);
                if (name != null && ((FOPPDFSingleByteFont)font).charMapGlobal.containsKey(name)) {
                    return ((FOPPDFSingleByteFont)font).charMapGlobal.get(name);
                }
            }
            return null;
        }

        private List<String> readCOSString(COSString s, PDSimpleFont oldFont) throws IOException {
            List<String> word = new ArrayList<String>();
            byte[] string = s.getBytes();
            int codeLength;
//            String t1Str = new String(string, "UTF-8");
            for (int i = 0; i < string.length; i += codeLength) {
                codeLength = 1;
                String c = oldFont.encode(string, i, codeLength);
//                if (oldFont instanceof PDType1Font && i < t1Str.length()) {
//                    c = ((PDType1Font)oldFont).encodetype1(string, i, codeLength);
//                }
                if (c == null && i + 1 < string.length) {
                    codeLength++;
                    c = oldFont.encode(string, i, codeLength);
                }
                if (c == null) {
                    return null;
                }
                word.add(c);
            }
            return word;
        }
    }

    private PDSimpleFont getFont(COSDictionary fontData) throws IOException {
        if (!fontMap.containsKey(fontData)) {
            if (fontMap.size() > 10) {
                fontMap.clear();
            }
            fontMap.put(fontData, (PDSimpleFont)PDFontFactory.createFont(fontData));
        }
        return fontMap.get(fontData);
    }

    /**
     * Creates a stream (from FOP's PDF library) from a PDF page parsed with PDFBox.
     * @param sourceDoc the source PDF the given page to be copied belongs to
     * @param page the page to transform into a stream
     * @param key value to use as key for the stream
     * @param eventBroadcaster events
     * @param atdoc adjustment for stream
     * @param fontinfo fonts
     * @param pos rectangle
     * @return the stream
     * @throws IOException if an I/O error occurs
     */
    public String createStreamFromPDFBoxPage(PDDocument sourceDoc, PDPage page, String key,
            EventBroadcaster eventBroadcaster, AffineTransform atdoc, FontInfo fontinfo, Rectangle pos)
        throws IOException {
        handleAcroForm(sourceDoc, page, eventBroadcaster, atdoc);
        PDResources sourcePageResources = page.findResources();
        PDFDictionary pageResources = null;
        PDStream pdStream = page.getContents();
        if (pdStream == null) {
            return "";
        }
        COSDictionary fonts = (COSDictionary)sourcePageResources.getCOSDictionary().getDictionaryObject(COSName.FONT);
        COSDictionary fontsBackup = null;
        String uniqueName = Integer.toString(key.hashCode());
        String newStream = null;
        if (fonts != null && pdfDoc.isMergeFontsEnabled()) {
            fontsBackup = new COSDictionary(fonts);
            MergeFontsPDFWriter m = new MergeFontsPDFWriter(fonts, fontinfo, uniqueName,
                    getResourceNames(sourcePageResources.getCOSDictionary()));
            newStream = m.writeText(pdStream);
            parentFonts = m.fontsToRemove.values();
//            if (newStream != null) {
//                for (Object f : fonts.keySet().toArray()) {
//                    COSDictionary fontdata = (COSDictionary)fonts.getDictionaryObject((COSName)f);
//                    if (getUniqueFontName(fontdata) != null) {
//                        fonts.removeItem((COSName)f);
//                    }
//                }
//            }
        }
        if (newStream == null) {
            newStream = new PDFWriter(uniqueName,
                    getResourceNames(sourcePageResources.getCOSDictionary())).writeText(pdStream);
        }
        pdStream = new PDStream(sourceDoc, new ByteArrayInputStream(newStream.getBytes("ISO-8859-1")));
        mergeXObj(sourcePageResources.getCOSDictionary(), fontinfo, uniqueName);
        pageResources = (PDFDictionary)cloneForNewDocument(sourcePageResources.getCOSDictionary());

        PDFDictionary fontDict = (PDFDictionary)pageResources.get("Font");
        if (fontDict != null && pdfDoc.isMergeFontsEnabled()) {
            for (Map.Entry<String, Typeface> fontEntry : fontinfo.getUsedFonts().entrySet()) {
                Typeface font = fontEntry.getValue();
                if (font instanceof FOPPDFFont) {
                    FOPPDFFont pdfFont = (FOPPDFFont)font;
                    if (pdfFont.getRef() == null) {
                        pdfFont.setRef(new PDFDictionary());
                        pdfDoc.assignObjectNumber(pdfFont.getRef());
                    }
                    fontDict.put(fontEntry.getKey(), pdfFont.getRef());
                }
            }
        }
        updateXObj(sourcePageResources.getCOSDictionary(), pageResources);
        if (fontsBackup != null) {
            sourcePageResources.getCOSDictionary().setItem(COSName.FONT, fontsBackup);
        }

        COSStream originalPageContents = (COSStream)pdStream.getCOSObject();

        bindOptionalContent(sourceDoc);

        PDFStream pageStream;
        Set filter;
        if (originalPageContents instanceof COSStreamArray) {
            COSStreamArray array = (COSStreamArray)originalPageContents;
            pageStream = new PDFStream();
            InputStream in = array.getUnfilteredStream();
            OutputStream out = pageStream.getBufferOutputStream();
            IOUtils.copyLarge(in, out);
            filter = FILTER_FILTER;
        } else {
            pageStream = (PDFStream)cloneForNewDocument(originalPageContents);
            filter = Collections.EMPTY_SET;
        }
        if (pageStream == null) {
            pageStream = new PDFStream();
        }
        if (originalPageContents != null) {
            transferDict(originalPageContents, pageStream, filter);
        }

        transferPageDict(fonts, uniqueName, sourcePageResources);

        PDRectangle mediaBox = page.findMediaBox();
        PDRectangle cropBox = page.findCropBox();
        PDRectangle viewBox = cropBox != null ? cropBox : mediaBox;

        //Handle the /Rotation entry on the page dict
        int rotation = PDFUtil.getNormalizedRotation(page);

        //Transform to FOP's user space
        float w = (float)pos.getWidth() / 1000f;
        float h = (float)pos.getHeight() / 1000f;
        if (rotation == 90 || rotation == 270) {
            float tmp = w;
            w = h;
            h = tmp;
        }
        atdoc.setTransform(AffineTransform.getScaleInstance(w / viewBox.getWidth(), h / viewBox.getHeight()));
        atdoc.translate(0, viewBox.getHeight());
        atdoc.rotate(-Math.PI);
        atdoc.scale(-1, 1);
        atdoc.translate(-viewBox.getLowerLeftX(), -viewBox.getLowerLeftY());

        switch (rotation) {
            case 90:
                atdoc.scale(viewBox.getWidth() / viewBox.getHeight(), viewBox.getHeight() / viewBox.getWidth());
                atdoc.translate(0, viewBox.getWidth());
                atdoc.rotate(-Math.PI / 2.0);
                atdoc.scale(viewBox.getWidth() / viewBox.getHeight(), viewBox.getHeight() / viewBox.getWidth());
                break;
            case 180:
                atdoc.translate(viewBox.getWidth(), viewBox.getHeight());
                atdoc.rotate(-Math.PI);
                break;
            case 270:
                atdoc.translate(0, viewBox.getHeight());
                atdoc.rotate(Math.toRadians(270 + 180));
                atdoc.translate(-viewBox.getWidth(), -viewBox.getHeight());
                break;
            default:
                //no additional transformations necessary
                break;
        }
        StringBuffer boxStr = new StringBuffer();
        boxStr.append(0).append(' ').append(0).append(' ');
        boxStr.append(PDFNumber.doubleOut(mediaBox.getWidth())).append(' ');
        boxStr.append(PDFNumber.doubleOut(mediaBox.getHeight())).append(" re W n\n");
        return boxStr.toString() + pdStream.getInputStreamAsString();
    }

    private void mergeXObj(COSDictionary sourcePageResources, FontInfo fontinfo, String uniqueName) throws IOException {
        COSDictionary xobj = (COSDictionary) sourcePageResources.getDictionaryObject(COSName.XOBJECT);
        if (xobj != null && pdfDoc.isMergeFontsEnabled()) {
            for (Map.Entry<COSName, COSBase> i : xobj.entrySet()) {
                COSObject v = (COSObject) i.getValue();
                COSStream stream = (COSStream) v.getObject();
                COSDictionary res = (COSDictionary) stream.getDictionaryObject(COSName.RESOURCES);
                if (res != null) {
                    COSDictionary src = (COSDictionary) res.getDictionaryObject(COSName.FONT);
                    if (src != null) {
                        COSDictionary target = (COSDictionary) sourcePageResources.getDictionaryObject(COSName.FONT);
                        if (target == null) {
                            sourcePageResources.setItem(COSName.FONT, src);
                        } else {
                            for (Map.Entry<COSName, COSBase> entry : src.entrySet()) {
                                if (!target.keySet().contains(entry.getKey())) {
                                    target.setItem(entry.getKey(), entry.getValue());
                                }
                            }
                        }
                        PDFWriter writer = new MergeFontsPDFWriter(src, fontinfo, uniqueName,
                                getResourceNames(sourcePageResources));
                        String c = writer.writeText(PDStream.createFromCOS(stream));
                        if (c != null) {
                            stream.removeItem(COSName.FILTER);
                            newXObj.put(i.getKey(), c);
                            for (Object e : src.keySet().toArray()) {
                                COSName name = (COSName) e;
                                src.setItem(name.getName() + uniqueName, src.getItem(name));
                                src.removeItem(name);
                            }
                        }
                    }
                }
            }
        }
    }

    private void updateXObj(COSDictionary sourcePageResources, PDFDictionary pageResources) throws IOException {
        COSDictionary xobj = (COSDictionary) sourcePageResources.getDictionaryObject(COSName.XOBJECT);
        if (xobj != null && pdfDoc.isMergeFontsEnabled()) {
            PDFDictionary target = (PDFDictionary) pageResources.get("XObject");
            for (COSName entry : xobj.keySet()) {
                if (newXObj.containsKey(entry)) {
                    PDFStream s = (PDFStream) target.get(entry.getName());
                    s.setData(newXObj.get(entry).getBytes("UTF-8"));
                    PDFDictionary xobjr = (PDFDictionary) s.get("Resources");
                    xobjr.put("Font", pageResources.get("Font"));
                }
            }
        }
    }

    private List<COSName> getResourceNames(COSDictionary sourcePageResources) {
        List<COSName> resourceNames = new ArrayList<COSName>();
        for (COSBase e : sourcePageResources.getValues()) {
            if (e instanceof COSObject) {
                e = ((COSObject) e).getObject();
            }
            if (e instanceof COSDictionary) {
                COSDictionary d = (COSDictionary) e;
                resourceNames.addAll(d.keySet());
            }
        }
        return resourceNames;
    }

    private void transferPageDict(COSDictionary fonts, String uniqueName, PDResources sourcePageResources)
        throws IOException {
        if (fonts != null) {
            for (Map.Entry<COSName, COSBase> f : fonts.entrySet()) {
                String name = f.getKey().getName() + uniqueName;
                targetPage.getPDFResources().addFont(name, (PDFDictionary)cloneForNewDocument(f.getValue()));
            }
        }
        for (Map.Entry<COSName, COSBase> e : sourcePageResources.getCOSDictionary().entrySet()) {
            transferDict(e, uniqueName);
        }
    }

    private void transferDict(Map.Entry<COSName, COSBase> dict, String uniqueName) throws IOException {
        COSBase src;
        if (dict.getValue() instanceof COSObject) {
            src = ((COSObject) dict.getValue()).getObject();
        } else {
            src = dict.getValue();
        }
        if (dict.getKey() != COSName.FONT && src instanceof COSDictionary) {
            String name = dict.getKey().getName();
            PDFDictionary newDict = (PDFDictionary) targetPage.getPDFResources().get(name);
            if (newDict == null) {
                newDict = new PDFDictionary(targetPage.getPDFResources());
            }
            COSDictionary srcDict = (COSDictionary) src;
            for (Map.Entry<COSName, COSBase> v : srcDict.entrySet()) {
                newDict.put(v.getKey().getName() + uniqueName, cloneForNewDocument(v.getValue()));
            }
            targetPage.getPDFResources().put(name, newDict);
        }
    }

    private String getNewFont(COSDictionary fontData, FontInfo fontinfo, Collection<String> usedFonts)
        throws IOException {
        String base = getUniqueFontName(fontData);
        if (base == null || usedFonts.contains(base) || (parentFonts != null && parentFonts.contains(base))) {
            return null;
        }
        try {
            for (Typeface t : fontinfo.getUsedFonts().values()) {
                if (t instanceof FOPPDFFont && base.equals(t.getFontName())) {
                    return ((FOPPDFFont)t).addFont(fontData);
                }
            }
            if (base.endsWith("cid") || fontData.getItem(COSName.SUBTYPE) != COSName.TYPE1
                    && fontData.getItem(COSName.SUBTYPE) != COSName.TRUE_TYPE) {
                fontinfo.addMetrics(base, new FOPPDFMultiByteFont(fontData, base));
            } else {
                fontinfo.addMetrics(base, new FOPPDFSingleByteFont(fontData, base));
            }
        } catch (IOException e) {
            log.warn(e.getMessage());
            return null;
        }
        fontinfo.useFont(base);
        return base;
    }

    private void bindOptionalContent(PDDocument sourceDoc) throws IOException {
        /*
         * PDOptionalContentProperties ocProperties =
         * sourceDoc.getDocumentCatalog().getOCProperties(); PDFDictionary ocDictionary =
         * (PDFDictionary) cloneForNewDocument(ocProperties); if (ocDictionary != null) {
         * this.pdfDoc.getRoot().put(COSName.OCPROPERTIES.getName(), ocDictionary); }
         */
    }

    private void handleAcroForm(PDDocument sourceDoc, PDPage page,
            EventBroadcaster eventBroadcaster, AffineTransform at) throws IOException {
        PDDocumentCatalog srcCatalog = sourceDoc.getDocumentCatalog();
        PDAcroForm srcAcroForm = srcCatalog.getAcroForm();
        List pageAnnotations = page.getAnnotations();
        if (srcAcroForm == null && pageAnnotations.isEmpty()) {
            return;
        }

        PDRectangle mediaBox = page.findMediaBox();
        PDRectangle cropBox = page.findCropBox();
        PDRectangle viewBox = cropBox != null ? cropBox : mediaBox;

        for (Object obj : pageAnnotations) {
            PDAnnotation annot = (PDAnnotation)obj;
            PDRectangle rect = annot.getRectangle();
            rect.move((float)(at.getTranslateX() - viewBox.getLowerLeftX()),
                    (float)at.getTranslateY() - viewBox.getLowerLeftY());
        }

        //Pseudo-cache the target page in place of the original source page.
        //This essentially replaces the original page reference with the target page.
        COSObject cosPage = null;
        if (page.getCOSObject() instanceof COSObject) {
            cosPage = (COSObject)page.getCOSObject();
        } else {
            PDPageNode pageNode = page.getParent();

            COSArray kids = (COSArray)pageNode.getDictionary().getDictionaryObject(COSName.KIDS);
            Iterator iter = kids.iterator();
            while (iter.hasNext()) {
                //Hopefully safe to cast, as kids need to be indirect objects
                COSObject kid = (COSObject)iter.next();
                if (kid.getObject() == page.getCOSObject()) {
                    cosPage = kid;
                    break;
                }
            }
            if (cosPage == null) {
                throw new IOException("Illegal PDF. Page not part of parent page node.");
            }
        }
        cacheClonedObject(cosPage, this.targetPage);

        COSArray annots = (COSArray) page.getCOSDictionary().getDictionaryObject(COSName.ANNOTS);
        Set<COSObject> fields = Collections.emptySet();
        if (annots != null) {
            fields = new HashSet();
            Iterator iter = annots.iterator();
            while (iter.hasNext()) {
                COSObject annot = (COSObject) iter.next();
                COSObject fieldObject = annot;
                COSDictionary field = (COSDictionary) fieldObject.getObject();
                if ("Widget".equals(field.getNameAsString(COSName.SUBTYPE))) {
                    COSObject parent;
                    while ((parent = (COSObject) field.getItem(COSName.PARENT)) != null) {
                        fieldObject = parent;
                        field = (COSDictionary) fieldObject.getObject();
                    }
                    fields.add(fieldObject);
                    Collection<COSName> exclude = new ArrayList<COSName>();
                    exclude.add(COSName.P);
                    if (((COSDictionary)annot.getObject()).getItem(COSName.getPDFName("StructParent")) != null) {
                        exclude.add(COSName.PARENT);
                    }
                    PDFObject clonedAnnot = (PDFObject) cloneForNewDocument(annot, annot, exclude);
                    targetPage.addAnnotation(clonedAnnot);
                }
            }
        }

        boolean formAlreadyCopied = getCachedClone(srcAcroForm) != null;
        PDFRoot catalog = this.pdfDoc.getRoot();
        PDFDictionary destAcroForm = (PDFDictionary)catalog.get(COSName.ACRO_FORM.getName());
        if (formAlreadyCopied) {
            //skip, already copied
        } else if (destAcroForm == null) {
            if (srcAcroForm != null) {
                //With this, only the first PDF's AcroForm is copied over. If later AcroForms have
                //different properties besides the actual fields, these get lost. Only fields
                //get merged.
                Collection exclude = Collections.singletonList(COSName.FIELDS);
                destAcroForm = (PDFDictionary)cloneForNewDocument(srcAcroForm, srcAcroForm, exclude);
            } else {
                //Work-around for incorrectly split PDFs which lack an AcroForm but have widgets
                //on pages. This doesn't handle the case where field dicts have "C" entries
                //(for the "CO" entry), so this may produce problems, but we have almost no chance
                //to guess the calculation order.
                destAcroForm = new PDFDictionary(pdfDoc.getRoot());
            }
            pdfDoc.registerObject(destAcroForm);
            catalog.put(COSName.ACRO_FORM.getName(), destAcroForm);
        }
        PDFArray clonedFields = (PDFArray) destAcroForm.get(COSName.FIELDS.getName());
        if (clonedFields == null) {
            clonedFields = new PDFArray();
            destAcroForm.put(COSName.FIELDS.getName(), clonedFields);
        }
        for (COSObject field : fields) {
            PDFDictionary clone = (PDFDictionary) cloneForNewDocument(field, field, Arrays.asList(COSName.KIDS));
            clonedFields.add(clone);
        }
    }
}
