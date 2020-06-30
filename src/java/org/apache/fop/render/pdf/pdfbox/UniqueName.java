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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.apache.pdfbox.cos.COSBase;
import org.apache.pdfbox.cos.COSDictionary;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.cos.COSObject;

public class UniqueName {
    private String key;
    private List<COSName> resourceNames;

    public UniqueName(String key, COSDictionary sourcePageResources, boolean disable) {
        if (disable) {
            resourceNames = Collections.emptyList();
        } else {
            key = key.split("#")[0];
            this.key = Integer.toString(key.hashCode());
            resourceNames = getResourceNames(sourcePageResources);
        }
    }

    protected String getName(COSName cn) {
        if (resourceNames.contains(cn)) {
            return cn.getName() + key;
        }
        return cn.getName();
    }

    private List<COSName> getResourceNames(COSDictionary sourcePageResources) {
        List<COSName> resourceNames = new ArrayList<COSName>();
        for (COSBase e : sourcePageResources.getValues()) {
            if (e instanceof COSObject) {
                e = ((COSObject) e).getObject();
            }
            if (e instanceof COSDictionary) {
                COSDictionary d = (COSDictionary) e;
                resourceNames.addAll(d.keySet());
            }
        }
        return resourceNames;
    }
}
