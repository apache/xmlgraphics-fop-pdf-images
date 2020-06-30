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
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.apache.pdfbox.contentstream.operator.Operator;
import org.apache.pdfbox.cos.COSArray;
import org.apache.pdfbox.cos.COSBase;
import org.apache.pdfbox.cos.COSBoolean;
import org.apache.pdfbox.cos.COSDictionary;
import org.apache.pdfbox.cos.COSFloat;
import org.apache.pdfbox.cos.COSInteger;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.cos.COSNull;
import org.apache.pdfbox.cos.COSString;
import org.apache.pdfbox.pdfparser.PDFStreamParser;
import org.apache.pdfbox.pdmodel.common.PDStream;

import org.apache.fop.pdf.PDFDocument;

public class PDFWriter {
    private DecimalFormat df = new DecimalFormat("#.####", new DecimalFormatSymbols(Locale.US));
    private Map<Float, String> floatCache = new HashMap<Float, String>();
    protected StringBuilder s = new StringBuilder();
    protected UniqueName key;
    private int currentMCID;
    protected boolean keyUsed;

    public PDFWriter(UniqueName key, int currentMCID) {
        this.key = key;
        this.currentMCID = currentMCID;
    }

    public String writeText(PDStream pdStream) throws IOException {
        PDFStreamParser pdfStreamParser = new PDFStreamParser(pdStream);
        pdfStreamParser.parse();
        List<Object> it = pdfStreamParser.getTokens();
        List<COSBase> arguments = new ArrayList<COSBase>();
        for (Object o : it) {
            if (o instanceof Operator) {
                Operator op = (Operator)o;
                readPDFArguments(op, arguments);
                s.append(op.getName() + "\n");
                arguments.clear();
                if (op.getImageParameters() != null) {
                    for (Map.Entry<COSName, COSBase> cn : op.getImageParameters().entrySet()) {
                        arguments.add(cn.getKey());
                        arguments.add(cn.getValue());
                    }
                    readPDFArguments(op, arguments);
                    s.append("ID " + new String(op.getImageData(), PDFDocument.ENCODING));
                    arguments.clear();
                    s.append("EI\n");
                }
            } else {
                arguments.add((COSBase)o);
            }
        }
        return s.toString();
    }

    protected void readPDFArguments(Operator op, Collection<COSBase> arguments) throws IOException {
        for (COSBase c : arguments) {
            processArg(op, c);
        }
    }

    protected void processArg(Operator op, COSBase c) throws IOException {
        if (c instanceof COSInteger) {
            s.append(((COSInteger) c).intValue());
            s.append(" ");
        } else if (c instanceof COSFloat) {
            float f = ((COSFloat) c).floatValue();
            if (!floatCache.containsKey(f)) {
                addCache(f);
            }
            s.append(floatCache.get(f));
            s.append(" ");
            if (floatCache.size() > 1024) {
                floatCache.clear();
            }
        } else if (c instanceof COSName) {
            COSName cn = (COSName)c;
            String name = key.getName(cn);
            s.append("/" + name);
            s.append(" ");
            if (!name.equals(cn.getName())) {
                keyUsed = true;
            }
        } else if (c instanceof COSString) {
            s.append("<" + ((COSString) c).toHexString() + ">");
        } else if (c instanceof COSArray) {
            s.append("[");
            readPDFArguments(op, (Collection<COSBase>) ((COSArray) c).toList());
            s.append("] ");
        } else if (c instanceof COSDictionary) {
            Collection<COSBase> dictArgs = new ArrayList<COSBase>();
            if (currentMCID != 0 && op.getName().equals("BDC")) {
                for (Map.Entry<COSName, COSBase> cn : ((COSDictionary)c).entrySet()) {
                    if (cn.getKey().getName().equals("MCID")) {
                        updateMCID(cn, dictArgs);
                    } else {
                        dictArgs.add(cn.getKey());
                        dictArgs.add(cn.getValue());
                    }
                }
            } else {
                for (Map.Entry<COSName, COSBase> cn : ((COSDictionary)c).entrySet()) {
                    dictArgs.add(cn.getKey());
                    dictArgs.add(cn.getValue());
                }
            }
            s.append("<<");
            readPDFArguments(op, dictArgs);
            s.append(">>");
        } else if (c instanceof COSBoolean) {
            s.append(((COSBoolean) c).getValue()).append(" ");
        } else if (c instanceof COSNull) {
            s.append("null ");
        } else {
            throw new IOException(c + " not supported");
        }
    }

    protected void addCache(float f) {
        String formatted = df.format(f);
        floatCache.put(f, formatted);
    }

    private void updateMCID(Map.Entry<COSName, COSBase> cn, Collection<COSBase> dictArgs) {
        COSBase cosMCID = cn.getValue();
        assert cosMCID instanceof COSInteger;
        COSInteger mcid = (COSInteger) cosMCID;
        COSInteger updatedID = COSInteger.get(mcid.intValue() + currentMCID);
        dictArgs.add(cn.getKey());
        dictArgs.add(updatedID);
    }
}
