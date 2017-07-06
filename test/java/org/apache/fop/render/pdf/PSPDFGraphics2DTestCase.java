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
package org.apache.fop.render.pdf;

import java.io.IOException;
import java.io.OutputStream;

import org.junit.Assert;
import org.junit.Test;

import org.apache.pdfbox.cos.COSArray;
import org.apache.pdfbox.cos.COSInteger;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.cos.COSStream;
import org.apache.pdfbox.pdmodel.common.function.PDFunction;
import org.apache.pdfbox.pdmodel.common.function.PDFunctionType0;

import org.apache.fop.render.gradient.Function;
import org.apache.fop.render.pdf.pdfbox.PSPDFGraphics2D;

public class PSPDFGraphics2DTestCase {

    @Test
    public void testShading() throws IOException {
        COSStream stream = new COSStream();
        OutputStream streamData = stream.createOutputStream();
        streamData.write("test".getBytes("UTF-8"));
        streamData.close();
        stream.setItem(COSName.BITS_PER_SAMPLE, COSInteger.get(8));
        stream.setItem(COSName.FUNCTION_TYPE, COSInteger.ZERO);
        COSArray range = new COSArray();
        range.add(COSInteger.ZERO);
        range.add(COSInteger.ONE);
        range.add(COSInteger.ZERO);
        range.add(COSInteger.ONE);
        range.add(COSInteger.ZERO);
        range.add(COSInteger.ONE);
        stream.setItem(COSName.RANGE, range);
        stream.setItem(COSName.DOMAIN, range);
        COSArray size = new COSArray();
        size.add(COSInteger.ONE);
        stream.setItem(COSName.SIZE, size);

        Function f = new MyPSPDFGraphics2D().getAFunction(new PDFunctionType0(stream));
        Assert.assertEquals(f.getBitsPerSample(), 8);
    }

    static class MyPSPDFGraphics2D extends PSPDFGraphics2D {
        MyPSPDFGraphics2D() {
            super(false);
        }

        Function getAFunction(PDFunction function) throws IOException {
            return getFunction(function);
        }
    }
}
