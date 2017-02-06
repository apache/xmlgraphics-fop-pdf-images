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

import java.util.Collections;
import java.util.List;

import org.apache.commons.io.IOUtils;
import org.apache.fontbox.cmap.CMap;
import org.apache.fontbox.cmap.CMapParser;
import org.apache.fontbox.util.BoundingBox;
import org.apache.pdfbox.cos.COSArray;
import org.apache.pdfbox.cos.COSBase;
import org.apache.pdfbox.cos.COSDictionary;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.cos.COSStream;
import org.apache.pdfbox.pdmodel.common.COSArrayList;
import org.apache.pdfbox.pdmodel.font.PDCIDFont;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDFontFactory;
import org.apache.pdfbox.pdmodel.font.PDSimpleFont;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.pdfbox.pdmodel.font.encoding.BuiltInEncoding;
import org.apache.pdfbox.pdmodel.font.encoding.DictionaryEncoding;
import org.apache.pdfbox.pdmodel.font.encoding.Encoding;

public class FontContainer {
    private COSDictionary dict;
    private List<Integer> widths;
    PDFont font;

    FontContainer(COSDictionary fontData) throws IOException {
        dict = fontData;
        font = PDFontFactory.createFont(fontData);
    }

    int getFirstChar() {
        return dict.getInt(COSName.FIRST_CHAR);
    }

    int getLastChar() {
        return dict.getInt(COSName.LAST_CHAR);
    }

    CMap getToUnicodeCMap() throws IOException {
        COSBase base = dict.getDictionaryObject(COSName.TO_UNICODE);
        if (font instanceof PDType0Font && base == null) {
            PDCIDFont cidFont = ((PDType0Font) font).getDescendantFont();
            base = cidFont.getCOSObject().getDictionaryObject(COSName.TO_UNICODE);
        }
        if (base instanceof COSName) {
            // predefined CMap
            String name = ((COSName)base).getName();
            CMapParser parser = new CMapParser();
            return parser.parsePredefined(name);
        } else if (base instanceof COSStream) {
            // embedded CMap
            InputStream input = null;
            try {
                input = ((COSStream)base).getUnfilteredStream();
                CMapParser parser = new CMapParser();
                return parser.parse(input);
            } finally {
                IOUtils.closeQuietly(input);
            }
        } else {
//            throw new IOException("Expected Name or Stream");
        }
        return null;
    }

    COSBase getToUnicode() {
        return dict.getDictionaryObject(COSName.TO_UNICODE);
    }

    List<Integer> getWidths() {
        if (widths == null) {
            COSArray array = (COSArray) dict.getDictionaryObject(COSName.WIDTHS);
            if (array != null) {
                widths = COSArrayList.convertIntegerCOSArrayToList(array);
            } else {
                widths = Collections.emptyList();
            }
        }
        return widths;
    }

    Encoding getEncoding() {
        if (font instanceof PDSimpleFont) {
            if (((PDSimpleFont) font).getEncoding() instanceof DictionaryEncoding) {
                return new DictionaryEncoding(
                        (COSDictionary)((PDSimpleFont)font).getEncoding().getCOSObject(), true, null);
            }
            return ((PDSimpleFont) font).getEncoding();
        }
        return null;
    }

    String getBaseEncodingName() {
        Encoding encoding = getEncoding();
        if (encoding != null && !(encoding instanceof BuiltInEncoding)) {
            COSBase cosObject = encoding.getCOSObject();
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

    float[] getBoundingBox() throws IOException {
        BoundingBox bb = font.getBoundingBox();
        return new float[] {bb.getLowerLeftX(), bb.getLowerLeftY(), bb.getUpperRightX(), bb.getUpperRightY()};
    }

    public PDFont getFont() {
        return font;
    }
}
