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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.fontbox.encoding.Encoding;
import org.apache.fontbox.type1.Type1Font;
import org.apache.pdfbox.pdmodel.font.PDType1Font;

import org.apache.fop.fonts.type1.PFBData;
import org.apache.fop.fonts.type1.PFBParser;
import org.apache.fop.fonts.type1.PostscriptParser;
import org.apache.fop.fonts.type1.Type1SubsetFile;

public class MergeType1Fonts extends Type1SubsetFile implements MergeFonts {
    private Map<Integer, String> nameMap = new HashMap<Integer, String>();
    private PostscriptParser.PSElement encoding;
    private List<String> subsetEncodingEntries = new ArrayList<String>();
    private PFBData pfbData = null;
    private byte[] decoded;
    private PostscriptParser.PSElement charStrings;
    private ByteArrayOutputStream subrsBeforeStream = new ByteArrayOutputStream();
    private ByteArrayOutputStream subrsEndStream = new ByteArrayOutputStream();
    private Map<Integer, byte[]> subByteMap = new HashMap<Integer, byte[]>();

    public MergeType1Fonts() {
        uniqueSubs = new LinkedHashMap<Integer, byte[]>();
        subsetCharStrings = new HashMap<String, byte[]>();
        charNames = new ArrayList<String>();
        subsetSubroutines = false;
        subsetEncodingEntries.add("dup 0 /.notdef put");
    }

    public void readFont(InputStream fontFile, String name, FontContainer font,
                         Map<Integer, Integer> subsetGlyphs, boolean cid) throws IOException {
        PFBParser pfbParser = new PFBParser();
        pfbData = pfbParser.parsePFB(fontFile);

        PostscriptParser psParser = new PostscriptParser();
        List<Integer> glyphs = new ArrayList<Integer>();
        Type1Font t1f = ((PDType1Font)font.font).getType1Font();
        Encoding enc = t1f.getEncoding();
        for (int i = font.getFirstChar(); i <= font.getLastChar(); i++) {
            if (!enc.getName(i).equals(".notdef")) {
                nameMap.put(i, enc.getName(i));
                glyphs.add(i);
            }
        }
        Collections.sort(glyphs);

        headerSection = psParser.parse(pfbData.getHeaderSegment());
        encoding = getElement("/Encoding", headerSection);
        if (encoding.getFoundUnexpected()) {
            throw new IOException("unable to interpret postscript on arrays");
        }

        List<String> encodingEntries = readEncoding(glyphs, encoding);
        for (String e : encodingEntries) {
            if (e != null && !subsetEncodingEntries.contains(e)) {
                subsetEncodingEntries.add(e);
            }
        }

        decoded = BinaryCoder.decodeBytes(pfbData.getEncryptedSegment(), 55665, 4);
        mainSection = psParser.parse(decoded);
        PostscriptParser.PSFixedArray subroutines = (PostscriptParser.PSFixedArray)getElement("/Subrs", mainSection);
        charStrings = getElement("/CharStrings", mainSection);
        if (subroutines != null) {
            subrsBeforeStream.reset();
            subrsBeforeStream.write(decoded, 0, subroutines.getStartPoint());
            subrsEndStream.reset();
            subrsEndStream.write(decoded, subroutines.getEndPoint(),
                    charStrings.getStartPoint() - subroutines.getEndPoint());
        }
        List<byte[]> subArray = t1f.getSubrsArray();
        for (int i = 0; i < subArray.size(); i++) {
            if (subByteMap.containsKey(i) && !Arrays.equals(subByteMap.get(i), subArray.get(i))) {
                throw new IOException("Can't merge font subroutines " + font.font.getName());
            }
            subByteMap.put(i, subArray.get(i));
        }
        Map<String, byte[]> cs = t1f.getCharStringsDict();
        int lenIV = 4;
        PostscriptParser.PSElement element = getElement("/lenIV", mainSection);
        if (element != null && element instanceof PostscriptParser.PSVariable) {
            PostscriptParser.PSVariable lenIVVar = (PostscriptParser.PSVariable)element;
            lenIV = Integer.parseInt(lenIVVar.getValue());
        }
        for (String e : cs.keySet()) {
            int[] be = charStrings.getBinaryEntries().get("/" + e);
            if (be != null) {
                byte[] charStringEntry = getBinaryEntry(be, decoded);
                if (lenIV != 4) {
                    charStringEntry = BinaryCoder.decodeBytes(charStringEntry, 4330, lenIV);
                    charStringEntry = BinaryCoder.encodeBytes(charStringEntry, 4330, 4);
                }
                subsetCharStrings.put("/" + e, charStringEntry);
            }
        }
    }

    public byte[] getMergedFontSubset() throws IOException {
        ByteArrayOutputStream boasHeader = writeHeader(pfbData, encoding);

        ByteArrayOutputStream boasMain = writeMainSection(decoded, mainSection, charStrings);
        byte[] mainSectionBytes = BinaryCoder.encodeBytes(boasMain.toByteArray(), 55665, 4);
        boasMain.reset();
        boasMain.write(mainSectionBytes);

        ByteArrayOutputStream baosTrailer = new ByteArrayOutputStream();
        baosTrailer.write(pfbData.getTrailerSegment(), 0, pfbData.getTrailerSegment().length);

        return stitchFont(boasHeader, boasMain, baosTrailer);
    }

