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
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import org.apache.fontbox.cff.CFFType1Font;
import org.apache.fontbox.cmap.CMap;

import org.apache.fontbox.ttf.CmapSubtable;
import org.apache.fontbox.ttf.TrueTypeFont;

import org.apache.pdfbox.cos.COSArray;
import org.apache.pdfbox.cos.COSBase;
import org.apache.pdfbox.cos.COSDictionary;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.cos.COSNumber;

import org.apache.pdfbox.pdmodel.common.PDStream;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDFontDescriptor;
import org.apache.pdfbox.pdmodel.font.PDTrueTypeFont;
import org.apache.pdfbox.pdmodel.font.PDType1CFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.encoding.BuiltInEncoding;
import org.apache.pdfbox.pdmodel.font.encoding.Encoding;

import org.apache.fop.fonts.EmbeddingMode;
import org.apache.fop.fonts.FontType;
import org.apache.fop.fonts.SingleByteEncoding;
import org.apache.fop.fonts.SingleByteFont;
import org.apache.fop.pdf.PDFDictionary;
import org.apache.fop.pdf.PDFText;

public class FOPPDFSingleByteFont extends SingleByteFont implements FOPPDFFont {
    private int fontCount;
    private FontContainer font;
    protected PDFDictionary ref;
    protected Map<String, Integer> charMapGlobal = new LinkedHashMap<String, Integer>();
    private Map<Integer, Integer> newWidth = new HashMap<Integer, Integer>();
    private Map<String, byte[]> charStringsDict;
    private List<MergeTTFonts.Cmap> newCmap = new ArrayList<MergeTTFonts.Cmap>();
    private Map<Integer, String> encodingMap = new TreeMap<Integer, String>();
    private int encodingSkip;
    private MergeFonts mergeFonts;
    private String shortFontName;
    private final Map<COSDictionary, FontContainer> fontMap = new HashMap<COSDictionary, FontContainer>();

