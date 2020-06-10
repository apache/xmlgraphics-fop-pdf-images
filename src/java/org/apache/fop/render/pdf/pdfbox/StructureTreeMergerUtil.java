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
package org.apache.fop.render.pdf.pdfbox;

import java.util.ArrayList;
import java.util.List;
import java.util.Map.Entry;

import org.apache.pdfbox.cos.COSArray;
import org.apache.pdfbox.cos.COSBase;
import org.apache.pdfbox.cos.COSDictionary;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.cos.COSObject;

public final class StructureTreeMergerUtil {

    private StructureTreeMergerUtil() { }

    public static int findObjectPositionInKidsArray(COSObject kid) {
        COSObject parent = (COSObject) kid.getItem(COSName.P);
        COSBase kids = parent.getItem(COSName.K);
        if (kids instanceof COSArray) {
            COSArray kidsArray = (COSArray)kids;
            return kidsArray.indexOfObject(kid);
        } else {
            return 0;
        }
    }

    public static List<String> findRoleMapKeyByValue(String type, COSDictionary roleMap) {
        List<String> keys = new ArrayList<String>();
        if (roleMap != null) {
            for (Entry<COSName, COSBase> entry : roleMap.entrySet()) {
                if (entry.getValue() instanceof COSName) {
                    String value = ((COSName) entry.getValue()).getName();
                    String key = entry.getKey().getName();
                    if (type.equals(value)) {
                        keys.add(key);
                    }
                }
            }
        }
        return keys;
    }
}
