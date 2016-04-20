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

import org.apache.fop.events.EventBroadcaster;
import org.apache.fop.events.EventProducer;

interface PDFBoxEventProducer extends EventProducer {

    /** Provider class for the event producer. */
    final class Provider {

        private Provider() { }

        /**
         * Returns an event producer.
         *
         * @param broadcaster the event broadcaster to use
         * @return the event producer
         */
        public static PDFBoxEventProducer get(EventBroadcaster broadcaster) {
            return broadcaster.getEventProducerFor(PDFBoxEventProducer.class);
        }
    }

    /**
     * The PDF version of the document being created is less than that of the PDF being inserted.
     *
     * @param source the event source
     * @param outDocVersion PDF version of the document being created
     * @param inputDocVersion PDF version of the included document
     * @event.severity WARN
     */
    void pdfVersionMismatch(Object source, String outDocVersion, String inputDocVersion);


    /**
     * The document to be included is encrypted.
     *
     * @param source the event source
     * @event.severity ERROR
     */
    void encryptedPdf(Object source);

    /**
     * PDF/A mode is active.
     *
     * @param source the event source
     * @event.severity WARN
     */
    void pdfAActive(Object source);

    /**
     * PDF/X mode is active.
     *
     * @param source the event source
     * @event.severity WARN
     */
    void pdfXActive(Object source);
}
