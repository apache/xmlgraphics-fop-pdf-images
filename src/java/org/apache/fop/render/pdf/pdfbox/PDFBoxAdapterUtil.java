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

import java.awt.Rectangle;
import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.apache.commons.io.IOUtils;
import org.apache.pdfbox.cos.COSArray;
import org.apache.pdfbox.cos.COSBase;
import org.apache.pdfbox.cos.COSDictionary;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.cos.COSObject;
import org.apache.pdfbox.cos.COSStream;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotation;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAppearanceDictionary;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAppearanceStream;

import org.apache.fop.pdf.PDFArray;
import org.apache.fop.pdf.PDFDictionary;

public final class PDFBoxAdapterUtil {
    private PDFBoxAdapterUtil() {
    }

    private static Integer getStreamHash(COSStream o) throws IOException {
        return getDictionaryHash(o).hashCode();
    }

    private static String getDictionaryHash(COSBase base) throws IOException {
        return getDictionaryHash(base, new HashMap<COSBase, String>());
    }

    private static String getDictionaryHash(COSBase base, Map<COSBase, String> objs) throws IOException {
        if (base == null) {
            return "null";
        }
        if (objs.containsKey(base)) {
            return objs.get(base);
        }
        objs.put(base, String.valueOf(base.hashCode()));
        String value;
        if (base instanceof COSDictionary) {
            StringBuilder sb = new StringBuilder("COSDictionary{");
            Map<COSName, String> map = new HashMap<>();
            for (Map.Entry<COSName, COSBase> entry : ((COSDictionary) base).entrySet()) {
                map.put(entry.getKey(), getDictionaryHash(entry.getValue(), objs));
            }
            for (Map.Entry<COSName, String> entry : new TreeMap<>(map).entrySet()) {
                sb.append(entry.getKey());
                sb.append(":");
                sb.append(entry.getValue());
                sb.append(";");
            }
            sb.append("}");
            if (base instanceof COSStream && ((COSStream) base).hasData()) {
                InputStream stream = ((COSStream)base).createRawInputStream();
                byte[] b = IOUtils.toByteArray(stream);
                sb.append("COSStream{").append(Arrays.hashCode(b)).append("}");
            }
            value = sb.toString();
        } else if (base instanceof COSObject) {
            COSObject obj = (COSObject) base;
            value = "COSObject{" + getDictionaryHash(obj.getObject(), objs) + "}";
        } else if (base instanceof COSArray) {
            COSArray array = (COSArray) base;
            StringBuilder sb = new StringBuilder("COSArray[");
            for (Object o : array) {
                if (o instanceof COSObject) {
                    COSBase obj = ((COSObject) o).getObject();
                    if (obj instanceof COSStream) {
                        sb.append(getDictionaryHash(obj, objs));
                    } else {
                        sb.append(o);
                    }
                } else {
                    sb.append(o);
                }
                sb.append(",");
            }
            sb.append("]");
            value = sb.toString();
        } else {
            value = base.toString();
        }
        objs.put(base, value);
        return value;
    }

    protected static Object getBaseKey(Object base) throws IOException {
        if (base instanceof COSObject) {
            COSObject obj = (COSObject)base;
            COSBase o = obj.getObject();
            if (o instanceof COSStream) {
                Integer hash = PDFBoxAdapterUtil.getStreamHash((COSStream) o);
                if (hash != null) {
                    return hash;
                }
            }
            return obj.getObjectNumber() + " " + obj.getGenerationNumber();
        }
        if (base instanceof COSDictionary) {
            String dict = PDFBoxAdapterUtil.getDictionaryHash((COSBase) base);
            return String.valueOf(dict.hashCode());
        }
        return null;
    }

