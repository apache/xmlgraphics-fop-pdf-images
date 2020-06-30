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

import java.io.IOException;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;

import org.apache.commons.io.output.CountingOutputStream;

import org.apache.fop.pdf.PDFDocument;
import org.apache.fop.pdf.PDFObject;
import org.apache.fop.pdf.PDFText;

/**
 * Special PDF object for strings that goes beyond FOP's {@link PDFText} class.
 */
public class PDFString extends PDFObject {

    private String text;
    private byte[] binary;

    /**
     * Creates a new String.
     * @param text the text
     */
    public PDFString(String text) {
        this.text = text;
    }

    /**
     * Creates a new String.
     * @param data the text data as byte array
     */
    public PDFString(byte[] data) {
        this.binary = data.clone();
    }

    /**
     * Returns the string as a Unicode string.
     * @return the string
     */
    public String getString() {
        if (this.text == null) {
            String encoding = PDFDocument.ENCODING;
            int start = 0;
            if (this.binary.length > 2) {
                if (this.binary[0] == (byte)0xFF && this.binary[1] == (byte)0xFE) {
                    encoding = "UTF-16LE";
                    start = 2;
                } else if (this.binary[0] == (byte)0xFE && this.binary[1] == (byte)0xFF) {
                    encoding = "UTF-16BE";
                    start = 2;
                }
            }
            try {
                this.text = new String(this.binary, start, this.binary.length - start, encoding);
            } catch (UnsupportedEncodingException e) {
                throw new RuntimeException("Incompatible JVM: " + e.getMessage());
            }
        }
        return this.text;
    }

    /**
     * Returns the string as binary data.
     * @return the binary representation
     */
    public byte[] getBinary() {
        if (this.binary == null) {
            boolean unicode16 = false;
            char[] chars = this.text.toCharArray();
            int length = chars.length;
            for (int i = 0; i < length; i++) {
                if (chars[i] > 255) {
                    unicode16 = true;
                    break;
                }
            }
            try {
                byte[] binary;
                if (unicode16) {
                    byte[] data = this.text.getBytes("UTF-16BE");
                    binary = new byte[data.length + 2];
                    binary[0] = (byte)0xFE;
                    binary[1] = (byte)0xFF;
                    System.arraycopy(data, 0, binary, 2, data.length);
                } else {
                    byte[] data = this.text.getBytes(PDFDocument.ENCODING);
                    binary = new byte[data.length];
                    System.arraycopy(data, 0, binary, 0, data.length);
                }
            } catch (UnsupportedEncodingException e) {
                throw new RuntimeException("Incompatible JVM: " + e.getMessage());
            }
        }
        if (binary == null) {
            return new byte[0];
        }
        return this.binary.clone();
    }

    @Override
    public int output(OutputStream stream) throws IOException {
        CountingOutputStream cout = new CountingOutputStream(stream);
        PDFDocument.flushTextBuffer(new StringBuilder(PDFText.escapeText(getString())), cout);
        return cout.getCount();
    }

    /**
     * Indicates whether the given binary data contains only US-ASCII characters.
     * @param data the binary data
     * @return true if only US-ASCII data is found
     */
    public static boolean isUSASCII(byte[] data) {
        for (int i = 0, c = data.length; i < c; i++) {
            if ((data[i] & 0xFF) >= 128) {
                return false;
            }
        }
        return true;
    }
}
