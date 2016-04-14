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

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.apache.fontbox.cff.CFFCIDFont;
import org.apache.fontbox.cff.CFFCharset;
import org.apache.fontbox.cff.CFFEncoding;
import org.apache.fontbox.cff.CFFFont;
import org.apache.fontbox.cff.CFFISOAdobeCharset;
import org.apache.fontbox.cff.CFFParser;
import org.apache.fontbox.cff.CFFStandardEncoding;
import org.apache.fontbox.cff.CFFStandardString;
import org.apache.fontbox.cff.CFFType1Font;

import org.apache.fop.fonts.cff.CFFDataReader;
import org.apache.fop.fonts.truetype.FontFileReader;

public class MergeCFFFonts extends OTFSubSetFile implements MergeFonts {
    protected List<LinkedHashMap<Integer, Integer>> subsetGlyphsList = new ArrayList<LinkedHashMap<Integer, Integer>>();
    private boolean fallbackIndex;
    private int charsetOffset;
    private int fontFileSize;
    private Set<String> used = new HashSet<String>();
    private List<String> strings = new ArrayList<String>();
    private List<Integer> chars = new ArrayList<Integer>();
    private List<String> added = new ArrayList<String>();
    private Map<Integer, Integer> range = new LinkedHashMap<Integer, Integer>();
    private int noOfFonts;
    private CFFEncoding encoding = null;

    public MergeCFFFonts() throws IOException {
        gidToSID = new LinkedHashMap<Integer, Integer>();
        subsetCharStringsIndex = new ArrayList<byte[]>();
    }

    public void readFont(InputStream is, String name, FontContainer fontContainer,
                         Map<Integer, Integer> subsetGlyphs, boolean cid) throws IOException {
        this.embeddedName = name;
        FontFileReader fontFile = new FontFileReader(is);
        CFFParser p = new CFFParser();
        CFFFont ff = p.parse(fontFile.getAllBytes()).get(0);

        if (used.containsAll(getStrings(ff).keySet())) {
            return;
        }
        fontFileSize += fontFile.getFileSize();
        this.fontFile = fontFile;
        used.addAll(getStrings(ff).keySet());
        if (fileFont == null) {
            fileFont = ff;
        }
        LinkedHashMap<Integer, Integer> sg = new LinkedHashMap<Integer, Integer>();
        for (int i = 0; i < ff.getNumCharStrings() + 1; i++) {
            sg.put(i, i);
        }
        subsetGlyphsList.add(sg);
        cffReader = new CFFDataReader(fontFile);

        for (int sid : getSids(ff.getCharset())) {
            if (sid >= NUM_STANDARD_STRINGS) {
                int index = sid - NUM_STANDARD_STRINGS;
                if (index <= cffReader.getStringIndex().getNumObjects()) {
                    String data = new String(cffReader.getStringIndex().getValue(index), "US-ASCII");
                    if (!strings.contains(data)) {
                        strings.add(data);
                    }
                }
            }
        }

        if (ff instanceof CFFType1Font) {
            encoding = ((CFFType1Font)ff).getEncoding();
            if (!(encoding instanceof CFFStandardEncoding)) {
                for (int c : encoding.getCodeToNameMap().keySet()) {
                    if (!chars.contains(c) && c != 0) {
                        chars.add(c);
                    }
                }
            }
        }
        setupMapping(ff.getCharset(), sg);

        for (Map.Entry<String, byte[]> s : getStrings(ff).entrySet()) {
            if (!added.contains(s.getKey())) {
                subsetCharStringsIndex.add(s.getValue());
                added.add(s.getKey());
            }
        }

        CFFCharset cSet = ff.getCharset();
        String cClass = cSet.getClass().getName();
        if (cClass.equals("org.apache.fontbox.cff.CFFParser$Format1Charset")
                || cClass.equals("org.apache.fontbox.cff.CFFParser$Format0Charset")) {
            for (int sid : getSids(cSet)) {
                range.put(sid, 0);
            }
        }
        noOfFonts++;
    }

    private void setupMapping(CFFCharset charset, LinkedHashMap<Integer, Integer> sg) {
        int subsetGlyphIndex = 0;
        for (int sid : getSids(charset)) {
            if (sg.containsKey(subsetGlyphIndex)) {
                int gid = sg.get(subsetGlyphIndex);

                //Check whether the SID falls into the standard string set
                if (sid < NUM_STANDARD_STRINGS) {
                    gidToSID.put(sg.get(gid), sid);
                } else {
                    int index = sid - NUM_STANDARD_STRINGS;
                    if (index <= cffReader.getStringIndex().getNumObjects()) {
                        gidToSID.put(sg.get(gid), stringIndexData.size() + NUM_STANDARD_STRINGS - 1);
                    } else {
                        gidToSID.put(sg.get(gid), index);
                    }
                }
                subsetGlyphIndex++;
            }
        }
    }

