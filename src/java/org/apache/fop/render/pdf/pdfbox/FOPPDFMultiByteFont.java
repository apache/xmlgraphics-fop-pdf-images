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
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

import org.apache.commons.io.IOUtils;
import org.apache.fontbox.cff.CFFFont;
import org.apache.fontbox.cmap.CMap;
import org.apache.fontbox.ttf.CMAPEncodingEntry;
import org.apache.fontbox.ttf.GlyphData;
import org.apache.fontbox.ttf.MaximumProfileTable;
import org.apache.fontbox.ttf.TrueTypeFont;

import org.apache.pdfbox.cos.COSArray;
import org.apache.pdfbox.cos.COSBase;
import org.apache.pdfbox.cos.COSDictionary;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.cos.COSObject;
import org.apache.pdfbox.encoding.Encoding;
import org.apache.pdfbox.pdmodel.common.PDStream;
import org.apache.pdfbox.pdmodel.font.PDCIDFont;
import org.apache.pdfbox.pdmodel.font.PDCIDFontType0Font;
import org.apache.pdfbox.pdmodel.font.PDCIDFontType2Font;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDFontDescriptorDictionary;
import org.apache.pdfbox.pdmodel.font.PDFontFactory;
import org.apache.pdfbox.pdmodel.font.PDTrueTypeFont;
import org.apache.pdfbox.pdmodel.font.PDType0Font;

import org.apache.fop.fonts.CIDFontType;
import org.apache.fop.fonts.CustomFont;
import org.apache.fop.fonts.EmbeddingMode;
import org.apache.fop.fonts.FontType;
import org.apache.fop.fonts.MultiByteFont;
import org.apache.fop.fonts.truetype.FontFileReader;
import org.apache.fop.pdf.PDFDictionary;
import org.apache.fop.util.CharUtilities;

public class FOPPDFMultiByteFont extends MultiByteFont implements FOPPDFFont {
    protected PDFDictionary ref;
    private Map<Integer, Integer> newWidth = new TreeMap<Integer, Integer>();
    private Map<String, Integer> charMapGlobal = new LinkedHashMap<String, Integer>();
    private MergeTTFonts mergeTTFonts = new MergeTTFonts();
    private MergeCFFFonts mergeCFFFonts = new MergeCFFFonts();
    //private Map<String, GlyphData> glyphs = new HashMap<String, GlyphData>();
    private final Map<COSDictionary, PDFont> fontMap = new HashMap<COSDictionary, PDFont>();

    public FOPPDFMultiByteFont(COSDictionary fontData, String name) throws IOException {
        super(null, EmbeddingMode.SUBSET);
        //this stops fop modifying font later on
        setEmbeddingMode(EmbeddingMode.FULL);
        readFontBBox(fontData);
        setFontName(name);
        addFont(fontData);
    }

    public String addFont(COSDictionary fontData) throws IOException {
        PDFont font = getFont(fontData);
        setProperties(this, font);
        PDFont mainFont = font;
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
        //if (glyphData.length > 0 && differentGlyphData(glyphData, mapping)) {
        //    return null;
        //}
        Map<Integer, String> gidToGlyph = new TreeMap<Integer, String>(mapping);
        if (mainFont instanceof PDTrueTypeFont) {
            CMAPEncodingEntry cmap = ttf.getCMAP().getCmaps()[0];
            gidToGlyph.clear();
            int[] gidToCode = cmap.getGlyphIdToCharacterCode();
            for (int i = 1; i < glyphData.length && i < gidToCode.length; i++) {
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

    private void readCharMap(PDFont font, Map<Integer, String> gidToGlyph, GlyphData[] glyphData,
                             PDFont mainFont, Map<Integer, Integer> oldToNewGIMap) {
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

    private Map<Integer, String> getMapping(PDFont font, CMap c, int len) throws IOException {
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

//        private boolean differentGlyphData(GlyphData[] data, Map<Integer, String> mapping) throws IOException {
//            Map<String, Integer> tmpMap = new HashMap<String, Integer>();
//            for (Map.Entry<Integer, String> entry : mapping.entrySet()) {
//                if (!tmpMap.containsKey(entry.getValue())) {
//                    tmpMap.put(entry.getValue(), entry.getKey());
//                }
//            }
//            mapping.clear();
//            for (Map.Entry<String, Integer> entry : tmpMap.entrySet()) {
//                mapping.put(entry.getValue(), entry.getKey());
//            }
//
//            for (Map.Entry<Integer, String> n : mapping.entrySet()) {
//                if (data[n.getKey()] != null) {
//                    if (glyphs.containsKey(n.getValue()) && !glyphs.get(n.getValue()).equals(data[n.getKey()])) {
//                        return true;
//                    }
//                    glyphs.put(n.getValue(), data[n.getKey()]);
//                }
//            }
//            return false;
//        }

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

    protected PDFont getFont(COSDictionary fontData) throws IOException {
        if (!fontMap.containsKey(fontData)) {
            if (fontMap.size() > 10) {
                fontMap.clear();
            }
            fontMap.put(fontData, PDFontFactory.createFont(fontData));
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

    protected static void mergeMaxp(TrueTypeFont ttf, MaximumProfileTable outMaxp) {
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
}
