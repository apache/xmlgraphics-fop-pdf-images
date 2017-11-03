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
import java.util.List;

import org.apache.pdfbox.cos.COSDictionary;

import org.apache.fop.pdf.PDFDictionary;
import org.apache.fop.pdf.RefPDFFont;

interface FOPPDFFont extends RefPDFFont {
    String getFontName();
    void setRef(PDFDictionary d);
    String addFont(COSDictionary fontdata) throws IOException;
    int size();
    String getMappedWord(List<String> word, byte[] bytes, FontContainer oldFont);
}