    public static List<Integer> getSids(CFFCharset cSet) {
        List<Integer> sids = new ArrayList<Integer>();
        try {
            for (int gid = 0; gid < 1024; gid++) {
                int sid = cSet.getCIDForGID(gid);
                if (sid != 0) {
                    sids.add(sid);
                }
            }
        } catch (IllegalStateException e) {
            try {
                final Method getSIDForGID = CFFCharset.class.getDeclaredMethod("getSIDForGID", int.class);
                AccessController.doPrivileged(new PrivilegedAction() {
                    public Object run() {
                        getSIDForGID.setAccessible(true);
                        return null;
                    }
                });
                for (int gid = 0; gid < 1024; gid++) {
                    int sid = (Integer)getSIDForGID.invoke(cSet, gid);
                    if (sid != 0) {
                        sids.add(sid);
                    }
                }
            } catch (NoSuchMethodException e1) {
                throw new RuntimeException(e1);
            } catch (InvocationTargetException e1) {
                throw new RuntimeException(e1);
            } catch (IllegalAccessException e1) {
                throw new RuntimeException(e1);
            }
        }
        return sids;
    }

    public static Map<String, byte[]> getStrings(CFFFont ff) throws IOException {
        CFFCharset cs = ff.getCharset();
        List<byte[]> csbytes = ff.getCharStringBytes();
        Map<String, byte[]> strings = new LinkedHashMap<String, byte[]>();
        int i = 0;
        try {
            for (int gid = 0; gid < 256; gid++) {
                String name = cs.getNameForGID(gid);
                if (name != null && i < csbytes.size()) {
                    strings.put(name, csbytes.get(i));
                    i++;
                }
            }
        } catch (IllegalStateException e) {
            strings.put(".notdef", csbytes.get(0));
            for (int sid : getSids(ff.getCharset())) {
                if (i < csbytes.size()) {
                    i++;
                    strings.put(readString(sid), csbytes.get(i));
                }
            }
        }
        return strings;
    }

    private static String readString(int index) throws IOException {
        if (index >= 0 && index <= 390) {
            return CFFStandardString.getName(index);
        }
        // technically this maps to .notdef, but we need a unique glyph name
        return "SID" + index;
    }

    public byte[] getMergedFontSubset() throws IOException {
        output = new byte[fontFileSize * 2];
        if (noOfFonts == 1) {
            writeBytes(fontFile.getAllBytes());
            return super.getFontSubset();
        }
        createCFF();
        return super.getFontSubset();
    }

