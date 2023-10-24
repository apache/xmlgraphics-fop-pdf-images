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
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import org.apache.fop.fonts.truetype.FontFileReader;
import org.apache.fop.fonts.truetype.GlyfTable;
import org.apache.fop.fonts.truetype.OFDirTabEntry;
import org.apache.fop.fonts.truetype.OFMtxEntry;
import org.apache.fop.fonts.truetype.OFTableName;
import org.apache.fop.fonts.truetype.TTFSubSetFile;

public class MergeTTFonts extends TTFSubSetFile implements MergeFonts {
    private Map<Integer, Glyph> added = new TreeMap<>();
    private int origIndexesLen;
    private int size;
    protected MaximumProfileTable maxp = new MaximumProfileTable();
    private Integer nhmtxDiff = null;
    private List<Cmap> cmap;
    private Set<Integer> composedGlyphs = Collections.emptySet();
    private Set<Integer> compositeGlyphs = Collections.emptySet();

    static class Glyph {
        final byte[] data;
        final OFMtxEntry mtx;
        final boolean composed;
        final boolean composite;
        final int origGlyphIndex;
        Glyph(byte[] data, OFMtxEntry mtx, boolean composed, boolean composite, int origGlyphIndex) {
            this.data = data;
            this.mtx = mtx;
            this.composed = composed;
            this.composite = composite;
            this.origGlyphIndex = origGlyphIndex;
        }
    }

    public MergeTTFonts(List<Cmap> cmap) {
        this.cmap = cmap;
    }

    /**
     * Create the glyf table and fill in loca table
     * @param glyphs map of glyphs
     * @param in fontfile
     * @throws IOException on error
     */
    private void readGlyf(Map<Integer, Integer> glyphs, FontFileReader in) throws IOException {
        OFDirTabEntry entry = dirTabs.get(OFTableName.GLYF);
        if (entry != null) {
            int[] origIndexes = buildSubsetIndexToOrigIndexMap(glyphs);
            for (int i = 0; i < origIndexes.length; i++) {
                int nextOffset = 0;
                int origGlyphIndex = origIndexes[i];
                if (origGlyphIndex >= (mtxTab.length - 1)) {
                    nextOffset = (int)lastLoca;
                } else {
                    nextOffset = (int)mtxTab[origGlyphIndex + 1].getOffset();
                }
                int glyphOffset = (int)mtxTab[origGlyphIndex].getOffset();
                int glyphLength = nextOffset - glyphOffset;
                if (glyphLength < 0) {
                    continue;
                }
                byte[] glyphData = in.getBytes(
                        (int)entry.getOffset() + glyphOffset,
                        glyphLength);

                Glyph g = new Glyph(glyphData, mtxTab[origGlyphIndex],
                        composedGlyphs.contains(origGlyphIndex),
                        compositeGlyphs.contains(origGlyphIndex), origGlyphIndex);
                if (!cid && (origIndexesLen == 0 || (glyphLength > 0 && i > 0))) {
                    added.put(i, g);
                } else if (cid) {
                    added.put(i + origIndexesLen, g);
                }
            }
            if (!cid) {
                origIndexesLen = origIndexes.length;
            } else {
                origIndexesLen += origIndexes.length;
            }
        } else {
            throw new IOException("Can't find glyf table");
        }
    }