    protected static void rotate(int rotation, PDRectangle viewBox, AffineTransform atdoc) {
        float w = viewBox.getWidth();
        float h = viewBox.getHeight();
        float x = viewBox.getLowerLeftX();
        float y = viewBox.getLowerLeftY();
        switch (rotation) {
            case 90:
                atdoc.rotate(Math.toRadians(rotation + 180), x, y);
                atdoc.translate(-h, 0);
                break;
            case 180:
                atdoc.translate(w, h);
                atdoc.rotate(Math.toRadians(rotation), x, y);
                break;
            case 270:
                atdoc.rotate(Math.toRadians(rotation + 180), x, h + y);
                atdoc.translate(-w, 0);
                break;
            default:
                //no additional transformations necessary
                break;
        }
    }

    protected static void updateAnnotationLink(PDFDictionary clonedAnnot) {
        Object a = clonedAnnot.get("A");
        if (a instanceof PDFDictionary) {
            PDFDictionary annot = (PDFDictionary) a;
            Object oldarrayObj = annot.get("D");
            if (oldarrayObj instanceof PDFArray) {
                PDFArray oldarray = (PDFArray) oldarrayObj;
                Object newarrayObj = oldarray.get(0);
                if (newarrayObj instanceof PDFArray) {
                    PDFArray newarray = (PDFArray) newarrayObj;
                    for (int i = 1; i < oldarray.length(); i++) {
                        newarray.add(oldarray.get(i));
                    }
                    annot.put("D", oldarray.get(0));
                }
            }
        }
    }

    protected static void moveAnnotations(PDPage page, List pageAnnotations, AffineTransform at, Rectangle pos) {
        PDRectangle mediaBox = page.getMediaBox();
        PDRectangle cropBox = page.getCropBox();
        PDRectangle viewBox = cropBox != null ? cropBox : mediaBox;
        for (Object obj : pageAnnotations) {
            PDAnnotation annot = (PDAnnotation) obj;
            PDRectangle rect = annot.getRectangle();
            float translateX = (float) (at.getTranslateX() - viewBox.getLowerLeftX());
            float translateY = (float) (at.getTranslateY() - viewBox.getLowerLeftY());
            if (rect != null) {
                rect.setUpperRightX(rect.getUpperRightX() + translateX);
                rect.setLowerLeftX(rect.getLowerLeftX() + translateX);
                rect.setUpperRightY(rect.getUpperRightY() + translateY);
                rect.setLowerLeftY(rect.getLowerLeftY() + translateY);
                int rotation = PDFUtil.getNormalizedRotation(page);
                if (rotation > 0) {
                    AffineTransform transform = AffineTransform.getTranslateInstance(translateX, translateY);
                    float height = (float)pos.getHeight() / 1000f;
                    rotateStream(transform, rotation, height, annot);
                    transform.translate(-translateX, -translateY);
                    rect = applyTransform(rect, transform);
                }
                annot.setRectangle(rect);
            }
        }
    }

    private static void rotateStream(AffineTransform transform, int rotation, float height, PDAnnotation annot) {
        transform.rotate(Math.toRadians(-rotation));
        transform.translate(-height, 0);
        COSDictionary mkDict = (COSDictionary) annot.getCOSObject().getDictionaryObject(COSName.MK);
        if (mkDict != null) {
            mkDict.removeItem(COSName.R);
        }
        PDAppearanceDictionary appearance = annot.getAppearance();
        if (appearance != null) {
            for (PDAppearanceStream stream : appearance.getNormalAppearance().getSubDictionary().values()) {
                stream.setMatrix(new AffineTransform());
            }
            for (PDAppearanceStream stream : appearance.getDownAppearance().getSubDictionary().values()) {
                stream.setMatrix(new AffineTransform());
            }
        }
    }

    private static PDRectangle applyTransform(PDRectangle rect, AffineTransform apAt) {
        Rectangle2D.Float rectangle = new Rectangle2D.Float();
        rectangle.setRect(rect.getLowerLeftX(), rect.getLowerLeftY(), rect.getWidth(), rect.getHeight());
        Rectangle2D rectangleT = apAt.createTransformedShape(rectangle).getBounds2D();
        rect = new PDRectangle((float)rectangleT.getX(), (float)rectangleT.getY(),
                (float)rectangleT.getWidth(), (float)rectangleT.getHeight());
        return rect;
    }
}
