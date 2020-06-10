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

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.apache.pdfbox.cos.COSArray;
import org.apache.pdfbox.cos.COSBase;
import org.apache.pdfbox.cos.COSDictionary;
import org.apache.pdfbox.cos.COSInteger;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.cos.COSObject;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDNumberTreeNode;
import org.apache.pdfbox.pdmodel.graphics.PDXObject;

public class PageParentTreeFinder {

    private PDPage srcPage;
    public PageParentTreeFinder(PDPage srcPage) {
        this.srcPage = srcPage;
    }

    public COSArray getPageParentTreeArray(PDDocument srcDoc) throws IOException {
        int position = srcPage.getCOSObject().getInt(COSName.STRUCT_PARENTS);
        if (position == -1) {
            position = findXObjectStructParent();
        }
        if (position != -1) {
            PDNumberTreeNode srcNumberTreeNode = srcDoc.getDocumentCatalog().getStructureTreeRoot().getParentTree();
            return traverseParentTree(srcNumberTreeNode.getCOSObject(), position);
        }
        return new COSArray();
    }
    //TODO handle structural hierarchy in xboject stream
    private int findXObjectStructParent() throws IOException {
        int position = -1;
        Iterable<COSName> mapXObject = srcPage.getResources().getXObjectNames();
        for (COSName n : mapXObject) {
            PDXObject t = srcPage.getResources().getXObject(n);
            COSDictionary xObjectDict = t.getCOSObject();
            position = xObjectDict.getInt(COSName.STRUCT_PARENTS);
            if (position != -1) {
                return position;
            }
        }
        return position;
    }

    private COSArray traverseParentTree(COSDictionary numberTreeNodeDict, int position) {
        COSArray numberTree;
        COSArray parentTree;
        List<COSArray> nums = new ArrayList<COSArray>();
        if (numberTreeNodeDict.containsKey(COSName.NUMS)) {
            numberTree = (COSArray)numberTreeNodeDict.getItem(COSName.NUMS);
            return extractMarkedContentParents(numberTree, position);
        } else {
            parentTree = (COSArray) numberTreeNodeDict.getDictionaryObject(COSName.KIDS);
            traverseKids(parentTree, position, nums);
        }
        if (nums.isEmpty()) {
            return new COSArray();
        }
        return nums.get(0);
    }

    private void traverseKids(COSBase kids, int position, List<COSArray> numList) {
        if (!numList.isEmpty()) {
            return;
        }
        if (kids instanceof COSArray) {
            COSArray kidsArray = (COSArray)kids;
            for (COSBase kid : kidsArray) {
                COSObject kidCOSObj = (COSObject) kid;
                traverseKids(kidCOSObj, position, numList);
            }
        } else if (kids instanceof COSObject) {
            COSObject kidCOSObj = (COSObject) kids;
            if (kidCOSObj.getDictionaryObject(COSName.NUMS) == null) {
                traverseKids(kidCOSObj.getDictionaryObject(COSName.KIDS), position, numList);
            } else {
                if (kidCOSObj.getDictionaryObject(COSName.LIMITS) != null) {
                    COSArray kidCOSArray = (COSArray) kidCOSObj.getDictionaryObject(COSName.LIMITS);
                    int lowerLimit = ((COSInteger) kidCOSArray.get(0)).intValue();
                    int upperLimit = ((COSInteger) kidCOSArray.get(1)).intValue();
                    if (lowerLimit <= position && position <= upperLimit) {
                        COSArray nums = (COSArray) kidCOSObj.getDictionaryObject(COSName.NUMS);
                        numList.add(extractMarkedContentParents(nums, position));
                    }
                } else {
                    COSArray nums = (COSArray) kidCOSObj.getDictionaryObject(COSName.NUMS);
                    numList.add(extractMarkedContentParents(nums, position));
                }
            }
        }
    }

    private COSArray extractMarkedContentParents(COSArray numberTree, int position) {
        COSBase tempObject;
        boolean keyFlag = false;
        for (COSBase kid : numberTree) {
            if (keyFlag) {
                if (kid instanceof COSObject) {
                    tempObject = ((COSObject)kid).getObject();
                    if (tempObject instanceof COSArray) {
                        return (COSArray) tempObject;
                    }
                } else if (kid instanceof COSArray) {
                    return (COSArray)kid;
                }
            }
            if (kid instanceof COSInteger) {
                int temp = ((COSInteger)kid).intValue();
                if (temp == position) {
                    keyFlag = true;
                }
            }
        }
        return new COSArray();
    }
}