    private void createGlyf() throws IOException {
        OFDirTabEntry entry = dirTabs.get(OFTableName.GLYF);
        int size = 0;
        int startPos = 0;
        int endOffset = 0;    // Store this as the last loca

        if (entry != null) {
            pad4();
            startPos = currentPos;
            /* Loca table must be in order by glyph index, so build
             * an array first and then write the glyph info and
             * location offset.
             */
            glyphOffsets = new int[origIndexesLen];
            for (Map.Entry<Integer, Glyph> gly : added.entrySet()) {
                byte[] glyphData = gly.getValue().data;
                int glyphLength = glyphData.length;
                int i = gly.getKey();
                if (i >= origIndexesLen) {
                    continue;
                }
                int endOffset1 = endOffset;
                // Copy glyph
                writeBytes(glyphData);
                // Update loca table
                if (cid || locaFormat == 1) {
                    writeULong(locaOffset + i * 4, currentPos - startPos);
                }
                if ((currentPos - startPos + glyphLength) > endOffset1) {
                    endOffset1 = currentPos - startPos + glyphLength;
                }

                // Store the glyph boundary positions relative to the start of the font
                glyphOffsets[i] = currentPos;
                currentPos += glyphLength;
                realSize += glyphLength;

                endOffset = endOffset1;
            }

            size = currentPos - startPos;

            currentPos += 12;
            realSize += 12;
            updateCheckSum(startPos, size + 12, OFTableName.GLYF);

            // Update loca checksum and last loca index
            if (cid || locaFormat == 1) {
                writeULong(locaOffset + added.size() * 4, endOffset);
            }
            int locaSize = added.size() * 4 + 4;
            int checksum = getCheckSum(output, locaOffset, locaSize);
            writeULong(offsets.get(OFTableName.LOCA), checksum);
            int padSize = (locaOffset + locaSize) % 4;
            newDirTabs.put(OFTableName.LOCA,
                    new OFDirTabEntry(locaOffset, locaSize + padSize));

            if (!cid && locaFormat == 0) {
                int i = 0;
                int offset = 0;
                for (Glyph e : added.values()) {
                    writeUShort(locaOffset + i * 2, offset / 2);
                    offset += e.data.length;
                    i++;
                }
                writeUShort(locaOffset + i * 2, offset / 2);
            }
        } else {
            throw new IOException("Can't find glyf table");
        }
    }

    /**
     * Create the hmtx table by copying metrics from original
     * font to subset font. The glyphs Map contains an
     * Integer key and Integer value that maps the original
     * metric (key) to the subset metric (value)
     * @throws IOException on error
     */
    protected void createHmtx() throws IOException {
        OFTableName hmtx = OFTableName.HMTX;
        OFDirTabEntry entry = dirTabs.get(hmtx);
        if (entry != null) {
            pad4();
            // int offset = (int)entry.offset;

            int longHorMetricSize = added.size() * 2;
            int leftSideBearingSize = added.size() * 2;
            int hmtxSize = longHorMetricSize + leftSideBearingSize;

            for (Map.Entry<Integer, Glyph> e : added.entrySet()) {
                Integer subsetIndex = e.getKey();
                OFMtxEntry mtx = e.getValue().mtx;
                writeUShort(currentPos + subsetIndex * 4,
                        mtx.getWx());
                writeUShort(currentPos + subsetIndex * 4 + 2,
                        mtx.getLsb());
            }

            updateCheckSum(currentPos, hmtxSize, hmtx);
            currentPos += hmtxSize;
            realSize += hmtxSize;
        } else {
            throw new IOException("Can't find hmtx table");
        }
    }

    /**
     * Returns a subset of the original font.
     *
     * @param is font file
     * @param name name
     * @param fontContainer fontContainer
     * @param subsetGlyphs Map of glyphs (glyphs has old index as (Integer) key and
     * new index as (Integer) value)
     * @param cid is cid
     * @throws IOException in case of an I/O problem
     */
    public void readFont(InputStream is, String name, FontContainer fontContainer,
                         Map<Integer, Integer> subsetGlyphs, boolean cid) throws IOException {
        this.cid = cid;
        if (subsetGlyphs.isEmpty()) {
            return;
        }
        this.fontFile = new FontFileReader(is);
        size += fontFile.getAllBytes().length;

        readDirTabs();
        readFontHeader();
        getNumGlyphs();
        readHorizontalHeader();
        readHorizontalMetrics();
        readIndexToLocation();
        int sgsize = subsetGlyphs.size();
        if (!cid && subsetGlyphs.size() <= 1) {
            for (int i = 0; i < mtxTab.length; i++) {
                subsetGlyphs.put(i, i);
            }
        }
        scanGlyphs(fontFile, subsetGlyphs);
        readGlyf(subsetGlyphs, fontFile);
        if (nhmtxDiff == null) {
            nhmtxDiff = sgsize - nhmtx;
            if (nhmtxDiff < 0) {
                nhmtxDiff = 0;
            }
        }
    }

