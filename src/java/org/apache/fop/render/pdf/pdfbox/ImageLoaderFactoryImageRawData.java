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
import java.util.Map;

import org.apache.xmlgraphics.image.loader.Image;
import org.apache.xmlgraphics.image.loader.ImageException;
import org.apache.xmlgraphics.image.loader.ImageFlavor;
import org.apache.xmlgraphics.image.loader.ImageInfo;
import org.apache.xmlgraphics.image.loader.ImageSessionContext;
import org.apache.xmlgraphics.image.loader.impl.AbstractImageLoaderFactory;
import org.apache.xmlgraphics.image.loader.spi.ImageLoader;

public class ImageLoaderFactoryImageRawData extends AbstractImageLoaderFactory {
    public String[] getSupportedMIMETypes() {
        String[] mimes = {"image/DataBufferInt"};
        return mimes;
    }

    public ImageFlavor[] getSupportedFlavors(String s) {
        ImageFlavor[] imageFlavors = {ImageFlavor.BUFFERED_IMAGE};
        return imageFlavors;
    }

    public ImageLoader newImageLoader(ImageFlavor imageFlavor) {
        return new RawImageLoader();
    }

    static class RawImageLoader implements ImageLoader {

        public Image loadImage(ImageInfo imageInfo, Map map, ImageSessionContext imageSessionContext)
            throws ImageException, IOException {
            return imageInfo.getOriginalImage();
        }

        public Image loadImage(ImageInfo imageInfo, ImageSessionContext imageSessionContext)
            throws ImageException, IOException {
            return null;
        }

        public ImageFlavor getTargetFlavor() {
            return ImageFlavor.BUFFERED_IMAGE;
        }

        public int getUsagePenalty() {
            return 0;
        }
    }

    public boolean isAvailable() {
        return true;
    }
}
