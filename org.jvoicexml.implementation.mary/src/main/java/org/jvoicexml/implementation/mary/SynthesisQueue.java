/*
 * JVoiceXML - A free VoiceXML implementation.
 *
 * Copyright (C) 2010-2017 JVoiceXML group - http://jvoicexml.sourceforge.net
 * The JVoiceXML group hereby disclaims all copyright interest in the
 * library `JVoiceXML' (a free VoiceXML implementation).
 * JVoiceXML group, $Date$, Dirk Schnelle-Walka, project lead
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Library General Public
 * License as published by the Free Software Foundation; either
 * version 2 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Library General Public License for more details.
 *
 * You should have received a copy of the GNU Library General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 *
 */

package org.jvoicexml.implementation.mary;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Collection;
import java.util.Map;
import java.util.Queue;

import org.apache.log4j.Logger;
import org.jvoicexml.SpeakableSsmlText;
import org.jvoicexml.SpeakableText;
import org.jvoicexml.event.error.BadFetchError;
import org.jvoicexml.event.plain.implementation.OutputEndedEvent;
import org.jvoicexml.event.plain.implementation.OutputStartedEvent;
import org.jvoicexml.event.plain.implementation.QueueEmptyEvent;
import org.jvoicexml.event.plain.implementation.SystemOutputEvent;
import org.jvoicexml.implementation.SystemOutputImplementationListener;
import org.jvoicexml.xml.ssml.Speak;
import org.jvoicexml.xml.ssml.SsmlDocument;
import org.jvoicexml.xml.vxml.BargeInType;

import marytts.client.MaryClient;

/**
 * SynthesisQueue extends Thead and is responsible for getting the speakables.
 * from the queue in which they are stored by MarySynthesizedOutput and passes
 * them to Mary server and to TextOutput if text output is enabled After getting
 * the processed data from the server it calls queueAudio method of
 * MaryAudioFileOutput to play the sound
 * 
 * @author Dirk Schnelle-Walka
 * @author Giannis Assiouras
 * 
 */
final class SynthesisQueue extends Thread {
    /** Logger for this class. */
    private static final Logger LOGGER = Logger.getLogger(SynthesisQueue.class);

    /** Timeout for requests to the Mary server. */
    private static final int SERVER_TIMEOUT = 5000;

    /** The queue of speakables. */
    private final Queue<SpeakableText> queuedSpeakables;

    /** The system output listener. */
    private SystemOutputImplementationListener listener;

    /** The Mary synthesized output resource. */
    private final MarySynthesizedOutput output;

    /** The Mary client used to send requests to Mary server . */
    private MaryClient processor;

    /**
     * Reference to the SpeakableText object that is currently played back. This
     * can be either a SpeakablePlainText or a SpeakableSsmlText.
     */
    private SpeakableText queuedSpeakable;

    /**
     * The HashTable that contains Mary synthesis request parameters. e.g
     * audioType,voiceName,voiceEffects and their value
     */
    private Map<String, String> maryRequestParameters;

    /** The line output stream. */
    private LineAudioFormatOutputStream out;

    /**
     * Constructs a new SynthesisQueue object. .
     * @param synthesizedOutput reference to the parent
     */
    SynthesisQueue(final MarySynthesizedOutput synthesizedOutput) {
        queuedSpeakables = new java.util.LinkedList<SpeakableText>();
        output = synthesizedOutput;
        setDaemon(true);
        setName("SynthesisQueueThread");
    }

