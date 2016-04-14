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
import java.awt.Paint;
import java.awt.PaintContext;
import java.awt.Rectangle;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.awt.image.ImageObserver;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.lang.reflect.Field;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.commons.io.IOUtils;
import org.apache.pdfbox.cos.COSArray;
import org.apache.pdfbox.cos.COSBase;
import org.apache.pdfbox.cos.COSDictionary;
import org.apache.pdfbox.cos.COSInteger;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.cos.COSStream;
import org.apache.pdfbox.pdmodel.common.function.PDFunction;
import org.apache.pdfbox.pdmodel.common.function.PDFunctionType0;
import org.apache.pdfbox.pdmodel.common.function.PDFunctionType2;
import org.apache.pdfbox.pdmodel.common.function.PDFunctionType3;
import org.apache.pdfbox.pdmodel.graphics.shading.AxialShadingContext;
import org.apache.pdfbox.pdmodel.graphics.shading.AxialShadingPaint;
import org.apache.pdfbox.pdmodel.graphics.shading.RadialShadingContext;
import org.apache.pdfbox.pdmodel.graphics.shading.RadialShadingPaint;
import org.apache.pdfbox.util.Matrix;

import org.apache.xmlgraphics.image.loader.ImageInfo;
import org.apache.xmlgraphics.image.loader.ImageSize;
import org.apache.xmlgraphics.io.TempResourceURIGenerator;
import org.apache.xmlgraphics.java2d.ps.PSGraphics2D;
import org.apache.xmlgraphics.ps.PSGenerator;
import org.apache.xmlgraphics.ps.PSResource;

import org.apache.fop.pdf.PDFDeviceColorSpace;
import org.apache.fop.render.gradient.Function;
import org.apache.fop.render.gradient.GradientMaker;
import org.apache.fop.render.gradient.GradientMaker.DoubleFormatter;
import org.apache.fop.render.gradient.Pattern;
import org.apache.fop.render.gradient.Shading;
import org.apache.fop.render.ps.Gradient;
import org.apache.fop.render.ps.PSDocumentHandler;
import org.apache.fop.render.ps.PSImageUtils;

public class PSPDFGraphics2D extends PSGraphics2D {
    private boolean clearRect;

    public PSPDFGraphics2D(boolean textAsShapes) {
        super(textAsShapes);
    }

    public PSPDFGraphics2D(PSGraphics2D g) {
        super(g);
    }

    public PSPDFGraphics2D(boolean textAsShapes, PSGenerator gen) {
        super(textAsShapes, gen);
    }

    public void clearRect(int x, int y, int width, int height) {
        if (clearRect) {
            super.clearRect(x, y, width, height);
        }
        clearRect = true;
    }

    private final GradientMaker.DoubleFormatter doubleFormatter = new DoubleFormatter() {

        public String formatDouble(double d) {
            return getPSGenerator().formatDouble(d);
        }
    };

    protected void applyPaint(Paint paint, boolean fill) {
        preparePainting();
        if (paint instanceof AxialShadingPaint || paint instanceof RadialShadingPaint) {
            PaintContext paintContext = paint.createContext(null, new Rectangle(), null, new AffineTransform(),
                    getRenderingHints());
            int deviceColorSpace = PDFDeviceColorSpace.DEVICE_RGB;
            if (paint instanceof AxialShadingPaint) {
                try {
                    AxialShadingContext asc = (AxialShadingContext) paintContext;
                    float[] fCoords = asc.getCoords();
                    transformCoords(fCoords, paint, true);
                    PDFunction function = asc.getFunction();
                    Function targetFT = getFunction(function);
                    if (targetFT != null) {
                        if (targetFT.getFunctions().size() == 5
                                && targetFT.getFunctions().get(0).getFunctionType() == 0) {
                            return;
                        }
                        List<Double> dCoords = floatArrayToDoubleList(fCoords);
                        PDFDeviceColorSpace colSpace = new PDFDeviceColorSpace(deviceColorSpace);
                        Shading shading = new Shading(2, colSpace, dCoords, targetFT);
                        Pattern pattern = new Pattern(2, shading, null);
                        gen.write(Gradient.outputPattern(pattern, doubleFormatter));
                    }
                } catch (IOException ioe) {
                    handleIOException(ioe);
                }
            } else if (paint instanceof RadialShadingPaint) {
                try {
                    RadialShadingContext rsc = (RadialShadingContext) paintContext;
                    float[] fCoords = rsc.getCoords();
                    transformCoords(fCoords, paint, false);
                    PDFunction function = rsc.getFunction();
                    Function targetFT3 = getFunction(function);
                    List<Double> dCoords = floatArrayToDoubleList(fCoords);
                    PDFDeviceColorSpace colSpace = new PDFDeviceColorSpace(deviceColorSpace);
                    Shading shading = new Shading(3, colSpace, dCoords, targetFT3);
                    Pattern pattern = new Pattern(2, shading, null);
                    gen.write(Gradient.outputPattern(pattern, doubleFormatter));
                } catch (IOException ioe) {
                    handleIOException(ioe);
                }
            }
        }
    }

