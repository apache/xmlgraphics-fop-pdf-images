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
package org.apache.fop.render.pdf.pdfbox;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

import org.apache.commons.io.IOUtils;
import org.apache.fontbox.cff.CFFCharset;
import org.apache.fontbox.cff.CFFFont;
import org.apache.fontbox.cff.CFFStandardString;
import org.apache.fontbox.cmap.CMap;

import org.apache.fontbox.ttf.CmapSubtable;
import org.apache.fontbox.ttf.GlyphData;
import org.apache.fontbox.ttf.TrueTypeFont;

import org.apache.pdfbox.cos.COSArray;
import org.apache.pdfbox.cos.COSBase;
import org.apache.pdfbox.cos.COSDictionary;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.cos.COSObject;
import org.apache.pdfbox.pdmodel.common.PDStream;
import org.apache.pdfbox.pdmodel.font.PDCIDFont;
import org.apache.pdfbox.pdmodel.font.PDCIDFontType0;
import org.apache.pdfbox.pdmodel.font.PDCIDFontType2;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDFontDescriptor;
import org.apache.pdfbox.pdmodel.font.PDTrueTypeFont;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.pdfbox.pdmodel.font.encoding.GlyphList;

import org.apache.fop.fonts.CIDFontType;
import org.apache.fop.fonts.CustomFont;
import org.apache.fop.fonts.EmbeddingMode;
import org.apache.fop.fonts.FontType;
import org.apache.fop.fonts.MultiByteFont;
import org.apache.fop.pdf.PDFDictionary;
import org.apache.fop.util.CharUtilities;

public class FOPPDFMultiByteFont extends MultiByteFont implements FOPPDFFont {
    protected PDFDictionary ref;
    private Map<Integer, Integer> newWidth = new TreeMap<Integer, Integer>();
    private Map<String, Integer> charMapGlobal = new LinkedHashMap<String, Integer>();
    private MergeFonts mergeFonts;
    //private Map<String, GlyphData> glyphs = new HashMap<String, GlyphData>();
    private final Map<COSDictionary, FontContainer> fontMap = new HashMap<COSDictionary, FontContainer>();

    public FOPPDFMultiByteFont(COSDictionary fontData, String name) throws IOException {
        super(null, EmbeddingMode.SUBSET);
        //this stops fop modifying font later on
        setEmbeddingMode(EmbeddingMode.FULL);
        readFontBBox(fontData);
        setFontName(name);
        addFont(fontData);
        notifyMapOperation();
    }

