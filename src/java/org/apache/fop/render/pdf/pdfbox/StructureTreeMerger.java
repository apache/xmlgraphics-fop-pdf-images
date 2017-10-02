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
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.apache.pdfbox.cos.COSArray;
import org.apache.pdfbox.cos.COSBase;
import org.apache.pdfbox.cos.COSDictionary;
import org.apache.pdfbox.cos.COSInteger;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.cos.COSNull;
import org.apache.pdfbox.cos.COSObject;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.documentinterchange.taggedpdf.StandardStructureTypes;


import org.apache.fop.pdf.PDFDictionary;
import org.apache.fop.pdf.PDFDocument;
import org.apache.fop.pdf.PDFNumber;
import org.apache.fop.pdf.PDFObject;
import org.apache.fop.pdf.PDFPage;
import org.apache.fop.pdf.PDFReference;
import org.apache.fop.pdf.PDFStructElem;


import org.apache.fop.render.pdf.PDFLogicalStructureHandler;

public class StructureTreeMerger {

    PDFBoxAdapter adapter;
    PDFPage targetPage;
    PDFDocument pdfDoc;
    private PDPage srcPage;
    private COSDictionary roleMap;
    protected PDFStructElem currentSessionElem;
    private PDFLogicalStructureHandler logicalStructHandler;
    private Map<Integer, PDFStructElem> structElemCache = new HashMap<Integer, PDFStructElem>();
    private Map<Integer, PDFStructElem> markedContentMap = new TreeMap<Integer, PDFStructElem>();
    private int currentMCID;
    private List<COSObject> topElems = new ArrayList<COSObject>();
    private COSArray extra = new COSArray();
    private COSArray originalParentTree = new COSArray();

    public StructureTreeMerger(PDFStructElem currentSessionElem, PDFLogicalStructureHandler logicalStructHandler,
                               PDFBoxAdapter adapter, PDPage srcPage) {
        this.adapter = adapter;
        this.srcPage = srcPage;
        this.targetPage = adapter.getTargetPage();
        this.pdfDoc = targetPage.getDocument();
        this.currentMCID = adapter.getCurrentMCID();
        this.logicalStructHandler = logicalStructHandler;
        this.currentSessionElem = currentSessionElem;
    }

    public void setRoleMap(COSDictionary roleMap) {
        this.roleMap = roleMap;
    }

    public void copyStructure(COSArray pageParentTreeArray) throws IOException {
        originalParentTree = pageParentTreeArray;
        pageParentTreeArray = removeNonCOSObjects(pageParentTreeArray);
        for (COSBase entry : pageParentTreeArray) {
            COSObject entryObj = (COSObject)entry;
            createPageStructElements(entryObj);
        }
        createParents(pageParentTreeArray);
        for (COSObject top : topElems) {
            findLeafNodesInPageFromStructElemObjects(top);
        }
        createParents(extra);
        addToPageParentTreeArray();
        removeNullPlaceholders();
    }

    public void createDirectDescendants(COSBase base, PDFStructElem parent) throws IOException {
        if (base instanceof COSDictionary) {
            COSDictionary baseDict = (COSDictionary)base;
            if (baseDict.keySet().contains(COSName.K)) {
                createDirectDescendants(baseDict.getItem(COSName.K), parent);
            }
        } else if (base instanceof COSArray) {
            COSArray array = (COSArray)base;
            for (int i = 0; i < array.size(); i++) {
                createDirectDescendants(array.get(i), parent);
            }
        } else {
            assert base instanceof COSObject;
            COSObject obj = (COSObject)base;
            createAndRegisterStructElem(obj);
            PDFStructElem elem = structElemCache.get((int)obj.getObjectNumber());
            copyElemEntries(obj, elem);
            parent.addKid(elem);
            elem.setParent(parent);
            COSBase objKid = obj.getItem(COSName.K);
            if (objKid != null) {
                createDirectDescendants(objKid, elem);
            }
        }
    }

