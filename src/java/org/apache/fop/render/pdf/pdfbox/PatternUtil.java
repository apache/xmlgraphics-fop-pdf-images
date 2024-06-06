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
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.pattern.PDAbstractPattern;
import org.apache.pdfbox.util.Matrix;

import org.apache.fop.pdf.PDFArray;
import org.apache.fop.pdf.PDFDictionary;
import org.apache.fop.pdf.PDFObject;
import org.apache.fop.pdf.PDFPage;

public class PatternUtil {
    private List<COSName> patternNames = new ArrayList<>();
    private PDFPage targetPage;
    private Rectangle pos;
    private PDPage sourcePage;

    public PatternUtil(PDFPage targetPage, Rectangle pos, PDPage sourcePage, boolean disabled) throws IOException {
        if (!disabled) {
            this.targetPage = targetPage;
            this.pos = pos;
            this.sourcePage = sourcePage;
            PDResources srcPgResources = sourcePage.getResources();
            if (srcPgResources != null) {
                for (COSName name : srcPgResources.getPatternNames()) {
                    patternNames.add(name);
                }
                transformPatterns();
            }
        }
    }

    private void transformPatterns() throws IOException {
        // The pattern found in the source document.
        AffineTransform shadingAdjust = getShadingAffineTransform();
        PDResources srcPgResources = sourcePage.getResources();
        for (COSName srcPgPatternName : patternNames) {
            // Get the original pattern.
            PDAbstractPattern srcPattern = srcPgResources.getPattern(srcPgPatternName);
            Matrix originalMatrix = srcPattern.getMatrix();
            if (originalMatrix == null) {
                originalMatrix = new Matrix();
            }
            Matrix shadingMatrix = new Matrix(shadingAdjust);
            // Create the required new matrix and apply it to the pattern.
            Matrix newMatrix = originalMatrix.multiply(shadingMatrix);
            srcPattern.setMatrix(newMatrix.createAffineTransform());
            // Add the pattern to the page resources for now.
            srcPgResources.put(srcPgPatternName, srcPattern);
        }
    }

    public List<COSName> getPatternNames() {
        return patternNames;
    }

    private AffineTransform getShadingAffineTransform() {
        PDRectangle srcMediaBox = sourcePage.getMediaBox();
        PDFArray targetPageMediaBox = (PDFArray)targetPage.get("MediaBox");
        double targetMediaBoxHeight = (double) targetPageMediaBox.get(3);
        // Convert destRect to use bottom/left frame as origin.
        Rectangle cDestRect = new Rectangle(pos);
        cDestRect.y = (int) ((targetMediaBoxHeight * 1000 - (pos.getY() + pos.getHeight())));
        double xScaleFactor = cDestRect.getWidth() / 1000f / srcMediaBox.getWidth();
        double yScaleFactor = cDestRect.getHeight() / 1000f / srcMediaBox.getHeight();
        // x translation: media box offset + scaled cDestRect x-offset
        double xTranslation = cDestRect.getX() / 1000f - srcMediaBox.getLowerLeftX() * xScaleFactor;
        // y translation: media box offset + scaled cDestRect
        double yTranslation = cDestRect.getY() / 1000f - srcMediaBox.getLowerLeftY() * yScaleFactor;
        return new AffineTransform(xScaleFactor, 0, 0, yScaleFactor, xTranslation, yTranslation);
    }

    public void promotePatterns() {
        if (targetPage != null) {
            PDFDictionary patternsDict = (PDFDictionary) targetPage.getPDFResources().get(COSName.PATTERN.getName());
            if (patternsDict != null) {
                for (String key : patternsDict.keySet()) {
                    PDFObject pattern = (PDFObject) patternsDict.get(key);
                    pattern.setObjectNumber(targetPage.getDocument());
                    targetPage.getDocument().addObject(pattern);
                }
            }
        }
    }

    public String getKey(String key) {
        if (patternNames.isEmpty()) {
            return key;
        }
        return key + pos.getX() + pos.getY() + pos.getWidth() + pos.getHeight();
    }

    public List<COSName> getExclude() {
        if (targetPage == null) {
            return Collections.emptyList();
        }
        return Collections.singletonList(COSName.PATTERN);
    }
}
