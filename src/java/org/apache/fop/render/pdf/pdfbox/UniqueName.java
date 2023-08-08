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
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.apache.pdfbox.cos.COSBase;
import org.apache.pdfbox.cos.COSDictionary;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.cos.COSObject;

import org.apache.fop.pdf.PDFDocument;

/**
 * This class provides collision-avoidance for names within a collection.
 * That is, if a submitted name collides with a name in the collection, an alternative is provided.
 * The alternative may or may not be in the collection.
 */
public class UniqueName {
    private String key;
    private String patternKey;
    private List<COSName> resourceNames = Collections.emptyList();
    private List<COSName> patternNames = Collections.emptyList();

    public UniqueName(String key, COSDictionary sourcePageResources, List<COSName> patternNames, boolean disable,
                      Rectangle destRect) {
        if (!disable) {
            key = key.split("#")[0];
            this.key = Integer.toString(key.hashCode());
            if (!patternNames.isEmpty()) {
                // Make pattern key unique to the destination rectangle.
                patternKey = Integer.toString((key
                        + destRect.getX() + destRect.getY() + destRect.getWidth() + destRect.getHeight()).hashCode());
                this.patternNames = patternNames;
            }
            resourceNames = getResourceNames(sourcePageResources);
        }
    }

    /**
     * Provides a de-collisioned name.
     * @param cn Submitted name.
     * @return The submitted name if it is not already in this object's collection or an alternative it is.
     *         It is possible that the alternative is also in the collection.
     */
    protected String getName(COSName cn) {
        if (patternNames.contains(cn)) {
            return cn.getName() + patternKey;
        }
        if (resourceNames.contains(cn)) {
            return cn.getName() + key;
        }
        return cn.getName();
    }

    /**
     * Writes a name into a StringBuilder, appending a suffix if the name exists in this object's collection.
     * @param sb The StringBuilder.
     * @param cn The COSName.
     * @throws IOException On IO exception.
     */
    protected void writeName(StringBuilder sb, COSName cn) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        cn.writePDF(bos);
        String name = bos.toString(PDFDocument.ENCODING);
        sb.append(name);
        if (patternNames.contains(cn)) {
            sb.append(patternKey);
        }
        if (resourceNames.contains(cn)) {
            sb.append(key);
        }
    }

    private List<COSName> getResourceNames(COSDictionary sourcePageResources) {
        List<COSName> resourceNames = new ArrayList<>();
        for (COSBase e : sourcePageResources.getValues()) {
            if (e instanceof COSObject) {
                e = ((COSObject) e).getObject();
            }
            if (e instanceof COSDictionary) {
                COSDictionary d = (COSDictionary) e;
                for (COSName cosName : d.keySet()) {
                    if (!patternNames.contains(cosName)) {
                        resourceNames.add(cosName);
                    }
                }
            }
        }
        resourceNames.remove(COSName.S);
        return resourceNames;
    }
}