    public void setCurrentSessionElem() {
        if (currentSessionElem == null) {
            currentSessionElem = pdfDoc.getStructureTreeElements()
                    .get(pdfDoc.getStructureTreeElements().size() - 1);
        }
    }

    private void createParents(COSArray markedContentParents) throws IOException {
        for (COSBase entry : markedContentParents) {
            COSObject elemCos = (COSObject)entry;
            COSObject elemParent = (COSObject)elemCos.getItem(COSName.P);
            if (elemParent != null) {
                PDFStructElem elem = structElemCache.get((int)elemCos.getObjectNumber());
                createParents(elemCos, elemParent, elem);
            }
        }
    }

    private PDFStructElem createAndRegisterStructElem(COSObject entry) {
        PDFStructElem elem = new PDFStructElem();
        pdfDoc.registerStructureElement(elem);
        structElemCache.put((int)entry.getObjectNumber(), elem);
        return elem;
    }

    private void copyElemEntries(COSBase base, PDFStructElem elem) throws IOException {
        assert base instanceof COSObject;
        COSObject baseObj = (COSObject) base;
        COSDictionary baseDic = (COSDictionary) baseObj.getObject();
        COSName[] names = {COSName.TYPE, COSName.S, COSName.PG, COSName.ALT, COSName.LANG, COSName.A,
            COSName.ACTUAL_TEXT, COSName.T, COSName.E, COSName.C };
        for (COSName name : names) {
            if (baseDic.keySet().contains(name)) {
                if (name.equals(COSName.PG)) {
                    elem.put(COSName.PG.getName(), targetPage.makeReference());
                } else {
                    elem.put(name.getName(), adapter.cloneForNewDocument(baseDic.getItem(name)));
                }
            }
        }
        adapter.cacheClonedObject(base, elem);
    }

    private PDFStructElem createPageStructElements(COSObject entry) throws IOException {
        int objID = (int)entry.getObjectNumber();
        if (structElemCache.containsKey(objID)) {
            return null;
        }
        PDFStructElem elem = createAndRegisterStructElem(entry);
        copyElemEntries(entry, elem);
        COSDictionary baseDict = (COSDictionary) entry.getObject();
        COSBase kid = baseDict.getItem(COSName.K);
        createKids(kid, baseDict, elem, false);
        return elem;
    }

    private void createParents(COSObject cosElem, COSObject cosParentElem, PDFStructElem elem) throws IOException {
        int elemObjectID = (int)cosParentElem.getObjectNumber();
        COSDictionary parentElemDictionary = (COSDictionary)cosParentElem.getObject();
        PDFStructElem elemParent = structElemCache.get(elemObjectID);
        if (isStructureTreeRoot(parentElemDictionary)) {
            elem.setParent(currentSessionElem);
            currentSessionElem.addKid(elem);
            topElems.add(cosElem);
        } else if (elemParent != null) {
            if (!checkIfStructureTypeIsPresent(parentElemDictionary, StandardStructureTypes.TR)) {
                elem.setParent(elemParent);
                int position = StructureTreeMergerUtil.findObjectPositionInKidsArray(cosElem);
                elemParent.addKidInSpecificOrder(position, elem);
            }
        } else if (!checkIfStructureTypeIsPresent(parentElemDictionary, StandardStructureTypes.DOCUMENT)) {
            elemParent = createAndRegisterStructElem(cosParentElem);
            copyElemEntries(cosParentElem, elemParent);
            elem.setParent(elemParent);
            fillKidsWithNull(elemParent, (COSDictionary)cosParentElem.getObject());
            if (parentElemDictionary.getDictionaryObject(COSName.S) == COSName.TR) {
                COSBase rowKids = parentElemDictionary.getItem(COSName.K);
                createKids(rowKids, parentElemDictionary, elemParent, true);
            } else {
                int position = StructureTreeMergerUtil.findObjectPositionInKidsArray(cosElem);
                elemParent.addKidInSpecificOrder(position, elem);
            }
            COSObject parentObj = (COSObject)parentElemDictionary.getItem(COSName.P);
            createParents(cosParentElem, parentObj, elemParent);
        } else {
            elem.setParent(currentSessionElem);
            int position = StructureTreeMergerUtil.findObjectPositionInKidsArray(cosElem);
            currentSessionElem.addKidInSpecificOrder(position, elem);
            topElems.add(cosElem);
        }
    }

