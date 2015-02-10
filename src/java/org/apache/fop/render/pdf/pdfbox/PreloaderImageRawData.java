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

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;

import javax.xml.transform.Source;

import org.apache.pdfbox.io.IOUtils;

import org.apache.xmlgraphics.image.loader.ImageContext;
import org.apache.xmlgraphics.image.loader.ImageException;
import org.apache.xmlgraphics.image.loader.ImageInfo;
import org.apache.xmlgraphics.image.loader.ImageSize;
import org.apache.xmlgraphics.image.loader.ImageSource;
import org.apache.xmlgraphics.image.loader.impl.AbstractImagePreloader;
import org.apache.xmlgraphics.image.loader.impl.ImageRendered;

public class PreloaderImageRawData extends AbstractImagePreloader {
    public ImageInfo preloadImage(String s, Source source, ImageContext imageContext)
        throws ImageException, IOException {
        if (source instanceof ImageSource && s.contains(DataBufferInt.class.getName())) {
            InputStream is = ((ImageSource)source).getInputStream();
            byte[] input = IOUtils.toByteArray(is);
            is.reset();
            IntBuffer intBuf = ByteBuffer.wrap(input).order(ByteOrder.BIG_ENDIAN).asIntBuffer();
            int[] array = new int[intBuf.remaining()];
            intBuf.get(array);
            int width = array[0];
            int height = array[1];
            ImageInfo info = new ImageInfo(s, "image/DataBufferInt");
            ImageSize size = new ImageSize(width, height, imageContext.getSourceResolution());
            size.calcSizeFromPixels();
            info.setSize(size);
            BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
            DataBufferInt db = (DataBufferInt) img.getRaster().getDataBuffer();
            System.arraycopy(array, 2, db.getData(), 0, db.getData().length);
            info.getCustomObjects().put(ImageInfo.ORIGINAL_IMAGE, new ImageRendered(info, img, null));
            return info;
        }
        return null;
    }
}