    /**
     * Thread's run method:If the queue is Empty it fires a QueueEmpty Event to
     * MarySynthesizedOutput and from there to the Voice Browser. Otherwise it
     * removes the first speakable and passes it to the Mary server.
     */
    @Override
    public void run() {
        while (processor != null && !isInterrupted()) {
            synchronized (queuedSpeakables) {
                if (queuedSpeakables.isEmpty()) {
                    fireQueueEmpty();
                    try {
                        queuedSpeakables.wait();
                    } catch (InterruptedException e) {
                        return;
                    }
                }

                queuedSpeakable = queuedSpeakables.remove();
            }

            if (processor != null) {
                try {
                    passSpeakableToMary(queuedSpeakable);
                } catch (BadFetchError e) {
                    listener.outputError(e);
                } catch (IOException e) {
                    LOGGER.error(e.getMessage(), e);
                    final BadFetchError error = new BadFetchError(
                            e.getMessage(), e);
                    listener.outputError(error);
                }
            }
        }
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("synthesis queue terminated");
        }
    }

    /**
     * The method that actually passes the speakable to Mary. According to the
     * speakable Type it calls the process method of the MaryClient with
     * inputType set to "TEXT" or "SSML" as appropriate It gets the answer from
     * the server at ByteArrayOutputStream out and then it calls queueAudio
     * method of MaryAudioFileOutput to play the audio. This method also fires
     * the events OutputStarted and OutputEnded to MarySynthesizedOutput as well
     * as error Events that inform the Browser that some exception occurred
     * either at process or queueAudio processes This method does not return
     * until the audio playing has completed during the process
     * 
     * @param speakable
     *            the speakable to be passed to Mary server
     * @throws BadFetchError
     *             if an error occurs while playing
     * @throws IOException
     *             error communicating with Mary
     */
    private void passSpeakableToMary(final SpeakableText speakable)
            throws BadFetchError, IOException {
        fireOutputStarted(speakable);
        out = new LineAudioFormatOutputStream();
        try {
            if (speakable instanceof SpeakableSsmlText) {
                final SpeakableSsmlText ssml = (SpeakableSsmlText) speakable;
                speakSsml(ssml, out);
                fireOutputEnded(speakable);
            } else {
                throw new BadFetchError("Unsupported Speakable type: '"
                        + speakable.getClass() + "'");
            }
        } finally {
            out.close();
            out = null;
        }
    }

    /**
     * Speaks the given SSML document.
     * 
     * @param ssml
     *            the SSML document to be synthesized by Mary
     * @param responseStream
     *            the output buffer to store Mary's response
     * @exception IOException
     *                error communicating with Mary
     */
    private void speakSsml(final SpeakableSsmlText ssml,
            final OutputStream responseStream)
            throws IOException {
        final SsmlDocument document = ssml.getDocument();
        final Speak speak = document.getSpeak();
        final String lang = speak.getXmlLang();
        final String text = document.toXml();
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("synthesizing '" + text + "'");
        }
        processor.process(text, "SSML", "AUDIO",
                lang,
                maryRequestParameters.get("audioType"),
                maryRequestParameters.get("voiceName"), responseStream,
                SERVER_TIMEOUT);
    }

    /**
     * All the notification events are passed initially. SynthesizedOutput and
     * from there to VoiceBrowser
     * 
     * @param outputListener
     *            the MarySynthesizedOutput object
     */
    public void addListener(final SystemOutputImplementationListener outputListener) {
        listener = outputListener;
    }

    /**
     * Removes the listener for system output events.
     * 
     * @param outputListener
     *            the MarySynthesizedOutput object to remove.
     */
    public void removeListener(final SystemOutputImplementationListener outputListener) {
        synchronized (listener) {
            listener = null;
        }
    }

    /**
     * Notifies the Listener that output queue is empty.
     */
    private void fireQueueEmpty() {
        final SystemOutputEvent event = new QueueEmptyEvent(output, null);
        fireOutputEvent(event);
    }

    /**
     * Notifies the MarySynthesizedOutput about the given event.
     * 
     * @param event
     *            the event.
     */
    private void fireOutputEvent(final SystemOutputEvent event) {
        listener.outputStatusChanged(event);
    }

    /**
     * Notifies the MarySynthesizedOutput that output has started.
     * 
     * @param speakable
     *            the current speakable.
     */
    private void fireOutputStarted(final SpeakableText speakable) {
        final SystemOutputEvent event = new OutputStartedEvent(output,
                null, speakable);
        fireOutputEvent(event);
    }

    /**
     * Notifies the MarySynthesizedOutput that output has ended.
     * 
     * @param speakable
     *            the current speakable.
     */
    private void fireOutputEnded(final SpeakableText speakable) {
        final SystemOutputEvent event = new OutputEndedEvent(output, null,
                speakable);
        fireOutputEvent(event);
    }

    /**
     * Sets the MaryClient object that will be used bu this Thread. to send
     * requests to Mary server
     * 
     * @param maryClient
     *            the connection to Mary.
     */
    public void setProcessor(final MaryClient maryClient) {
        processor = maryClient;
    }

    /**
     * The queueSpeakable method simply offers a speakable to the queue. it
     * notifies the synthesisQueue Thread and then it returns
     * 
     * @param speakable
     *            the speakable to offer
     */
    public void queueSpeakables(final SpeakableText speakable) {
        synchronized (queuedSpeakables) {
            queuedSpeakables.offer(speakable);
            queuedSpeakables.notify();
        }
    }

    /**
     * Removes all the speakables from the queue.
     */
    public void clearQueue() {
        synchronized (queuedSpeakables) {
            queuedSpeakables.clear();
        }
    }

    /**
     * Stops the currently playing output if barge-in is enabled and. Removes
     * from the queue the speakables for which barge-in is enabled
     * @param bargeInType the type of bargein to cancel
     */
    public void cancelOutput(final BargeInType bargeInType) {
        if ((queuedSpeakable == null)
                || !queuedSpeakable.isBargeInEnabled(bargeInType)) {
            return;
        }
        if (out != null) {
            out.cancel();
        }
        synchronized (queuedSpeakables) {
            final Collection<SpeakableText> skipped =
                    new java.util.ArrayList<SpeakableText>();
            for (SpeakableText speakable : queuedSpeakables) {
                if (speakable.isBargeInEnabled(bargeInType)) {
                    skipped.add(speakable);
                } else {
                    break;
                }
            }
            queuedSpeakables.removeAll(skipped);
        }
    }

    /**
     * Stops the currently playing output if barge-in is enabled.
     */
    public void cancelAudioOutput() {
        if ((queuedSpeakable != null)
                && (queuedSpeakable.isBargeInEnabled(BargeInType.SPEECH)
                        || queuedSpeakable.isBargeInEnabled(
                                BargeInType.HOTWORD))) {
            return;
        }
        if (out != null) {
            out.cancel();
        }
    }

    /**
     * Sets the parameters e.g AudioType, VoiceName, VoiceEffects required by
     * MaryClient to make a synthesis request to MaryServer.
     * 
     * @param parameters
     *            The HashTable that contains synthesis parameters and their
     *            values
     */
    public void setRequestParameters(final Map<String, String> parameters) {
        maryRequestParameters = parameters;
    }
}
