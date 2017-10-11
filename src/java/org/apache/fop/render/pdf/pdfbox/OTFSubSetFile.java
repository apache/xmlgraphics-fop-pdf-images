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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.apache.fop.fonts.cff.CFFDataReader;
import org.apache.fop.fonts.cff.CFFDataReader.DICTEntry;
import org.apache.fop.fonts.truetype.OTFSubSetWriter;

/**
 * Reads an OpenType CFF file and generates a subset
 * The OpenType specification can be found at the Microsoft
 * Typography site: http://www.microsoft.com/typography/otspec/
 */
public abstract class OTFSubSetFile extends OTFSubSetWriter {

    /** A map of the new GID to SID used to construct the charset table **/
    protected Map<Integer, Integer> gidToSID;

    /** List of subroutines to write to the local / global indexes in the subset font **/
    protected List<byte[]> subsetLocalIndexSubr;


    /** A list of unique subroutines from the global / local subroutine indexes */
    protected List<Integer> localUniques;
    protected List<Integer> globalUniques;

    /** A list of char string data for each glyph to be stored in the subset font **/
    protected List<byte[]> subsetCharStringsIndex;

    /** The embedded name to change in the name table **/
    protected String embeddedName;

    /** An array used to hold the string index data for the subset font **/
    protected List<byte[]> stringIndexData = new ArrayList<byte[]>();

    /** The CFF reader object used to read data and offsets from the original font file */
    protected CFFDataReader cffReader;

    /** The number of standard strings in CFF **/
    public static final int NUM_STANDARD_STRINGS = 391;

    public OTFSubSetFile() throws IOException {
        super();
    }

    protected int writeTopDICT() throws IOException {
        Map<String, DICTEntry> topDICT = cffReader.getTopDictEntries();
        List<String> topDictStringEntries = Arrays.asList("version", "Notice", "Copyright",
                "FullName", "FamilyName", "Weight", "PostScript");
        ByteArrayOutputStream dict = new ByteArrayOutputStream();
        for (Map.Entry<String, DICTEntry> dictEntry : topDICT.entrySet()) {
            String dictKey = dictEntry.getKey();
            DICTEntry entry = dictEntry.getValue();
            //If the value is an SID, update the reference but keep the size the same
            if (dictKey.equals("ROS")) {
                dict.write(writeROSEntry(entry));
            } else if (dictKey.equals("CIDCount")) {
                dict.write(writeCIDCount(entry));
            } else if (topDictStringEntries.contains(dictKey)) {
                dict.write(writeTopDictStringEntry(entry));
            } else {
                dict.write(entry.getByteData());
            }
        }
        byte[] topDictIndex = cffReader.getTopDictIndex().getByteData();
        int offSize = topDictIndex[2];
        return writeIndex(Arrays.asList(dict.toByteArray()), offSize) - dict.size();
    }

    private byte[] writeROSEntry(DICTEntry dictEntry) throws IOException {
        int sidA = dictEntry.getOperands().get(0).intValue();
        if (sidA > 390) {
            stringIndexData.add(cffReader.getStringIndex().getValue(sidA - NUM_STANDARD_STRINGS));
        }
        int sidAStringIndex = stringIndexData.size() + 390;
        int sidB = dictEntry.getOperands().get(1).intValue();
        if (sidB > 390) {
            stringIndexData.add("Identity".getBytes("UTF-8"));
        }
        int sidBStringIndex = stringIndexData.size() + 390;
        byte[] cidEntryByteData = dictEntry.getByteData();
        updateOffset(cidEntryByteData, 0, dictEntry.getOperandLengths().get(0),
                sidAStringIndex);
        updateOffset(cidEntryByteData, dictEntry.getOperandLengths().get(0),
                dictEntry.getOperandLengths().get(1), sidBStringIndex);
        updateOffset(cidEntryByteData, dictEntry.getOperandLengths().get(0)
                + dictEntry.getOperandLengths().get(1), dictEntry.getOperandLengths().get(2), 139);
        return cidEntryByteData;
    }

    protected byte[] writeCIDCount(DICTEntry dictEntry) throws IOException {
        return dictEntry.getByteData();
    }

