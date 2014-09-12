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
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.pdfbox.cos.COSArray;
import org.apache.pdfbox.cos.COSBase;
import org.apache.pdfbox.cos.COSBoolean;
import org.apache.pdfbox.cos.COSDictionary;
import org.apache.pdfbox.cos.COSFloat;
import org.apache.pdfbox.cos.COSInteger;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.cos.COSString;
import org.apache.pdfbox.pdfparser.PDFStreamParser;
import org.apache.pdfbox.pdmodel.common.PDStream;
import org.apache.pdfbox.util.operator.PDFOperator;

public class PDFWriter {
    protected StringBuilder s = new StringBuilder();
    private String key;
    private List<COSName> resourceNames;

    public PDFWriter(String key, List<COSName> resourceNames) {
        this.key = key;
        this.resourceNames = resourceNames;
    }

    public String writeText(PDStream pdStream) throws IOException {
        Iterator<Object> it = new PDFStreamParser(pdStream).getTokenIterator();
        List<COSBase> arguments = new ArrayList<COSBase>();
        while (it.hasNext()) {
            Object o = it.next();
            if (o instanceof PDFOperator) {
                PDFOperator op = (PDFOperator)o;
                readPDFArguments(op, arguments);
                s.append(op.getOperation() + "\n");
                arguments.clear();
                if (op.getImageParameters() != null) {
                    for (Map.Entry<COSName, COSBase> cn : op.getImageParameters().entrySet()) {
                        arguments.add(cn.getKey());
                        arguments.add(cn.getValue());
                    }
                    readPDFArguments(op, arguments);
                    s.append("ID " + new String(op.getImageData(), "ISO-8859-1"));
                    arguments.clear();
                    s.append("EI\n");
                }
            } else {
                arguments.add((COSBase)o);
            }
        }
        return s.toString();
    }

    protected void readPDFArguments(PDFOperator op, Collection<COSBase> arguments) throws IOException {
        for (COSBase c : arguments) {
            processArg(op, c);
        }
    }

    protected void processArg(PDFOperator op, COSBase c) throws IOException {
        if (c instanceof COSInteger) {
            s.append(((COSInteger) c).intValue());
            s.append(" ");
        } else if (c instanceof COSFloat) {
            float f = ((COSFloat) c).floatValue();
            s.append(new DecimalFormat("#.####").format(f));
            s.append(" ");
        } else if (c instanceof COSName) {
            COSName cn = (COSName)c;
            s.append("/" + cn.getName());
            addKey(cn);
            s.append(" ");
        } else if (c instanceof COSString) {
            s.append("<" + ((COSString) c).getHexString() + ">");
        } else if (c instanceof COSArray) {
            s.append("[");
            readPDFArguments(op, (Collection<COSBase>) ((COSArray) c).toList());
            s.append("] ");
        } else if (c instanceof COSDictionary) {
            Collection<COSBase> dictArgs = new ArrayList<COSBase>();
            for (Map.Entry<COSName, COSBase> cn : ((COSDictionary)c).entrySet()) {
                dictArgs.add(cn.getKey());
                dictArgs.add(cn.getValue());
            }
            s.append("<<");
            readPDFArguments(op, dictArgs);
            s.append(">>");
        } else if (c instanceof COSBoolean) {
            s.append(((COSBoolean) c).getValue());
        } else {
            throw new IOException(c + " not supported");
        }
    }

    protected void addKey(COSName cn) {
        if (resourceNames.contains(cn)) {
            s.append(key);
        }
    }
}
