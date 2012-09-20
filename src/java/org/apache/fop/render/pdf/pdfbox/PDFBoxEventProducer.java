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
            return (PDFBoxEventProducer) broadcaster.getEventProducerFor(
                    PDFBoxEventProducer.class);
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
     * @param source
     * @event.severity WARN
     */
    void pdfAActive(Object source);

    /**
     * PDF/X mode is active.
     *
     * @param source
     * @event.severity WARN
     */
    void pdfXActive(Object source);
}
