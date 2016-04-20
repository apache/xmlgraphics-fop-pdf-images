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
package org.apache.fop.render.pdf;

import java.awt.image.DataBufferInt;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;

import javax.imageio.ImageIO;
import javax.imageio.stream.MemoryCacheImageInputStream;

import org.junit.Test;

import org.apache.xmlgraphics.image.loader.ImageException;
import org.apache.xmlgraphics.image.loader.ImageInfo;
import org.apache.xmlgraphics.image.loader.ImageSource;
import org.apache.xmlgraphics.image.loader.impl.DefaultImageContext;
import org.apache.xmlgraphics.image.loader.impl.ImageRendered;

import org.apache.fop.render.pdf.pdfbox.PreloaderImageRawData;
import org.apache.fop.render.pdf.pdfbox.PreloaderPDF;

import junit.framework.Assert;

public class PreloaderPDFTestCase {

    @Test
    public void testPreloaderImageRawData() throws IOException, ImageException {
        PreloaderImageRawData p = new PreloaderImageRawData();
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(bos);
        dos.writeInt(1);
        dos.writeInt(1);
        dos.writeInt(1);
        InputStream is = new ByteArrayInputStream(bos.toByteArray());
        ImageSource src = new ImageSource(new MemoryCacheImageInputStream(is), "", true);
        ImageInfo img = p.preloadImage(DataBufferInt.class.getName(), src, new DefaultImageContext());
        Assert.assertTrue(img.getOriginalImage() instanceof ImageRendered);
    }

    @Test
    public void testPreloaderPDF() throws Exception {
        ImageSource imageSource = new ImageSource(
                ImageIO.createImageInputStream(new File(PDFBoxAdapterTestCase.ROTATE)), "", true);
        ImageInfo imageInfo = new PreloaderPDF().preloadImage("", imageSource, new DefaultImageContext());
        Assert.assertEquals(imageInfo.getMimeType(), "application/pdf");
    }
}