    protected void scanGlyphs(FontFileReader in, Map<Integer, Integer> subsetGlyphs) throws IOException {
        OFDirTabEntry glyfTableInfo = dirTabs.get(OFTableName.GLYF);
        if (glyfTableInfo == null) {
            throw new IOException("Glyf table could not be found");
        }
        MergeGlyfTable mergeGlyfTable = new MergeGlyfTable(in, mtxTab, glyfTableInfo, subsetGlyphs);
        composedGlyphs = mergeGlyfTable.getComposedGlyphs();
        compositeGlyphs = mergeGlyfTable.getCompositeGlyphs();
    }

    static class MergeGlyfTable extends GlyfTable {
        public MergeGlyfTable(FontFileReader in, OFMtxEntry[] metrics, OFDirTabEntry dirTableEntry,
                              Map<Integer, Integer> glyphs) throws IOException {
            super(in, metrics, dirTableEntry, glyphs);
            populateGlyphsWithComposites();
        }

        protected void populateGlyphsWithComposites() throws IOException {
            for (int indexInOriginal : subset.keySet()) {
                scanGlyphsRecursively(indexInOriginal);
            }
            addAllComposedGlyphsToSubset();
        }

        private void scanGlyphsRecursively(int indexInOriginal) throws IOException {
            if (!subset.containsKey(indexInOriginal)) {
                composedGlyphs.add(indexInOriginal);
            }
            if (isComposite(indexInOriginal)) {
                compositeGlyphs.add(indexInOriginal);
                Set<Integer> composedGlyphs = retrieveComposedGlyphs(indexInOriginal);
                for (Integer composedGlyph : composedGlyphs) {
                    scanGlyphsRecursively(composedGlyph);
                }
            }
        }

        Set<Integer> getComposedGlyphs() {
            return composedGlyphs;
        }

        Set<Integer> getCompositeGlyphs() {
            return compositeGlyphs;
        }

        @Override
        protected void addAllComposedGlyphsToSubset() {
            int newIndex = -1;
            for (int v : subset.values()) {
                if (v > newIndex) {
                    newIndex = v;
                }
            }
            for (int composedGlyph : composedGlyphs) {
                subset.put(composedGlyph, ++newIndex);
            }
        }
    }

    private void reorderGlyphs() throws IOException {
        Map<Integer, Integer> remap = new HashMap<>();
        Map<Integer, Glyph> glyphMap = new TreeMap<>();
        int i = 0;
        for (Glyph glyph : added.values()) {
            if (!glyph.composed) {
                glyphMap.put(i, glyph);
                remap.put(glyph.origGlyphIndex, i);
                i++;
            }
        }
        for (Glyph glyph : added.values()) {
            if (glyph.composed) {
                glyphMap.put(i, glyph);
                remap.put(glyph.origGlyphIndex, i);
                i++;
            }
        }
        for (Glyph glyph : glyphMap.values()) {
            if (glyph.composite && glyph.data.length > 0) {
                remapComposite(glyph.data, remap);
            }
        }
        added = glyphMap;
    }