    @Override
    protected List<String> searchEntries(HashMap<Integer, String> encodingEntries, int glyph) {
        List<String> matches = new ArrayList<String>();
        for (Map.Entry<Integer, String> entry : encodingEntries.entrySet()) {
            String tag = getEntryPart(entry.getValue(), 3);
            String name = "/" + nameMap.get(glyph);
            if (name.equals(tag)) {
                matches.add(entry.getValue());
            }
        }
        return matches;
    }

    protected List<String> readEncoding(List<Integer> glyphs,
                                        PostscriptParser.PSElement encoding) {
        List<String> subsetEncodingEntries = new ArrayList<String>();
        //Handle custom encoding
        if (encoding instanceof PostscriptParser.PSFixedArray) {
            PostscriptParser.PSFixedArray encodingArray = (PostscriptParser.PSFixedArray)encoding;
            for (int glyph : glyphs) {
                if (glyph != 0) {
                    //Find matching entries in the encoding table from the given glyph
                    List<String> matches = searchEntries(encodingArray.getEntries(), glyph);
                    /* If no matches are found, perform a lookup using the glyph index.
                     * This isn't done by default as some symbol based type-1 fonts do not
                     * place their characters according to the glyph index. */
                    if (matches.isEmpty()) {
                        matches.clear();
                        matches.add(encodingArray.getEntries().get(glyph));
                    }
                    for (String match : matches) {
                        subsetEncodingEntries.add(match);
                    }
                }
            }
            //Handle fixed encoding
        } else if (encoding instanceof PostscriptParser.PSVariable) {
            if (((PostscriptParser.PSVariable) encoding).getValue().equals("StandardEncoding")) {
                standardEncoding = true;
                for (Map.Entry<Integer, String> v : nameMap.entrySet()) {
                    subsetEncodingEntries.add(String.format("dup %d /%s put", v.getKey(), v.getValue()));
                }
            } else {
                throw new RuntimeException(
                        "Only Custom or StandardEncoding is supported when creating a Type 1 subset.");
            }
        }
        return subsetEncodingEntries;
    }

    @Override
    protected ByteArrayOutputStream writeHeader(PFBData pfbData, PostscriptParser.PSElement encoding)
        throws IOException {
        ByteArrayOutputStream boasHeader = new ByteArrayOutputStream();
        boasHeader.write(pfbData.getHeaderSegment(), 0, encoding.getStartPoint() - 1);

        if (!standardEncoding) {
            //Write out the new encoding table for the subset font
            String encodingArray = eol + "/Encoding 256 array" + eol
                    + "0 1 255 {1 index exch /.notdef put } for" + eol;
            byte[] encodingDefinition = encodingArray.getBytes("ASCII");
            boasHeader.write(encodingDefinition, 0, encodingDefinition.length);
            for (Map.Entry<Integer, String> entry : nameMap.entrySet()) {
                String arrayEntry = String.format("dup %d /%s put", entry.getKey(),
                        entry.getValue());
                writeString(arrayEntry + eol, boasHeader);
            }
            writeString("readonly def" + eol, boasHeader);
        } else {
            String theEncoding = eol + "/Encoding StandardEncoding def" + eol;
            boasHeader.write(theEncoding.getBytes("ASCII"));
        }
        boasHeader.write(pfbData.getHeaderSegment(), encoding.getEndPoint(),
                pfbData.getHeaderSegment().length - encoding.getEndPoint());

        return boasHeader;
    }

    @Override
    protected ByteArrayOutputStream writeMainSection(byte[] decoded, List<PostscriptParser.PSElement> mainSection,
                                                     PostscriptParser.PSElement charStrings) throws IOException {
        ByteArrayOutputStream main = new ByteArrayOutputStream();
        //Find the ID of the three most commonly subroutines defined in Type 1 fonts
        String rd = findVariable(decoded, mainSection, new String[] {"string currentfile exch readstring pop"}, "RD");
        String nd = findVariable(decoded, mainSection, new String[] {"def", "noaccess def"}, "noaccess def");
        String np = findVariable(decoded, mainSection, new String[] {"put", "noaccess put"}, "noaccess put");
        main.write(subrsBeforeStream.toByteArray());
        writeString("/lenIV 4 def", main);
        writeString("/Subrs " + subByteMap.size() + " array" + eol, main);
        for (Map.Entry<Integer, byte[]> e : subByteMap.entrySet()) {
            if (e.getValue() != null) {
                byte[] encoded = BinaryCoder.encodeBytes(e.getValue(), 4330, 4);
                writeString("dup " + e.getKey() + " " + encoded.length + " " + rd + " ", main);
                main.write(encoded);
                writeString(" " + np + eol, main);
            }
        }
        writeString(nd + eol, main);
        main.write(subrsEndStream.toByteArray());
        //Write the subset charString array
        writeString(eol + String.format("/CharStrings %d dict dup begin",
                subsetCharStrings.size()), main);
        for (Map.Entry<String, byte[]> entry : subsetCharStrings.entrySet()) {
            writeString(eol + String.format("%s %d %s ", entry.getKey(),
                    entry.getValue().length, rd),
                    main);
            main.write(entry.getValue());
            writeString(" " + nd, main);
        }
        writeString(eol + "end", main);
        main.write(decoded, charStrings.getEndPoint(), decoded.length - charStrings.getEndPoint());

        return main;
    }
}
