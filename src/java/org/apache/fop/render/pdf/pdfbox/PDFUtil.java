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

import org.apache.pdfbox.pdmodel.PDPage;

/**
 * Common utility functions for PDF access.
 */
public final class PDFUtil {

    private PDFUtil() { }

    /**
     * Determines the rotation of a given page and normalizes the returned value to the values
     * 0, 90, 180 and 270. If not a multiple of 90 is encountered, 0 is returned.
     * @param page the page
     * @return the page rotation (0, 90, 180 or 270)
     */
    public static int getNormalizedRotation(PDPage page) {
        //Handle the /Rotation entry on the page dict
        int rotation = page.getRotation();
        rotation %= 360;
        if (rotation < 0) {
            rotation += 360;
        }
        switch (rotation) {
            case 90:
            case 180:
            case 270:
                return rotation;
            default:
                return 0;
        }
    }

}