    public FOPPDFSingleByteFont(COSDictionary fontData, String name) throws IOException {
        super(null, EmbeddingMode.FULL);
        if (fontData.getItem(COSName.SUBTYPE) == COSName.TRUE_TYPE) {
            setFontType(FontType.TRUETYPE);
        }
        width = new int[0];
        font = getFont(fontData);
        setFirstChar(font.getFirstChar());
        setLastChar(font.getLastChar());
        shortFontName = MergeFontsPDFWriter.getName(font.font.getName());
        loadFontFile(font);
        float[] bBoxF = font.getBoundingBox();
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
        FOPPDFMultiByteFont.setProperties(this, font.font);
        if (font.getWidths() != null) {
            //if width contains 0 we cant rely on codeToNameMap
            boolean usesZero = font.getWidths().contains(0);
            Set<Integer> codeToName = getCodeToName(font.getEncoding()).keySet();
            for (int i = getFirstChar();
                 i <= Math.min(getLastChar(), getFirstChar() + font.getWidths().size()); i++) {
                if (usesZero || codeToName.contains(i)) {
                    int w = font.getWidths().get(i - getFirstChar());
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
            COSBase cos = null;
            if (!(encoding instanceof BuiltInEncoding)) {
                cos = encoding.getCOSObject();
            }
            if (cos instanceof COSDictionary) {
                COSDictionary enc = (COSDictionary) cos;
                COSName baseEncodingName = (COSName) enc.getDictionaryObject(COSName.BASE_ENCODING);
                if (baseEncodingName != null) {
                    Encoding baseEncoding = Encoding.getInstance(baseEncodingName);
                    codeToName.putAll(baseEncoding.getCodeToNameMap());
                }
                COSArray differences = (COSArray)enc.getDictionaryObject(COSName.DIFFERENCES);
                int currentIndex = -1;
                for (int i = 0; differences != null && i < differences.size(); i++) {
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

    private Object getCmap(FontContainer font) throws IOException {
        if (font.getEncoding() != null) {
            return font.getEncoding();
        }
        if (font.getToUnicodeCMap() == null) {
            throw new IOException("No cmap found in " + font.font.getName());
        }
        return font.getToUnicodeCMap();
    }

    private PDStream readFontFile(PDFont font) throws IOException {
        PDFontDescriptor fd = font.getFontDescriptor();
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
            throw new IOException(font.getName() + " no font file");
        }
        return ff;
    }

    private void loadFontFile(FontContainer font) throws IOException {
        PDStream ff = readFontFile(font.font);
        mergeFontFile(ff.createInputStream(), font);
        if (font.font instanceof PDTrueTypeFont) {
            TrueTypeFont ttfont = ((PDTrueTypeFont) font.font).getTrueTypeFont();
            CmapSubtable[] cmapList = ttfont.getCmap().getCmaps();
            for (CmapSubtable c : cmapList) {
                MergeTTFonts.Cmap tempCmap = getNewCmap(c.getPlatformId(), c.getPlatformEncodingId());
                for (int i = 0; i < 256 * 256; i++) {
                    int gid = c.getGlyphId(i);
                    if (gid != 0) {
                        tempCmap.glyphIdToCharacterCode.put(i, gid);
                    }
                }
            }
            FOPPDFMultiByteFont.mergeMaxp(ttfont, ((MergeTTFonts)mergeFonts).maxp);
        }
    }

    private MergeTTFonts.Cmap getNewCmap(int platformID, int platformEncodingID) {
        for (MergeTTFonts.Cmap cmap : newCmap) {
            if (cmap.platformId == platformID && cmap.platformEncodingId == platformEncodingID) {
                return cmap;
            }
        }
        MergeTTFonts.Cmap cmap = new MergeTTFonts.Cmap(platformID, platformEncodingID);
        newCmap.add(cmap);
        return cmap;
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
        return shortFontName;
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
        FontContainer font = getFont(fontData);
        if ((font.font instanceof PDType1Font || font.font instanceof PDType1CFont) && differentGlyphData(font.font)) {
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

    private Map<String, byte[]> getCharStringsDict(PDFont font) throws IOException {
        if (font instanceof PDType1Font) {
            return ((PDType1Font)font).getType1Font().getCharStringsDict();
        }
        CFFType1Font cffFont = ((PDType1CFont) font).getCFFType1Font();
        List<byte[]> bytes = cffFont.getCharStringBytes();
        Map<String, byte[]> map = new HashMap<String, byte[]>();
        for (int i = 0; i < bytes.size(); i++) {
            map.put(cffFont.getCharset().getNameForGID(i), bytes.get(i));
        }
        return map;
    }

    private boolean differentGlyphData(PDFont otherFont) throws IOException {
        if (charStringsDict == null) {
            charStringsDict = getCharStringsDict(font.font);
        }
        Map<String, byte[]> otherFontMap = getCharStringsDict(otherFont);
        for (Map.Entry<String, byte[]> s : otherFontMap.entrySet()) {
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

    private void mergeWidths(FontContainer font) throws IOException {
        int w = 0;
        int skipGlyphIndex = getLastChar() + 1;
        Object cmap = getCmap(font);
        Set<Integer> codeToName = getCodeToName(font.getEncoding()).keySet();
        for (int i = font.getFirstChar(); i <= font.getLastChar(); i++) {
            boolean addedWidth = false;
            int glyphIndexPos = skipGlyphIndex;
            if (font.font instanceof PDTrueTypeFont) {
                glyphIndexPos = i;
            }
            int neww = 0;
            if (font.getWidths() != null) {
                neww = font.getWidths().get(i - font.getFirstChar());
                if (!newWidth.containsKey(i) || newWidth.get(i) == 0) {
                    if (getFontType() == FontType.TYPE1
                            || font.font instanceof PDTrueTypeFont
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
            return c.toUnicode(i);
        }
        Encoding enc = (Encoding)cmap;
        return enc.getName(i);
    }

    public String getEncodingName() {
        return font.getBaseEncodingName();
    }

    private void addEncoding(FontContainer fontForEnc) {
        List<String> added = new ArrayList<String>(encodingMap.values());
        Map<Integer, String> codeToName = getCodeToName(fontForEnc.getEncoding());
        for (int i = fontForEnc.getFirstChar(); i <= fontForEnc.getLastChar(); i++) {
            if (codeToName.keySet().contains(i)) {
                String s = codeToName.get(i);
                if (!added.contains(s) || !encodingMap.containsKey(i)) {
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

    private void mergeFontFile(InputStream ff, FontContainer pdFont) throws IOException {
        if (mergeFonts == null) {
            if (getFontType() == FontType.TRUETYPE) {
                mergeFonts = new MergeTTFonts(newCmap);
            } else if (getFontType() == FontType.TYPE1) {
                mergeFonts = new MergeType1Fonts();
            } else {
                mergeFonts = new MergeCFFFonts();
            }
        }
        Map<Integer, Integer> chars = new HashMap<Integer, Integer>();
        chars.put(0, 0);
        mergeFonts.readFont(ff, shortFontName, pdFont, chars, false);
        fontCount++;
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

    public String getMappedWord(List<String> word, byte[] bytes, FontContainer oldFont) {
        StringBuffer newOct = new StringBuffer();
        int i = 0;
        for (String str : word) {
            Integer mapped = getMapping(bytes[i], oldFont);
            if (mapped == null) {
                char c = str.charAt(0);
                if (str.length() > 1) {
                    c = (char) str.hashCode();
                }
                if (hasChar(c)) {
                    mapped = (int)mapChar(c);
                } else {
                    return null;
                }
            }
            PDFText.escapeStringChar((char)mapped.intValue(), newOct);
            i++;
        }
        return "(" + newOct.toString() + ")";
    }

    private Integer getMapping(byte i, FontContainer oldFont) {
        if (oldFont.getEncoding() != null) {
            String name = oldFont.getEncoding().getName(i);
            if (!name.equals(".notdef") && charMapGlobal.containsKey(name)) {
                return charMapGlobal.get(name);
            }
        }
        return null;
    }
}