    public String addFont(COSDictionary fontData) throws IOException {
        FontContainer font = getFont(fontData);
        setProperties(this, font.font);
        PDCIDFont mainFont = null;
        TrueTypeFont ttf = null;
        if (font.font instanceof PDType0Font) {
            PDCIDFont cidFont = ((PDType0Font) font.font).getDescendantFont();
            int dw = cidFont.getCOSObject().getInt(COSName.DW);
            setDefaultWidth(dw);
            mainFont = cidFont;
            if (cidFont instanceof PDCIDFontType0) {
                setCIDType(CIDFontType.CIDTYPE0);
                setFontType(FontType.CIDTYPE0);
            } else {
                ttf = ((PDCIDFontType2) cidFont).getTrueTypeFont();
            }
        } else {
            ttf = ((PDTrueTypeFont) font.font).getTrueTypeFont();
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
        CMap c = font.getToUnicodeCMap();
        Map<Integer, String> mapping = getMapping(font, c, glyphData.length);
        if (glyphData.length > 0) {
            differentGlyphData(glyphData, mapping);
        }
        Map<Integer, String> gidToGlyph = new TreeMap<Integer, String>(mapping);
        if (font.font instanceof PDTrueTypeFont) {
            CmapSubtable cmap = ttf.getCmap().getCmaps()[0];
            gidToGlyph.clear();
            for (int i = 1; i < glyphData.length; i++) {
                String mappedChar = mapping.get(cmap.getCharacterCode(i));
                gidToGlyph.put(i, mappedChar);
            }
        }
        readCharMap(font, gidToGlyph, glyphData, mainFont, oldToNewGIMap);
        InputStream ffr = readFontFile(font.font);
        if (mergeFonts == null) {
            if (ttf != null) {
                mergeFonts = new MergeTTFonts(null);
            } else {
                mergeFonts = new MergeCFFFonts();
            }
        }
        if (mergeFonts instanceof MergeTTFonts) {
            mergeMaxp(ttf, ((MergeTTFonts)mergeFonts).maxp);
            int sizeNoCompGlyphs = oldToNewGIMap.size();
            mergeFonts.readFont(ffr, null, null, oldToNewGIMap, true);
            if (oldToNewGIMap.size() > sizeNoCompGlyphs) {
                cidSet.mapChar(256 * 256, (char) 0);
            }
        } else {
            mergeFonts.readFont(ffr, getEmbedFontName(), null, null, true);
        }
        return getFontName();
    }

    private void readCharMap(FontContainer font, Map<Integer, String> gidToGlyph, GlyphData[] glyphData,
                             PDCIDFont mainFont, Map<Integer, Integer> oldToNewGIMap) throws IOException {
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
                char c = mappedChar.charAt(0);
                if (glyphData.length > 0
                        && glyphData[key] == null
                        && !CharUtilities.isAdjustableSpace(c)) {
                    continue;
                }
                boolean addToEnd = charMapGlobal.containsValue(key);
                if (addToEnd) {
                    addPrivateUseMapping(c, charMapGlobal.size() + 1);
                    charMapGlobal.put(mappedChar, charMapGlobal.size() + 1);
                } else {
                    addPrivateUseMapping(c, key);
                    charMapGlobal.put(mappedChar, key);
                }
                int glyph = 0;
                if (hasChar(c)) {
                    glyph = (int) mapChar(c);
                }
                oldToNewGIMap.put(key, glyph);
                if (!skipWidth) {
                    if (!(font.font instanceof PDTrueTypeFont)) {
                        widthPos = key;
                    }
                    float w = font.font.getWidth(widthPos);
                    if (w >= 0) {
                        if (mainFont instanceof PDCIDFontType0) {
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

    private Map<Integer, String> getMapping(FontContainer font, CMap c, int len) throws IOException {
        Map<Integer, String> mapping = new HashMap<Integer, String>();
        if (font.font instanceof PDType0Font) {
            PDCIDFont cidFont = ((PDType0Font) font.font).getDescendantFont();
            if (cidFont instanceof PDCIDFontType0) {
                mapping = getStrings(((PDCIDFontType0) cidFont).getCFFFont());
            }
        }
        if (c != null) {
            int last = font.getLastChar();
            if (last == -1) {
                last = len;
            }
            for (int i = font.getFirstChar(); i <= last; i++) {
                String l = c.toUnicode(i);
                if (l != null) {
                    mapping.put(i, l);
                }
            }
        }
        return mapping;
    }

    private Map<Integer, String> getStrings(CFFFont ff) throws IOException {
        CFFCharset cs = ff.getCharset();
        Map<Integer, String> strings = new LinkedHashMap<Integer, String>();
        for (int gid = 0; gid < 256; gid++) {
            int sid = cs.getCIDForGID(gid);
            if (sid != 0) {
                strings.put(sid, GlyphList.getAdobeGlyphList().toUnicode(readString(sid)));
            }
        }
        return strings;
    }

    private String readString(int index) throws IOException {
        if (index >= 0 && index <= 390) {
            return CFFStandardString.getName(index);
        }
        // technically this maps to .notdef, but we need a unique glyph name
        return "SID" + index;
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
                if (n.getKey() >= data.length) {
                    throw new IOException("Mapping not found in glyphData");
                }
//                if (data[n.getKey()] != null) {
//                    if (glyphs.containsKey(n.getValue()) && !glyphs.get(n.getValue()).equals(data[n.getKey()])) {
//                        return true;
//                    }
//                    glyphs.put(n.getValue(), data[n.getKey()]);
//                }
            }
            return false;
        }

    private InputStream readFontFile(PDFont font) throws IOException {
        PDFontDescriptor fd = font.getFontDescriptor();
        if (font instanceof PDType0Font) {
            PDCIDFont cidFont = ((PDType0Font) font).getDescendantFont();
            fd = cidFont.getFontDescriptor();
        }
        PDStream ff = fd.getFontFile3();
        if (ff == null) {
            ff = fd.getFontFile2();
            if (ff == null) {
                ff = fd.getFontFile();
            }
        }
        if (ff == null) {
            throw new IOException(font.getName() + " no fontfile");
        }
        InputStream is = ff.createInputStream();
        return new ByteArrayInputStream(IOUtils.toByteArray(is));
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
                    COSBase bboxArray = n.getValue();
                    if (bboxArray instanceof COSObject) {
                        bboxArray = ((COSObject) bboxArray).getObject();
                    }
                    float[] bboxf = ((COSArray)bboxArray).toFloatArray();
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
        return new ByteArrayInputStream(mergeFonts.getMergedFontSubset());
    }

    protected FontContainer getFont(COSDictionary fontData) throws IOException {
        if (!fontMap.containsKey(fontData)) {
            if (fontMap.size() > 10) {
                fontMap.clear();
            }
            fontMap.put(fontData, new FontContainer(fontData));
        }
        return fontMap.get(fontData);
    }

    protected static void setProperties(CustomFont cFont, PDFont font) {
        if (font.getFontDescriptor() != null) {
            cFont.setCapHeight((int) font.getFontDescriptor().getCapHeight());
            cFont.setAscender((int)font.getFontDescriptor().getAscent());
            cFont.setDescender((int)font.getFontDescriptor().getDescent());
            cFont.setXHeight((int)font.getFontDescriptor().getXHeight());
            cFont.setStemV((int)font.getFontDescriptor().getStemV());
        }
    }

    protected static void mergeMaxp(TrueTypeFont ttf, MaximumProfileTable outMaxp) throws IOException {
        org.apache.fontbox.ttf.MaximumProfileTable mp = ttf.getMaximumProfile();
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

    public String getMappedWord(List<String> word, byte[] bytes, FontContainer oldFont) {
        StringBuilder newHex = new StringBuilder();
        for (String str : word) {
            char c = str.charAt(0);
            if (str.length() > 1) {
                c = (char) str.hashCode();
            }
            if (hasChar(c)) {
                int mapped = (int)mapChar(c);
                newHex.append(String.format("%1$04x", mapped & 0xFFFF).toUpperCase(Locale.getDefault()));
            } else {
                return null;
            }
        }
        return "<" + newHex.toString() + ">";
    }
}