    private void transformCoords(float[] coords, Paint paint, boolean axialShading) {
        try {
            Field f = paint.getClass().getDeclaredField("matrix");
            f.setAccessible(true);
            Matrix ctm = (Matrix) f.get(paint);
            AffineTransform at = ctm.createAffineTransform();
            if (axialShading) {
                at.transform(coords, 0, coords, 0, 2);
            } else {
                at.transform(coords, 0, coords, 0, 1);
                at.transform(coords, 3, coords, 3, 1);
                coords[2] *= ctm.getScalingFactorX();
                coords[5] *= ctm.getScalingFactorX();
            }

        } catch (NoSuchFieldException e) {
            throw new RuntimeException(e);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    private static Function getFunction(PDFunction f) throws IOException {
        if (f instanceof PDFunctionType3) {
            PDFunctionType3 sourceFT3 = (PDFunctionType3) f;
            float[] bounds = sourceFT3.getBounds().toFloatArray();
            COSArray sourceFunctions = sourceFT3.getFunctions();
            List<Function> targetFunctions = new ArrayList<Function>();
            for (int j = 0; j < sourceFunctions.size(); j++) {
                targetFunctions.add(getFunction(PDFunction.create(sourceFunctions.get(j))));
            }
            return new Function(null, null, targetFunctions, toList(bounds), null);
        } else if (f instanceof PDFunctionType2) {
            PDFunctionType2 sourceFT2 = (PDFunctionType2) f;
            double interpolation = (double)sourceFT2.getN();
            float[] c0 = sourceFT2.getC0().toFloatArray();
            float[] c1 = sourceFT2.getC1().toFloatArray();
            return new Function(null, null, c0, c1, interpolation);
        } else if (f instanceof PDFunctionType0) {
            COSDictionary s = f.getCOSObject();
            assert s instanceof COSStream;
            COSStream stream = (COSStream) s;
            COSArray encode = (COSArray) s.getDictionaryObject(COSName.ENCODE);
            COSArray domain = (COSArray) s.getDictionaryObject(COSName.DOMAIN);
            COSArray range = (COSArray) s.getDictionaryObject(COSName.RANGE);
            int bits = ((COSInteger)s.getDictionaryObject(COSName.BITS_PER_SAMPLE)).intValue();
            COSArray size = (COSArray) s.getDictionaryObject(COSName.SIZE);
            byte[] x = IOUtils.toByteArray(stream.getUnfilteredStream());
            for (byte y : x) {
                if (y != 0) {
                    return new Function(floatArrayToDoubleList(domain.toFloatArray()),
                            floatArrayToDoubleList(range.toFloatArray()),
                            floatArrayToDoubleList(encode.toFloatArray()),
                            x,
                            bits,
                            toList(size)
                    );
                }
            }
            return null;
        }
        throw new IOException("Unsupported " + f.toString());
    }

    private static List<Float> toList(float[] array) {
        List<Float> list = new ArrayList<Float>(array.length);
        for (float f : array) {
            list.add(f);
        }
        return list;
    }

    private static List<Integer> toList(COSArray array) {
        List<Integer> list = new ArrayList<Integer>();
        for (COSBase i : array) {
            list.add(((COSInteger)i).intValue());
        }
        return list;
    }

    private static List<Double> floatArrayToDoubleList(float[] floatArray) {
        List<Double> doubleList = new ArrayList<Double>();
        for (float f : floatArray) {
            doubleList.add((double) f);
        }
        return doubleList;
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