    private void remapComposite(byte[] data, Map<Integer, Integer> remap) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(bos);
        DataInputStream dis = new DataInputStream(new ByteArrayInputStream(data));
        byte[] header = new byte[10];
        read(dis, header);
        dos.write(header);
        int flags;
        do {
            flags = dis.readShort();
            dos.writeShort(flags);
            int glyphIndex = dis.readShort();
            int indexInSubset = remap.get(glyphIndex);
            dos.writeShort(indexInSubset);
            int skip = GlyfTable.GlyfFlags.getOffsetToNextComposedGlyf(flags);
            byte[] rest = new byte[skip];
            read(dis, rest);
            dos.write(rest);
        } while(GlyfTable.GlyfFlags.hasMoreComposites(flags));
        System.arraycopy(bos.toByteArray(), 0, data, 0, bos.size());
    }

    private void read(DataInputStream dis, byte[] data) throws IOException {
        int size = dis.read(data);
        assert size == data.length;
    }

    public byte[] getMergedFontSubset() throws IOException {
        int sgsize = added.size();
        if (sgsize == 1 && size == fontFile.getAllBytes().length) {
            return fontFile.getAllBytes();
        }
        reorderGlyphs();

        output = new byte[size * 2];
        createDirectory();     // Create the TrueType header and directory
        if (!cid) {
            writeCMAP(cmap);
//            copyTable(fontFile, OFTableName.CMAP);
        }
        createHmtx();           // Create hmtx table
        createLoca(sgsize);    // create empty loca table
        createHead(fontFile);
        createOS2(fontFile);                          // copy the OS/2 table
        if (!cid) {
            createHhea(fontFile, sgsize - nhmtxDiff);    // Create the hhea table
        } else {
            createHhea(fontFile, sgsize);    // Create the hhea table
        }
        if (maxp.getVersion() == 0) {
            createMaxp(fontFile, sgsize);    // copy the maxp table
        } else {
            writeMaxp();
        }
        createCvt(fontFile);    // copy the cvt table
        createFpgm(fontFile);    // copy fpgm table
        createPost(fontFile);                         // copy the post table
        createPrep(fontFile);    // copy prep table
        createName(fontFile);                         // copy the name table
        createGlyf(); //create glyf table and update loca table
        pad4();
        createCheckSumAdjustment();
        return getFontSubset();
    }

    private void writeMaxp() {
        int checksum = currentPos;
        pad4();
        int startPos = currentPos;
        writeUShort((int) maxp.getVersion()); //version
        writeUShort(0);
        writeUShort(added.size()); //numGlyphs
        writeUShort(maxp.getMaxPoints()); //maxPoints
        writeUShort(maxp.getMaxContours()); //maxContours
        writeUShort(maxp.getMaxCompositePoints()); //maxCompositePoints
        writeUShort(maxp.getMaxCompositeContours()); //maxCompositeContours
        writeUShort(maxp.getMaxZones()); //maxZones
        writeUShort(maxp.getMaxTwilightPoints()); //maxTwilightPoints
        writeUShort(maxp.getMaxStorage()); //maxStorage
        writeUShort(maxp.getMaxFunctionDefs()); //maxFunctionDefs
        writeUShort(maxp.getMaxInstructionDefs()); //maxInstructionDefs
        writeUShort(maxp.getMaxStackElements()); //maxStackElements
        writeUShort(maxp.getMaxSizeOfInstructions()); //maxSizeOfInstructions
        writeUShort(maxp.getMaxComponentElements()); //maxComponentElements
        writeUShort(maxp.getMaxComponentDepth()); //maxComponentDepth
        updateCheckSum(checksum, currentPos - startPos, OFTableName.MAXP);
        realSize += currentPos - startPos;
    }

    private void writeCMAP(List<Cmap> cmaps) {
        mergeUniCmap(cmaps);

        int checksum = currentPos;
        pad4();
        int cmapPos = currentPos;
        writeUShort(0); //version
        writeUShort(cmaps.size()); //number of tables

        int tablesSize = 8 * cmaps.size();
        for (int i = 0; i < cmaps.size(); i++) {
            Cmap cmap = cmaps.get(i);
            writeUShort(cmap.platformId); //platformid
            writeUShort(cmap.platformEncodingId); //platformEncodingId
            writeULong(currentPos, 4 + tablesSize + getCmapOffset(cmaps, i)); //subTableOffset
            currentPos += 4;
        }

        for (Cmap cmap : cmaps) {
            if (cmap.platformId != 0) {
                writeUShort(4); //subtableFormat
                int segCount = cmap.glyphIdToCharacterCode.size() + 1;
                writeUShort(16 + (segCount * 8)); //length
                writeUShort(0); //lang
                writeUShort(segCount * 2); //segCountX2
                double searchRange = Math.pow(2, Math.floor(logBase2(segCount))) * 2;
                writeUShort((int) searchRange); //searchRange
                double entrySelector = Math.floor(logBase2(segCount));
                writeUShort((int) entrySelector); //entrySelector
                double rangeShift = (segCount * 2) - searchRange;
                writeUShort((int) rangeShift); //rangeShift
                for (int c : cmap.glyphIdToCharacterCode.keySet()) {
                    writeUShort(c); //endCode
                }
                writeUShort(0xFFFF);
                writeUShort(0); //reservedPad
                for (int c : cmap.glyphIdToCharacterCode.keySet()) {
                    writeUShort(c); //startCode
                }
                writeUShort(0);
                for (Map.Entry<Integer, Integer> entry : cmap.glyphIdToCharacterCode.entrySet()) {
                    writeUShort(entry.getValue() - entry.getKey()); //idDelta
                }
                writeUShort(0);
                for (int g : cmap.glyphIdToCharacterCode.keySet()) {
                    writeUShort(0); //idRangeOffsets
                }
                writeUShort(0);
            } else {
                writeUShort(12); //subtableFormat
                writeUShort(0);
                writeULong(currentPos, (cmap.glyphIdToCharacterCode.size() * 12) + 16);
                currentPos += 4;
                writeULong(currentPos, 0);
                currentPos += 4;
                writeULong(currentPos, cmap.glyphIdToCharacterCode.size());
                currentPos += 4;

                for (Map.Entry<Integer, Integer> g : cmap.glyphIdToCharacterCode.entrySet()) {
                    writeULong(currentPos, g.getKey());
                    currentPos += 4;
                    writeULong(currentPos, g.getKey());
                    currentPos += 4;
                    writeULong(currentPos, g.getValue());
                    currentPos += 4;
                }
            }
        }

        updateCheckSum(checksum, currentPos - cmapPos, OFTableName.CMAP);
        realSize += currentPos - cmapPos;
    }

    private int logBase2(int n) {
        return (int)(Math.log(n) / Math.log(2));
    }

    private void mergeUniCmap(List<Cmap> cmaps) {
        Cmap uniCmap = null;
        for (Cmap cmap : cmaps) {
            if (cmap.platformId == 3 && cmap.platformEncodingId == 1) {
                uniCmap = cmap;
            }
        }
        if (uniCmap != null) {
            for (Cmap cmap : cmaps) {
                uniCmap.glyphIdToCharacterCode.putAll(cmap.glyphIdToCharacterCode);
            }
        }
    }

    private int getCmapOffset(List<Cmap> cmaps, int index) {
        int result = 0;
        for (int i = 0; i < index; i++) {
            Cmap curCmap = cmaps.get(i);
            if (curCmap.platformId != 0) {
                int segCount = curCmap.glyphIdToCharacterCode.size() + 1;
                result += 16 + (segCount * 8); //length
            } else {
                result += (curCmap.glyphIdToCharacterCode.size() * 12) + 16;
            }
        }
        return result;
    }

    /**
     * Get index from starting at lowest glyph position
     * @param glyphs map
     * @return index map
     */
    protected int[] buildSubsetIndexToOrigIndexMap(Map<Integer, Integer> glyphs) {
        int[] origIndexes = new int[glyphs.size()];
        int minIndex = Integer.MAX_VALUE;
        for (int glyph : glyphs.values()) {
            if (minIndex > glyph) {
                minIndex = glyph;
            }
        }
        for (Map.Entry<Integer, Integer> glyph : glyphs.entrySet()) {
            int origIndex = glyph.getKey();
            int subsetIndex = glyph.getValue() - minIndex;
            origIndexes[subsetIndex] = origIndex;
        }
        return origIndexes;
    }

    public static class Cmap {
        int platformId;
        int platformEncodingId;
        Map<Integer, Integer> glyphIdToCharacterCode = new TreeMap<Integer, Integer>();

        public Cmap(int platformID, int platformEncodingID) {
            this.platformId = platformID;
            this.platformEncodingId = platformEncodingID;
        }
    }
}