    private void createKids(COSBase baseKid, COSDictionary parentDict, PDFStructElem parent,
                            boolean originatedFromTableRow) throws IOException {
        if (baseKid instanceof COSArray) {
            COSArray baseArray = (COSArray) baseKid;
            for (COSBase entry : baseArray) {
                createKids(entry, parentDict, parent, originatedFromTableRow);
            }
        } else if (baseKid instanceof COSObject) {
            COSObject kid = (COSObject)baseKid;
            createKidFromCOSObject(kid, parentDict, parent, originatedFromTableRow);
        } else if (baseKid instanceof COSInteger) {
            if (checkPageEntryInAncestorsRecursively(parentDict)) {
                PDFNumber num = (PDFNumber)adapter.cloneForNewDocument(baseKid);
                createKidEntryFromInt(num, parent);
            }
        } else if (baseKid instanceof COSDictionary) {
            COSDictionary mcrDict = (COSDictionary)baseKid;
            createKidFromCOSDictionary(mcrDict, parent, parentDict);
        }
    }

    private void createKidFromCOSObject(COSObject baseObj, COSDictionary parentDict, PDFStructElem parent,
                                        boolean originatedFromTableRow) throws IOException {
        COSBase baseKid = baseObj.getObject();
        if (baseKid instanceof COSInteger) {
            COSInteger number = (COSInteger) baseKid;
            createKids(number, parentDict, parent, originatedFromTableRow);
        } else {
            COSDictionary unwrappedDict = (COSDictionary)baseKid;
            if (unwrappedDict.getDictionaryObject(COSName.S) == null) {
                COSDictionary mcrDict = (COSDictionary)baseKid;
                createKidFromCOSDictionary(mcrDict, parent, parentDict);
            } else if (originatedFromTableRow) {
                int objID = (int)baseObj.getObjectNumber();
                if (structElemCache.get(objID) != null) {
                    PDFStructElem kidElem = structElemCache.get(objID);
                    parent.addKid(kidElem);
                    kidElem.setParent(parent);
                } else {
                    createkidEntryFromCosObjectForRow(baseObj, parent);
                }
            } else {
                parent.addKid(null);
            }
        }
    }

    private void createkidEntryFromCosObjectForRow(COSObject entree, PDFStructElem parent) throws IOException {
        int entreeObjID = (int)entree.getObjectNumber();
        PDFStructElem elemRef = structElemCache.get(entreeObjID);
        if (elemRef == null) {
            elemRef = createAndRegisterStructElem(entree);
            copyElemEntries(entree, elemRef);
            COSDictionary baseDict = (COSDictionary) entree.getObject();
            COSBase kid = baseDict.getItem(COSName.K);
            createKids(kid, baseDict, elemRef, true);
            parent.addKid(elemRef);
        } else {
            parent.addKid(elemRef);
        }
        elemRef.setParent(parent);
    }

    private boolean checkPageEntryInAncestorsRecursively(COSDictionary elem) {
        if (elem.containsKey(COSName.PG)) {
            COSDictionary pageDict = (COSDictionary)elem.getDictionaryObject(COSName.PG);
            return srcPage.getCOSObject() == pageDict;
        } else if (elem.containsKey(COSName.P)) {
            COSDictionary parent = (COSDictionary)elem.getDictionaryObject(COSName.P);
            return checkPageEntryInAncestorsRecursively(parent);
        } else {
            return true;
        }
    }

    private boolean isElementFromSourcePage(COSDictionary mrcDict, COSDictionary parentDict) {
        if (mrcDict.containsKey(COSName.PG)) {
            COSDictionary page = (COSDictionary)mrcDict.getDictionaryObject(COSName.PG);
            return srcPage.getCOSObject() == page;
        } else {
            return checkPageEntryInAncestorsRecursively(parentDict);
        }
    }

