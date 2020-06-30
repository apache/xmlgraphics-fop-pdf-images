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
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

import org.apache.commons.io.IOUtils;

import org.apache.pdfbox.cos.COSArray;
import org.apache.pdfbox.cos.COSBase;
import org.apache.pdfbox.cos.COSBoolean;
import org.apache.pdfbox.cos.COSDictionary;
import org.apache.pdfbox.cos.COSFloat;
import org.apache.pdfbox.cos.COSInteger;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.cos.COSNull;
import org.apache.pdfbox.cos.COSObject;
import org.apache.pdfbox.cos.COSStream;
import org.apache.pdfbox.cos.COSString;
import org.apache.pdfbox.pdmodel.common.COSObjectable;
import org.apache.pdfbox.pdmodel.common.PDStream;

import org.apache.fop.pdf.PDFArray;
import org.apache.fop.pdf.PDFDictionary;
import org.apache.fop.pdf.PDFDocument;
import org.apache.fop.pdf.PDFName;
import org.apache.fop.pdf.PDFNumber;
import org.apache.fop.pdf.PDFObject;
import org.apache.fop.pdf.PDFStream;

public class PDFCloner {
    private PDFBoxAdapter adapter;

    PDFCloner(PDFBoxAdapter adapter) {
        this.adapter = adapter;
    }

    protected Object cloneForNewDocument(Object base) throws IOException {
        return cloneForNewDocument(base, base);
    }

    protected Object cloneForNewDocument(Object base, Object keyBase) throws IOException {
        return cloneForNewDocument(base, keyBase, Collections.EMPTY_LIST);
    }

    protected Object cloneForNewDocument(Object base, Object keyBase, Collection exclude) throws IOException {
        if (base == null) {
            return null;
        }
        Object cached = adapter.getCachedClone(keyBase);
        if (cached != null) {
            // we are done, it has already been converted.
            return cached;
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
//        } else if (base instanceof COSStreamArray) {
//            COSStreamArray array = (COSStreamArray)base;
//            PDFArray newArray = new PDFArray();
//            cacheClonedObject(keyBase, newArray);
//            for (int i = 0, c = array.getStreamCount(); i < c; i++) {
//                newArray.add(cloneForNewDocument(array.get(i)));
//            }
//            return newArray;
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

    protected Object readCOSObject(COSObject object, Collection exclude) throws IOException {
        Object obj = cloneForNewDocument(object.getObject(), object, exclude);
        if (obj instanceof PDFObject) {
            PDFObject pdfobj = (PDFObject)obj;
            //pdfDoc.registerObject(pdfobj);
            if (!pdfobj.hasObjectNumber()) {
                throw new IllegalStateException("PDF object was not registered!");
            }
        }
        return obj;
    }

    private PDFDictionary readCOSDictionary(COSDictionary dic, Object keyBase, Collection exclude) throws IOException {
        PDFDictionary newDict = new PDFDictionary();
        cacheClonedObject(keyBase, newDict);
        for (Map.Entry<COSName, COSBase> e : dic.entrySet()) {
            if (!exclude.contains(e.getKey())) {
                String name = e.getKey().getName();
                if (adapter.uniqueName != null) {
                    name = adapter.uniqueName.getName(e.getKey());
                }
                newDict.put(name, cloneForNewDocument(e.getValue(), e.getValue(), exclude));
            }
        }
        return newDict;
    }

    private Object readCOSString(COSString string, Object keyBase) throws IOException {
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
        if (adapter.pdfDoc.isEncryptionActive()
                || (originalStream.containsKey(COSName.DECODE_PARMS) && !originalStream.containsKey(COSName.FILTER))) {
            in = originalStream.getUnfilteredStream();
            filter = adapter.FILTER_FILTER;
        } else {
            //transfer encoded data (don't reencode)
            in = originalStream.getFilteredStream();
            filter = Collections.EMPTY_SET;
        }
        PDFStream stream = new PDFStream();
        OutputStream out = stream.getBufferOutputStream();
        if (originalStream.getItem(COSName.SUBTYPE) == COSName.FORM && adapter.uniqueName != null) {
            PDFWriter writer = new PDFWriter(adapter.uniqueName, adapter.currentMCID);
            try {
                String newStream = writer.writeText(new PDStream(originalStream));
                if (writer.keyUsed) {
                    filter = adapter.FILTER_FILTER;
                    out.write(newStream.getBytes(PDFDocument.ENCODING));
                    out.close();
                    in = null;
                }
            } catch (IOException e) {
                //ignore
            }
        }
        if (in != null) {
            IOUtils.copyLarge(in, out);
        }
        adapter.transferDict(originalStream, stream, filter);
        return cacheClonedObject(keyBase, stream);
    }

    protected Object cacheClonedObject(Object base, Object cloned) throws IOException {
        Object key = PDFBoxAdapterUtil.getBaseKey(base);
        if (key == null) {
            return cloned;
        }
        PDFObject pdfobj = (PDFObject) cloned;
        if (pdfobj != null && !pdfobj.hasObjectNumber() && !(base instanceof COSDictionary)) {
            adapter.pdfDoc.registerObject(pdfobj);
        }
        adapter.clonedVersion.put(key, cloned);
        if (key instanceof Integer) {
            adapter.objectCache.put(key, cloned);
        }
        return cloned;
    }
}