    private byte[] writeTopDictStringEntry(DICTEntry dictEntry) throws IOException {
        int sid = dictEntry.getOperands().get(0).intValue();
        if (sid > 391) {
            stringIndexData.add(cffReader.getStringIndex().getValue(sid - 391));
        }

        byte[] newDictEntry = createNewRef(stringIndexData.size() + 390, dictEntry.getOperator(),
                dictEntry.getOperandLength());
        return newDictEntry;
    }

    public static byte[] createNewRef(int newRef, int[] operatorCode, int forceLength) {
        ByteArrayOutputStream newRefBytes = new ByteArrayOutputStream();
        if ((forceLength == -1 && newRef <= 107) || forceLength == 1) {
            //The index values are 0 indexed
            newRefBytes.write(newRef + 139);
            for (int i : operatorCode) {
                newRefBytes.write(i);
            }
        } else if ((forceLength == -1 && newRef <= 1131) || forceLength == 2) {
            if (newRef <= 363) {
                newRefBytes.write(247);
            } else if (newRef <= 619) {
                newRefBytes.write(248);
            } else if (newRef <= 875) {
                newRefBytes.write(249);
            } else {
                newRefBytes.write(250);
            }
            newRefBytes.write(newRef - 108);
            for (int i : operatorCode) {
                newRefBytes.write(i);
            }
        } else if ((forceLength == -1 && newRef <= 32767) || forceLength == 3) {
            newRefBytes.write(28);
            newRefBytes.write(newRef >> 8);
            newRefBytes.write(newRef);
            for (int i : operatorCode) {
                newRefBytes.write(i);
            }
        } else {
            newRefBytes.write(29);
            newRefBytes.write(newRef >> 24);
            newRefBytes.write(newRef >> 16);
            newRefBytes.write(newRef >> 8);
            newRefBytes.write(newRef);
            for (int i : operatorCode) {
                newRefBytes.write(i);
            }
        }
        return newRefBytes.toByteArray();
    }

    protected int writeIndex(List<byte[]> dataArray) {
        int totLength = 1;
        for (byte[] aDataArray1 : dataArray) {
            totLength += aDataArray1.length;
        }
        int offSize = getOffSize(totLength);
        return writeIndex(dataArray, offSize);
    }

    protected int writeIndex(List<byte[]> dataArray, int offSize) {
        int hdrTotal = 3;
        //2 byte number of items
        this.writeCard16(dataArray.size());
        //Offset Size: 1 byte = 256, 2 bytes = 65536 etc.
        //Offsets in the offset array are relative to the byte that precedes the object data.
        //Therefore the first element of the offset array is always 1.
        this.writeByte(offSize);
        //Count the first offset 1
        hdrTotal += offSize;
        int total = 0;
        for (int i = 0; i < dataArray.size(); i++) {
            hdrTotal += offSize;
            int length = dataArray.get(i).length;
            switch (offSize) {
                case 1:
                    if (i == 0) {
                        writeByte(1);
                    }
                    total += length;
                    writeByte(total + 1);
                    break;
                case 2:
                    if (i == 0) {
                        writeCard16(1);
                    }
                    total += length;
                    writeCard16(total + 1);
                    break;
                case 3:
                    if (i == 0) {
                        writeThreeByteNumber(1);
                    }
                    total += length;
                    writeThreeByteNumber(total + 1);
                    break;
                case 4:
                    if (i == 0) {
                        writeULong(1);
                    }
                    total += length;
                    writeULong(total + 1);
                    break;
                default:
                    throw new AssertionError("Offset Size was not an expected value.");
            }
        }
        for (byte[] aDataArray : dataArray) {
            writeBytes(aDataArray);
        }
        return hdrTotal + total;
    }

    private int getOffSize(int totLength) {
        int offSize = 1;
        if (totLength < (1 << 8)) {
            offSize = 1;
        } else if (totLength < (1 << 16)) {
            offSize = 2;
        } else if (totLength < (1 << 24)) {
            offSize = 3;
        } else {
            offSize = 4;
        }
        return offSize;
    }