    private void createKidFromCOSDictionary(COSDictionary mcrDict, PDFStructElem parent, COSDictionary baseDict)
        throws IOException {
        Collection<COSName> exclude = Arrays.asList(COSName.PG);
        PDFReference referenceObj;
        if (isElementFromSourcePage(mcrDict, baseDict)) {
            PDFDictionary contentItem = (PDFDictionary)adapter.cloneForNewDocument(mcrDict, mcrDict, exclude);
            if (mcrDict.keySet().contains(COSName.TYPE)) {
                String type = ((COSName) mcrDict.getDictionaryObject(COSName.TYPE)).getName();
                if (type.equals("OBJR")) {
                    COSObject obj = (COSObject) mcrDict.getItem(COSName.OBJ);
                    if (adapter.getCachedClone(obj) == null) {
                        referenceObj = null;
                    } else {
                        referenceObj = ((PDFObject) adapter.getCachedClone(obj)).makeReference();
                    }
                    contentItem.put(COSName.OBJ.getName(), referenceObj);
                    updateStructParentAndAddToPageParentTree(referenceObj, parent);
                } else if (type.equals("MCR")) {
                    updateMCIDEntry(contentItem);
                    markedContentMap.put((((PDFNumber)contentItem.get(COSName.MCID.getName())).getNumber())
                            .intValue(), parent);
                }
            }
            if (mcrDict.keySet().contains(COSName.PG)) {
                contentItem.put(COSName.PG.getName(), targetPage.makeReference());
            } else {
                parent.put(COSName.PG.getName(), targetPage.makeReference());
            }
            parent.addKid(contentItem);
        } else {
            parent.addKid(null);
        }
    }

    private void createKidEntryFromInt(PDFNumber num, PDFStructElem parent) {
        num.setNumber(num.getNumber().intValue() + currentMCID);
        parent.addKid(num);
        markedContentMap.put(num.getNumber().intValue(), parent);
    }

    private void updateMCIDEntry(PDFDictionary mcrDictionary) {
        if (currentMCID > 0) {
            int oldMCID = (((PDFNumber)mcrDictionary.get(COSName.MCID.getName())).getNumber()).intValue();
            PDFNumber number = new PDFNumber();
            number.setNumber(oldMCID + currentMCID);
            mcrDictionary.put(COSName.MCID.getName(), number);
        }
    }

    private void removeNullPlaceholders() {
        List<PDFStructElem> list = new ArrayList<PDFStructElem>(structElemCache.values());
        for (PDFStructElem elem : list) {
            List<PDFObject> kids = elem.getKids();
            if (kids != null) {
                kids.removeAll(Collections.singleton(null));
            }
        }
    }

    private boolean isStructureTreeRoot(COSDictionary elem) {
        if (elem.keySet().contains(COSName.TYPE)) {
            COSName type = (COSName)elem.getDictionaryObject(COSName.TYPE);
            return type.equals(COSName.STRUCT_TREE_ROOT);
        }
        return false;
    }


    public void addToPageParentTreeArray() {
        List<PDFStructElem> complete = restoreNullValuesInParentTree();
        for (PDFStructElem entry : complete) {
            logicalStructHandler.getPageParentTree().add(entry);
        }
    }

    private List<PDFStructElem> restoreNullValuesInParentTree() {
        int total = markedContentMap.size();
        List<PDFStructElem> list = new ArrayList<PDFStructElem>(markedContentMap.values());
        List<PDFStructElem> complete = new ArrayList<PDFStructElem>(total);
        for (COSBase base : originalParentTree) {
            if (base instanceof COSNull || base == null) {
                complete.add(null);
            } else if (!list.isEmpty()) {
                complete.add(list.get(0));
                list.remove(0);
            }
        }
        return complete;
    }

