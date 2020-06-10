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

package org.apache.fop.render.pdf;

import java.util.List;

import org.junit.Assert;
import org.junit.Test;

import org.apache.pdfbox.cos.COSDictionary;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.cos.COSNull;

import org.apache.fop.render.pdf.pdfbox.StructureTreeMergerUtil;

public class StructureTreeMergerUtilTestCase {

    @Test
    public void testFindRoleMapKeyByValue() {

        COSDictionary rolemap = new COSDictionary();
        COSName key1 = COSName.getPDFName("Para");
        COSName value1 = COSName.P;
        COSName key2 = COSName.getPDFName("Icon");
        COSName value2 = COSName.IMAGE;
        rolemap.setItem(key1, value1);
        rolemap.setItem(key2, value2);
        String type = "Image";
        List<String> result = StructureTreeMergerUtil.findRoleMapKeyByValue(type, rolemap);
        String test = result.get(0);
        String expected = "Icon";
        Assert.assertEquals(test, expected);
    }

    @Test
    public void testCOSNull() {
        COSDictionary rolemap = new COSDictionary();
        rolemap.setItem(COSName.A, COSNull.NULL);
        List<String> result = StructureTreeMergerUtil.findRoleMapKeyByValue(null, rolemap);
        Assert.assertTrue(result.isEmpty());
    }
}