    protected void createCFF() throws IOException {
        //Header
        writeBytes(cffReader.getHeader());

        //Name Index
        writeIndex(Arrays.asList(fileFont.getName().getBytes("UTF-8")));

        //Keep offset of the topDICT so it can be updated once all data has been written
        int topDictOffset = currentPos;
        //Top DICT Index and Data
        byte[] topDictIndex = cffReader.getTopDictIndex().getByteData();
        int offSize = topDictIndex[2];
        writeBytes(topDictIndex, 0, 3 + (offSize * 2));
        int topDictDataOffset = currentPos;
        writeTopDICT();
        createCharStringData();

        //String index
        writeStringIndex();

        Map<String, CFFDataReader.DICTEntry> topDICT = cffReader.getTopDictEntries();
        final CFFDataReader.DICTEntry charString = topDICT.get("CharStrings");
        final CFFDataReader.DICTEntry encodingEntry = topDICT.get("Encoding");

        int encodingOffset;
        if (encodingEntry != null && charString.getOffset() > encodingEntry.getOffset()) {
            charsetOffset = currentPos;
            if (!fallbackIndex) {
                charsetOffset += 2;
            }
            writeCharsetTable(cffReader.getFDSelect() != null, !fallbackIndex);
            encodingOffset = currentPos;
            writeEncoding();
        } else {
            writeCard16(0);
            encodingOffset = currentPos;
            writeEncoding();
            charsetOffset = currentPos;
            writeCharsetTable(cffReader.getFDSelect() != null, false);
        }

        int fdSelectOffset = currentPos;
        if (cffReader.getFDSelect() != null) {
            writeByte(0);
            for (int i = 0; i < subsetCharStringsIndex.size(); i++) {
                writeByte(0);
            }
        }

        //Keep offset to modify later with the local subroutine index offset
        int privateDictOffset = currentPos;
        writePrivateDict();

        //Char Strings Index
        int charStringOffset = currentPos;
        writeIndex(subsetCharStringsIndex);

        //Local subroutine index
        int localIndexOffset = currentPos;
        if (!subsetLocalIndexSubr.isEmpty()) {
            writeIndex(subsetLocalIndexSubr);
        }

        if (cffReader.getFDSelect() != null) {
            int fdArrayOffset = currentPos;
            writeCard16(1);
            writeByte(1); //Offset size
            writeByte(1); //First offset
            int count = 1;
            for (CFFDataReader.FontDict fdFont : cffReader.getFDFonts()) {
                count += fdFont.getByteData().length;
                writeByte(count);
            }
            int fdByteData = currentPos;
            for (CFFDataReader.FontDict fdFont : cffReader.getFDFonts()) {
                writeBytes(fdFont.getByteData());
            }
            List<Integer> privateDictOffsets = new ArrayList<Integer>();
            for (CFFDataReader.FontDict curFDFont : cffReader.getFDFonts()) {
                privateDictOffsets.add(currentPos);
                writeBytes(curFDFont.getPrivateDictData());
                writeIndex(new ArrayList<byte[]>());
            }
            currentPos = fdByteData;
            int i = 0;
            for (CFFDataReader.FontDict fdFont : cffReader.getFDFonts()) {
                byte[] fdFontByteData = fdFont.getByteData();
                Map<String, CFFDataReader.DICTEntry> fdFontDict = cffReader.parseDictData(fdFontByteData);
                //Update the Private dict reference
                CFFDataReader.DICTEntry fdPrivate = fdFontDict.get("Private");
                fdFontByteData = updateOffset(fdFontByteData,
                        fdPrivate.getOffset() + fdPrivate.getOperandLengths().get(0),
                        fdPrivate.getOperandLengths().get(1),
                        privateDictOffsets.get(i));
                writeBytes(fdFontByteData);
                i++;
            }

            updateCIDOffsets(topDictDataOffset, fdArrayOffset, fdSelectOffset, charsetOffset, charStringOffset,
                    encodingOffset);
        } else {
            //Update the offsets
            updateOffsets(topDictOffset, charsetOffset, charStringOffset, privateDictOffset, localIndexOffset,
                    encodingOffset);
        }
    }

    protected void writeEncoding() throws IOException {
        if (!chars.isEmpty()) {
            writeCard16(chars.size());
            for (int i : chars) {
                writeByte(i);
            }
        }
    }

    protected void writeStringIndex() throws IOException {
        int stringIndexSize = stringIndexData.size();
        for (String s : strings) {
            stringIndexData.add(s.getBytes("US-ASCII"));
        }

        //Write the String Index
        if (!stringIndexData.isEmpty()) {
            if (!strings.isEmpty() && !new String(stringIndexData.get(0), "UTF-8").equals(strings.get(0))) {
                //Keep strings in order as they are referenced from the TopDICT
                for (int i = 0; i < stringIndexSize; i++) {
                    stringIndexData.add(stringIndexData.remove(0));
                }
            } else {
                String notice = (String)fileFont.getTopDict().get("Notice");
                if (notice != null && !(fileFont instanceof CFFCIDFont)) {
                    stringIndexData.add(notice.getBytes("ISO-8859-1"));
                }
            }
            stringIndexData.add(embeddedName.getBytes("UTF-8"));
            writeIndex(stringIndexData);
        } else {
            String notice = (String)fileFont.getTopDict().get("Notice");
            if (notice != null) {
                writeIndex(Arrays.<byte[]>asList(notice.getBytes("ISO-8859-1"), embeddedName.getBytes("UTF-8")));
            } else {
                List<byte[]> sindex = new ArrayList<byte[]>();
                sindex.add(cffReader.getStringIndex().getData());
                if (sindex.size() > 1) {
                    fallbackIndex = true;
                    writeIndex(sindex);
                } else if (sindex.size() == 1) {
                    writeIndex(Arrays.asList(embeddedName.getBytes("UTF-8")));
                } else {
                    writeCard16(0);
                }
            }
        }
    }

    protected void createCharStringData() throws IOException {
        //Create the two lists which are to store the local and global subroutines
        subsetLocalIndexSubr = new ArrayList<byte[]>();

        localUniques = new ArrayList<Integer>();
        globalUniques = new ArrayList<Integer>();

        //Store the size of each subset index and clear the unique arrays
        localUniques.clear();
        globalUniques.clear();
    }