    private void updateStructParentAndAddToPageParentTree(PDFReference obj, PDFStructElem elem) {
        int nextParentTreeKey = logicalStructHandler.getNextParentTreeKey();
        if (obj != null) {
            PDFObject referenceObj = obj.getObject();
            assert referenceObj instanceof PDFDictionary;
            PDFDictionary objDict = (PDFDictionary)referenceObj;
            objDict.put((COSName.STRUCT_PARENT).getName(), nextParentTreeKey);
        }

        logicalStructHandler.getParentTree().addToNums(nextParentTreeKey, elem);
    }

    private void findLeafNodesInPageFromStructElemObjects(COSBase entry) throws IOException {
        if (entry instanceof COSObject) {
            COSObject entryObj = (COSObject) entry;
            COSDictionary structElemDictionary = (COSDictionary) entryObj.getObject();
            COSBase kid = structElemDictionary.getItem(COSName.K);
            findLeafKids(kid, entryObj);
        }
    }

    private void findLeafKids(COSBase kid, COSObject parent) throws IOException {
        if (kid instanceof COSArray) {
            COSArray arrayKid = (COSArray)kid;
            for (COSBase arrayEntry : arrayKid) {
                findLeafKids(arrayEntry, parent);
            }
        } else if (kid instanceof COSObject) {
            COSObject kidObject = (COSObject)kid;
            COSBase base = kidObject.getObject();
            COSDictionary temp = (COSDictionary)base;
            if (temp.getDictionaryObject(COSName.S) != null && temp.getItem(COSName.K) != null) {

                COSBase tempKids = temp.getItem(COSName.K);
                findLeafKids(tempKids, kidObject);
            } else {
                findLeafKids(temp, parent);
            }
        } else if (kid instanceof COSDictionary) {
            COSDictionary kidDictionary = (COSDictionary)kid;
            COSDictionary parentDict = (COSDictionary)parent.getObject();
            if (isElementFromSourcePage(kidDictionary, parentDict)) {
                PDFStructElem elem = structElemCache.get((int)parent.getObjectNumber());
                if (elem == null) {
                    elem = createAndRegisterStructElem(parent);
                    copyElemEntries(parent, elem);
                    extra.add(parent);
                    createKids(kid, parentDict, elem, false);
                }
            }
        } else {
            assert kid instanceof COSInteger;
            COSDictionary parentDict = (COSDictionary)parent.getObject();
            if (checkPageEntryInAncestorsRecursively(parentDict)) {
                PDFStructElem elem = structElemCache.get((int)parent.getObjectNumber());
                if (elem == null) {
                    elem = createAndRegisterStructElem(parent);
                    copyElemEntries(parent, elem);
                    createKids(kid, parentDict, elem, false);
                }
            }
        }
    }

    private void fillKidsWithNull(PDFStructElem elem, COSDictionary baseElem) {
        COSBase baseArray = baseElem.getItem(COSName.K);
        if (baseArray instanceof COSArray) {
            COSArray array = (COSArray)baseArray;
            int size = array.size();
            for (int i = 0; i < size; i++) {
                elem.addKid(null);
            }
        }
    }

    private boolean checkIfStructureTypeIsPresent(COSDictionary elemDictionary, String type) {
        String potentialCustomElemType = ((COSName)elemDictionary.getDictionaryObject(COSName.S)).getName();
        if (type.equals(potentialCustomElemType)) {
            return true;
        } else {
            List<String> rolemapValues = StructureTreeMergerUtil.findRoleMapKeyByValue(type, roleMap);
            return rolemapValues.contains(potentialCustomElemType);
        }
    }

    private COSArray removeNonCOSObjects(COSArray pageParentTreeArray) {
        COSArray objectList = new COSArray();
        for (COSBase entry : pageParentTreeArray) {
            if (entry instanceof COSObject) {
                COSObject entryObj = (COSObject)entry;
                objectList.add(entryObj);
            }
        }
        return objectList;
    }
    public void setCurrentSessionElemKid() {
        PDFNumber num = new PDFNumber();
        createKidEntryFromInt(num, currentSessionElem);
        addToPageParentTreeArray();
    }
}
