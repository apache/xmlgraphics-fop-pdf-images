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

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.awt.image.ImageObserver;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.URI;
import java.util.Arrays;

import org.apache.xmlgraphics.image.loader.ImageInfo;
import org.apache.xmlgraphics.image.loader.ImageSize;
import org.apache.xmlgraphics.io.TempResourceURIGenerator;
import org.apache.xmlgraphics.java2d.ps.PSGraphics2D;
import org.apache.xmlgraphics.ps.PSGenerator;
import org.apache.xmlgraphics.ps.PSResource;

import org.apache.fop.render.ps.PSDocumentHandler;
import org.apache.fop.render.ps.PSImageUtils;

public class PSPDFGraphics2D extends PSGraphics2D {

    public PSPDFGraphics2D(boolean textAsShapes) {
        super(textAsShapes);
    }

    public PSPDFGraphics2D(PSGraphics2D g) {
        super(g);
    }

    public PSPDFGraphics2D(boolean textAsShapes, PSGenerator gen) {
        super(textAsShapes, gen);
    }

    @Override
    public boolean drawImage(Image img, int x1, int y1, ImageObserver observer) {
        PSGenerator tmp = gen;
        if (gen instanceof PSDocumentHandler.FOPPSGenerator) {
            PSDocumentHandler.FOPPSGenerator fopGen = (PSDocumentHandler.FOPPSGenerator)tmp;
            PSDocumentHandler handler = fopGen.getHandler();
            if (handler.getPSUtil().isOptimizeResources()) {
                try {
                    final int width = img.getWidth(observer);
                    final int height = img.getHeight(observer);
                    if (width == -1 || height == -1) {
                        return false;
                    }
                    BufferedImage buf = getImage(width, height, img, observer);
                    if (buf == null) {
                        return false;
                    }
                    ByteArrayOutputStream bos = new ByteArrayOutputStream();
                    DataBufferInt db = (DataBufferInt) buf.getRaster().getDataBuffer();
                    DataOutputStream dos = new DataOutputStream(bos);
                    dos.writeInt(width);
                    dos.writeInt(height);
                    for (int i : db.getData()) {
                        dos.writeInt(i);
                    }
                    String format = DataBufferInt.class.getName();
                    int hash = Arrays.hashCode(bos.toByteArray());
                    URI uri = fopGen.getImages().get(hash);
                    if (uri == null) {
                        uri = new TempResourceURIGenerator("img" + hash + "." + format).generate();
                        fopGen.getImages().put(hash, uri);
                        BufferedOutputStream outputStream = fopGen.getTempStream(uri);
                        outputStream.write(bos.toByteArray());
                        outputStream.close();
                    }
                    PSResource form = handler.getFormForImage(uri.toASCIIString());
                    ImageInfo info = new ImageInfo(uri.toASCIIString(), "image/" + format);
                    ImageSize size = new ImageSize(width, height, handler.getUserAgent().getTargetResolution());
                    size.calcSizeFromPixels();
                    info.setSize(size);
                    float res = handler.getUserAgent().getSourceResolution() / 72;
                    Rectangle rect =
                            new Rectangle(0, 0, (int)(size.getWidthMpt() * res), (int)(size.getHeightMpt() * res));
                    gen.saveGraphicsState();
                    gen.concatMatrix(getTransform());
                    writeClip(getClip());
                    PSImageUtils.drawForm(form, info, rect, gen);
                    gen.restoreGraphicsState();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
                return true;
            }
        }
        return super.drawImage(img, x1, y1, observer);
    }

    private BufferedImage getImage(int width, int height, Image img, ImageObserver observer) {
        Dimension size = new Dimension(width, height);
        BufferedImage buf = buildBufferedImage(size);
        Graphics2D g = buf.createGraphics();
        g.setComposite(AlphaComposite.SrcOver);
        g.setBackground(new Color(1, 1, 1, 0));
        g.fillRect(0, 0, width, height);
        g.clip(new Rectangle(0, 0, buf.getWidth(), buf.getHeight()));
        if (!g.drawImage(img, 0, 0, observer)) {
            return null;
        }
        g.dispose();
        return buf;
    }
}