    protected void writeCharsetTable(boolean cidFont, boolean afterstringindex) throws IOException {
        if (range.isEmpty()) {
            writeByte(0);
            for (Map.Entry<Integer, Integer> gid : gidToSID.entrySet()) {
                if (cidFont && gid.getKey() == 0) {
                    continue;
                }
                writeCard16(cidFont ? gid.getKey() : gid.getValue());
            }
        } else {
            writeFormat1CS(range, afterstringindex);
        }
    }

    private void writeFormat1CS(Map<Integer, Integer> range, boolean afterstringindex) {
        if (!afterstringindex) {
            charsetOffset += 2;
        }
        writeByte(0);
        writeCard16(1);
        updateStandardRange(range);
        for (Map.Entry<Integer, Integer> i : range.entrySet()) {
            writeCard16(i.getKey());
            writeByte(i.getValue());
        }
        writeByte(1);
    }

    private void updateStandardRange(Map<Integer, Integer> range) {
        if (range.containsKey(NUM_STANDARD_STRINGS) && range.containsKey(NUM_STANDARD_STRINGS + 1)) {
            boolean mixedCS = false;
            for (int i : range.keySet()) {
                if (i < NUM_STANDARD_STRINGS && i > 1) {
                    mixedCS = true;
                    break;
                }
            }
            if (!mixedCS) {
                if (range.containsKey(1)) {
                    range.clear();
                    range.put(1, 0);
                }
                int last = -1;
                boolean simpleRange = false;
                for (int i : range.keySet()) {
                    simpleRange = last + 1 == i;
                    last = i;
                }
                if (simpleRange) {
                    for (int i = NUM_STANDARD_STRINGS; i < NUM_STANDARD_STRINGS + subsetCharStringsIndex.size(); i++) {
                        range.put(i, 0);
                    }
                } else {
                    range.put(NUM_STANDARD_STRINGS, subsetCharStringsIndex.size());
                }
            }
        } else if (cffReader.getFDSelect() instanceof CFFDataReader.Format3FDSelect) {
            int last = -1;
            int count = 1;
            Set<Integer> r = new TreeSet<Integer>(range.keySet());
            for (int i : r) {
                if (last + count == i) {
                    range.remove(i);
                    range.put(last, count);
                    count++;
                } else {
                    last = i;
                    count = 1;
                }
            }
        }
    }

    @Override
    protected void updateFixedOffsets(Map<String, CFFDataReader.DICTEntry> topDICT, int dataTopDictOffset,
                                      int charsetOffset, int charStringOffset, int encodingOffset) {
        //Charset offset in the top dict
        final CFFDataReader.DICTEntry charset = topDICT.get("charset");
        if (charset != null) {
            int oldCharsetOffset = dataTopDictOffset + charset.getOffset();
            int oldCharset = Integer.parseInt(String.format("%02x", output[oldCharsetOffset] & 0xff), 16);
            if (oldCharset >= 32 && oldCharset <= 246) {
                charsetOffset += 139;
            }
            output = updateOffset(output, oldCharsetOffset, charset.getOperandLength(), charsetOffset);
        }

        //Char string index offset in the private dict
        final CFFDataReader.DICTEntry charString = topDICT.get("CharStrings");
        int oldCharStringOffset = dataTopDictOffset + charString.getOffset();
        int oldString = Integer.parseInt(String.format("%02x", output[oldCharStringOffset] & 0xff), 16);
        if (oldString >= 32 && oldString <= 246) {
            charStringOffset += 139;
        }
        if (!(fileFont.getCharset() instanceof CFFISOAdobeCharset)) {
            output = updateOffset(output, oldCharStringOffset, charString.getOperandLength(), charStringOffset);
        }

        final CFFDataReader.DICTEntry encodingEntry = topDICT.get("Encoding");
        if (encodingEntry != null && encodingEntry.getOperands().get(0).intValue() != 0
                && encodingEntry.getOperands().get(0).intValue() != 1) {
            int oldEncodingOffset = dataTopDictOffset + encodingEntry.getOffset();
            int oldEnc = Integer.parseInt(String.format("%02x", output[oldEncodingOffset] & 0xff), 16);
            if (oldEnc >= 32 && oldEnc <= 246) {
                encodingOffset += 139;
            } else {
                encodingOffset--;
            }
            output = updateOffset(output, oldEncodingOffset, encodingEntry.getOperandLength(), encodingOffset);
        }
    }

    protected void writeCIDCount(CFFDataReader.DICTEntry dictEntry) throws IOException {
        writeBytes(dictEntry.getByteData());
    }
}
