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
import java.awt.Shape;
import java.awt.TexturePaint;
import java.awt.geom.AffineTransform;
import java.awt.geom.NoninvertibleTransformException;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.awt.image.ColorModel;
import java.awt.image.DataBufferInt;
import java.awt.image.ImageObserver;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.lang.reflect.Field;
import java.net.URI;
import java.security.AccessController;
import java.security.PrivilegedAction;
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
import org.apache.pdfbox.pdmodel.graphics.color.PDColorSpace;
import org.apache.pdfbox.pdmodel.graphics.color.PDDeviceCMYK;
import org.apache.pdfbox.pdmodel.graphics.color.PDDeviceGray;
import org.apache.pdfbox.pdmodel.graphics.shading.AxialShadingContext;
import org.apache.pdfbox.pdmodel.graphics.shading.AxialShadingPaint;
import org.apache.pdfbox.pdmodel.graphics.shading.RadialShadingContext;
import org.apache.pdfbox.pdmodel.graphics.shading.RadialShadingPaint;
import org.apache.pdfbox.pdmodel.graphics.shading.ShadingContext;
import org.apache.pdfbox.pdmodel.graphics.shading.ShadingPaint;
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
            if (paintContext instanceof ShadingContext) {
                PDColorSpace pdcs = getShadingColorSpace((ShadingContext) paintContext);
                if (pdcs instanceof PDDeviceCMYK) {
                    deviceColorSpace = PDFDeviceColorSpace.DEVICE_CMYK;
                } else if (pdcs instanceof PDDeviceGray) {
                    deviceColorSpace = PDFDeviceColorSpace.DEVICE_GRAY;
                }
            }
            if (paint instanceof AxialShadingPaint) {
                try {
                    AxialShadingContext asc = (AxialShadingContext) paintContext;
                    float[] fCoords = asc.getCoords();
                    transformCoords(fCoords, (ShadingPaint) paint, true);
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
                    transformCoords(fCoords, (ShadingPaint) paint, false);
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
        } else if (paint.getClass().getSimpleName().equals("TilingPaint")) {
            TexturePaint texturePaint = (TexturePaint) getField(paint, "paint");
            Matrix matrix = (Matrix) getField(paint, "patternMatrix");
            Rectangle2D rect = getTransformedRect(matrix, texturePaint.getAnchorRect());
            texturePaint = new TexturePaint(texturePaint.getImage(), rect);
            super.applyPaint(texturePaint, fill);
        }
    }

    private PDColorSpace getShadingColorSpace(ShadingContext shadingContext) {
        try {
            Field field = ShadingContext.class.getDeclaredField("shadingColorSpace");
            field.setAccessible(true);
            return (PDColorSpace) field.get(shadingContext);
        } catch (IllegalAccessException | NoSuchFieldException e) {
            throw new RuntimeException(e);
        }
    }

    private static Object getField(final Paint paint, final String field) {
        return AccessController.doPrivileged(new PrivilegedAction<Object>() {
            public Object run() {
                try {
                    Field f = paint.getClass().getDeclaredField(field);
                    f.setAccessible(true);
                    return f.get(paint);
                } catch (NoSuchFieldException e) {
                    throw new RuntimeException(e);
                } catch (IllegalAccessException e) {
                    throw new RuntimeException(e);
                }
            }
        });
    }

    private static Rectangle2D getTransformedRect(Matrix matrix, Rectangle2D anchorRect) {
        double x = anchorRect.getX();
        double y = anchorRect.getY();
        double width = anchorRect.getWidth();
        double height = anchorRect.getHeight();
        AffineTransform at = matrix.createAffineTransform();
        Point2D p1 = new Point2D.Double(x, y);
        Point2D p2 = new Point2D.Double(x + width, y + height);
        at.transform(p1, p1);
        at.transform(p2, p2);
        Rectangle2D rectangle = new Rectangle2D.Float(
                (float) Math.min(p1.getX(), p2.getX()),
                (float) Math.min(p1.getY(), p2.getY()),
                (float) Math.abs(width),
                (float) Math.abs(height));
        return rectangle;
    }

    private void transformCoords(float[] coords, ShadingPaint paint, boolean axialShading) {
        Matrix ctm = paint.getMatrix();
        AffineTransform at = ctm.createAffineTransform();
        if (axialShading) {
            at.transform(coords, 0, coords, 0, 2);
        } else {
            at.transform(coords, 0, coords, 0, 1);
            at.transform(coords, 3, coords, 3, 1);
            coords[2] *= ctm.getScalingFactorX();
            coords[5] *= ctm.getScalingFactorX();
        }
    }

    public void fill(Shape shape) {
        if ("org.apache.pdfbox.rendering.SoftMask".equals(getPaint().getClass().getName())) {
            assert shape instanceof Rectangle2D;
            Rectangle2D rect = (Rectangle2D) shape;
            BufferedImage img = new BufferedImage((int)rect.getWidth() * 2, (int)rect.getHeight() * 2,
                    BufferedImage.TYPE_INT_ARGB);
            Graphics2D graphics = img.createGraphics();
            graphics.setTransform(getTransform());
            graphics.setPaint(getPaint());
            graphics.fill(shape);
            graphics.dispose();
            try {
                drawImage(img, getTransform().createInverse(), null);
            } catch (NoninvertibleTransformException e) {
                throw new RuntimeException(e);
            }
        } else {
            super.fill(shape);
        }
    }

    protected static Function getFunction(PDFunction f) throws IOException {
        if (f instanceof PDFunctionType3) {
            PDFunctionType3 sourceFT3 = (PDFunctionType3) f;
            float[] bounds = sourceFT3.getBounds().toFloatArray();
            COSArray sourceFunctions = sourceFT3.getFunctions();
            List<Function> targetFunctions = new ArrayList<Function>();
            for (int j = 0; j < sourceFunctions.size(); j++) {
                targetFunctions.add(getFunction(PDFunction.create(sourceFunctions.get(j))));
            }
            float[] encode = sourceFT3.getEncode().toFloatArray();
            return new Function(null, null, targetFunctions, toList(bounds), floatArrayToDoubleList(encode));
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
            COSArray domain = (COSArray) s.getDictionaryObject(COSName.DOMAIN);
            COSArray range = (COSArray) s.getDictionaryObject(COSName.RANGE);
            int bits = ((COSInteger)s.getDictionaryObject(COSName.BITS_PER_SAMPLE)).intValue();
            COSArray size = (COSArray) s.getDictionaryObject(COSName.SIZE);
            COSArray encode = getEncode(s);
            byte[] x = IOUtils.toByteArray(stream.createInputStream());
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

    private static COSArray getEncode(COSDictionary s) {
        COSArray encode = (COSArray) s.getDictionaryObject(COSName.ENCODE);
        if (encode == null) {
            encode = new COSArray();
            COSArray size = (COSArray) s.getDictionaryObject(COSName.SIZE);
            int sizeValuesSize = size.size();
            for (int i = 0; i < sizeValuesSize; i++) {
                encode.add(COSInteger.ZERO);
                encode.add(COSInteger.get(size.getInt(i) - 1));
            }
        }
        return encode;
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
        Color mask = null;
        ColorModel cm = ((BufferedImage)img).getColorModel();
        if (cm.hasAlpha()) {
            mask = getMask((BufferedImage) img);
            img = convertToRGB(img, mask);
        }
        if (gen instanceof PSDocumentHandler.FOPPSGenerator) {
            PSDocumentHandler.FOPPSGenerator fopGen = (PSDocumentHandler.FOPPSGenerator)gen;
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
        return super.drawImage(img, x1, y1, observer, mask);
    }

    private Image convertToRGB(Image img, Color mask) {
        //convert when we use custom background color
        if (mask != Color.white) {
            BufferedImage rgbImg =
                    new BufferedImage(img.getWidth(null), img.getHeight(null), BufferedImage.TYPE_INT_RGB);
            Graphics2D g = rgbImg.createGraphics();
            g.setColor(mask);
            g.fillRect(0, 0, rgbImg.getWidth(), rgbImg.getHeight());
            g.drawImage(img, 0, 0, null);
            g.dispose();
            return rgbImg;
        }
        return img;
    }

    private Color getMask(BufferedImage img) {
        boolean black = false;
        boolean white = false;
        for (int i = 0; i < img.getWidth(); i++) {
            for (int j = 0; j < img.getHeight(); j++) {
                int rgb = img.getRGB(i, j);
                if (rgb == Color.white.getRGB()) {
                    white = true;
                } else if (rgb == Color.black.getRGB()) {
                    black = true;
                }
            }
        }
        if (white && !black) {
            return Color.black;
        }
        return Color.white;
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