    protected void writePrivateDict() throws IOException {
        Map<String, DICTEntry> topDICT = cffReader.getTopDictEntries();

        DICTEntry privateEntry = topDICT.get("Private");
        if (privateEntry != null) {
            writeBytes(cffReader.getPrivateDictBytes(privateEntry));
        }
    }

    protected void updateOffsets(Offsets offsets) throws IOException {
        Map<String, DICTEntry> topDICT = cffReader.getTopDictEntries();
        Map<String, DICTEntry> privateDICT = null;

        DICTEntry privateEntry = topDICT.get("Private");
        if (privateEntry != null) {
            privateDICT = cffReader.getPrivateDict(privateEntry);
        }

        updateFixedOffsets(topDICT, offsets);

        if (privateDICT != null) {
            //Private index offset in the top dict
            int oldPrivateOffset = offsets.topDictData + privateEntry.getOffset();
            updateOffset(oldPrivateOffset + privateEntry.getOperandLengths().get(0),
                    privateEntry.getOperandLengths().get(1), offsets.privateDict);

            //Update the local subroutine index offset in the private dict
            DICTEntry subroutines = privateDICT.get("Subrs");
            if (subroutines != null) {
                int oldLocalSubrOffset = offsets.privateDict + subroutines.getOffset();
                //Value needs to be converted to -139 etc.
                int encodeValue = 0;
                if (subroutines.getOperandLength() == 1) {
                    encodeValue = 139;
                }
                updateOffset(oldLocalSubrOffset, subroutines.getOperandLength(),
                        (offsets.localIndex - offsets.privateDict) + encodeValue);
            }
        }
    }

    static class Offsets {
        Integer topDictData;
        Integer encoding;
        Integer fdSelect;
        Integer charString;
        Integer fdArray;
        Integer privateDict;
        Integer localIndex;
    }

    protected abstract void updateFixedOffsets(Map<String, DICTEntry> topDICT, Offsets offsets) throws IOException;

    protected void updateCIDOffsets(Offsets offsets) throws IOException {
        Map<String, DICTEntry> topDict = cffReader.getTopDictEntries();

        DICTEntry fdArrayEntry = topDict.get("FDArray");
        if (fdArrayEntry != null) {
            updateOffset(offsets.topDictData + fdArrayEntry.getOffset() - 1,
                    fdArrayEntry.getOperandLength(), offsets.fdArray);
        }

        DICTEntry fdSelect = topDict.get("FDSelect");
        if (fdSelect != null) {
            updateOffset(offsets.topDictData + fdSelect.getOffset() - 1,
                    fdSelect.getOperandLength(), offsets.fdSelect);
        }

        updateFixedOffsets(topDict, offsets);
    }

    protected void updateOffset(int position, int length, int replacement) throws IOException {
        byte[] out = output.toByteArray();
        updateOffset(out, position, length, replacement);
        output.reset();
        output.write(out);
    }

    protected void updateOffset(byte[] out, int position, int length, int replacement) {
        switch (length) {
            case 1:
                out[position] = (byte)(replacement & 0xFF);
                break;
            case 2:
                assert replacement <= 1131;
                if (replacement <= 363) {
                    out[position] = (byte)247;
                } else if (replacement <= 619) {
                    out[position] = (byte)248;
                } else if (replacement <= 875) {
                    out[position] = (byte)249;
                } else {
                    out[position] = (byte)250;
                }
                out[position + 1] = (byte)(replacement - 108);
                break;
            case 3:
                assert replacement <= 32767;
                out[position] = (byte)28;
                out[position + 1] = (byte)((replacement >> 8) & 0xFF);
                out[position + 2] = (byte)(replacement & 0xFF);
                break;
            case 5:
                out[position] = (byte)29;
                out[position + 1] = (byte)((replacement >> 24) & 0xFF);
                out[position + 2] = (byte)((replacement >> 16) & 0xFF);
                out[position + 3] = (byte)((replacement >> 8) & 0xFF);
                out[position + 4] = (byte)(replacement & 0xFF);
                break;
            default:
        }
    }
}
