/*
 * JVoiceXML - A free VoiceXML implementation.
 *
 * Copyright (C) 2007-2019 JVoiceXML group - http://jvoicexml.sourceforge.net
 * The JVoiceXML group hereby disclaims all copyright interest in the
 * library `JVoiceXML' (a free VoiceXML implementation).
 * JVoiceXML group, $Date$, Dirk Schnelle-Walka, project lead
 *
 *  This library is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Library General Public
 *  License as published by the Free Software Foundation; either
 *  version 2 of the License, or (at your option) any later version.
 *
 *  This library is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 *  Library General Public License for more details.
 *
 *  You should have received a copy of the GNU Library General Public
 *  License along with this library; if not, write to the Free Software
 *  Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 *
 */

package org.jvoicexml.implementation.jtapi;

import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.util.Collection;

import javax.sound.sampled.AudioFormat;

import org.apache.log4j.Logger;
import org.jvoicexml.CallControlProperties;
import org.jvoicexml.ConnectionInformation;
import org.jvoicexml.callmanager.jtapi.JVoiceXmlTerminal;
import org.jvoicexml.callmanager.jtapi.JtapiConnectionInformation;
import org.jvoicexml.event.ErrorEvent;
import org.jvoicexml.event.error.NoresourceError;
import org.jvoicexml.implementation.CallControlImplementation;
import org.jvoicexml.implementation.CallControlImplementationEvent;
import org.jvoicexml.implementation.CallControlImplementationListener;
import org.jvoicexml.implementation.SystemOutputImplementation;
import org.jvoicexml.implementation.UserInputImplementation;
import org.jvoicexml.xml.srgs.ModeType;

/**
 * JTAPI based implementation of a {@link CallControlImplementation}.
 *
 * <p>
 * Audio output and user input is achieved via URIs.
 * </p>
 *
 * @author Hugo Monteiro
 * @author Renato Cassaca
 * @author Dirk Schnelle-Walka
 * @since 0.6
 */
public final class JtapiCallControlImplementation
    implements CallControlImplementation, CallControlImplementationListener {
    /** Logger instance. */
    private static final Logger LOGGER = Logger.getLogger(JtapiCallControlImplementation.class);

    /** Listener to this call control. */
    private final Collection<CallControlImplementationListener> callControlListeners;

    /** The JTAPI connection. */
    private JVoiceXmlTerminal terminal;

    /**
     * Constructs a new object.
     */
    public JtapiCallControlImplementation() {
        callControlListeners = new java.util.ArrayList<CallControlImplementationListener>();
    }

    /**
     * 
     * {@inheritDoc}
     */
    @Override
    public ModeType getModeType() {
        return ModeType.VOICE;
    }
    
    /**
     * {@inheritDoc}
     */
    @Override
    public void addListener(final CallControlImplementationListener listener) {
        synchronized (callControlListeners) {
            callControlListeners.add(listener);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void removeListener(final CallControlImplementationListener listener) {
        synchronized (callControlListeners) {
            callControlListeners.remove(listener);
        }
    }

    /**
     * {@inheritDoc}
     *
     * This implementation uses
     * {@link SystemOutputImplementation#getUriForNextSynthesisizedOutput()} to obtain a
     * URI that is being used to stream to the terminal.
     *
     * <p>
     * Although this terminal is the source where to stream the audio this
     * implementation makes no assumptions about the URI. In most cases this
     * will be related to the {@link UserInputImplementation} implementation. In the
     * simplest case this implementation <emph>invents</emph> a unique URI.
     * </p>
     */
    @Override
    public void play(Collection<SystemOutputImplementation> outputs,
            CallControlProperties props) throws NoresourceError, IOException {
        if (terminal == null) {
            throw new NoresourceError("No active telephony connection!");
        }
        if (outputs.isEmpty()) {
            throw new NoresourceError("no outputs provided");
        }
        final URI uri;
//        try {
            // TODO add a more generic interface here
            uri = null;//output.getUriForNextSynthesisizedOutput();
//        } catch (URISyntaxException e) {
//            throw new IOException(e.getMessage(), e);
//        }
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("playing URI '" + uri + "'");
        }
        terminal.play(uri, null);
    }

    /**
     * {@inheritDoc}
     *
     * This implementation uses {@link UserInputImplementation#getUriForNextSpokenInput()}
     * to obtain a URI to stream from the terminal to the spoken input device.
     */
    @Override
    public void record(Collection<UserInputImplementation> inputs,
            CallControlProperties props) throws NoresourceError, IOException {
        if (terminal == null) {
            throw new NoresourceError("No active telephony connection!");
        }
        if (inputs.isEmpty()) {
            throw new NoresourceError("no inpus provided");
        }

        final URI uri;
//        try {
            // TODO add a more generic interface here
            uri =null;// input.getUriForNextSpokenInput();
//        } catch (URISyntaxException e) {
//            throw new IOException(e.getMessage(), e);
//        }
        // TODO Do the actual recording.
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("recording to URI '" + uri + "'...");
        }
        // TODO Move the code from the FIA to here.
        terminal.record(uri, null);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public AudioFormat getRecordingAudioFormat() {
        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void startRecording(final UserInputImplementation input,
            final OutputStream stream, final CallControlProperties props)
            throws IOException, NoresourceError {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void stopRecording() throws NoresourceError {
        if (terminal == null) {
            throw new NoresourceError("No active telephony connection!");
        }

        terminal.stopRecord();
    }

    /**
     * Inform the {@link CallControlImplementationListener} about an answered event.
     */
    protected void fireAnswerEvent() {
        final Collection<CallControlImplementationListener> tmp = new java.util.ArrayList<CallControlImplementationListener>(
                callControlListeners);
        final CallControlImplementationEvent event = new CallControlImplementationEvent(this,
                CallControlImplementationEvent.ANSWERED);
        for (CallControlImplementationListener listener : tmp) {
            listener.telephonyCallAnswered(event);
        }
    }

    /**
     * Inform the {@link CallControlImplementationListener} about a play stopped event.
     * 
     * @param uri
     *            destination URI of the trasfer.
     */
    protected void fireTransferEvent(final String uri) {
        final Collection<CallControlImplementationListener> tmp = new java.util.ArrayList<CallControlImplementationListener>(
                callControlListeners);
        final CallControlImplementationEvent event = new CallControlImplementationEvent(this,
                CallControlImplementationEvent.TRANSFERRED, uri);
        for (CallControlImplementationListener listener : tmp) {
            listener.telephonyCallTransferred(event);
        }
    }

    /**
     * Inform the {@link CallControlImplementationListener} about a play started event.
     */
    protected void firePlayEvent() {
        final Collection<CallControlImplementationListener> tmp = new java.util.ArrayList<CallControlImplementationListener>(
                callControlListeners);
        final CallControlImplementationEvent event = new CallControlImplementationEvent(this,
                CallControlImplementationEvent.PLAY_STARTED);
        for (CallControlImplementationListener listener : tmp) {
            listener.telephonyMediaEvent(event);
        }
    }

    /**
     * Inform the {@link CallControlImplementationListener} about a play stopped event.
     */
    protected void firePlayStoppedEvent() {
        final Collection<CallControlImplementationListener> tmp = new java.util.ArrayList<CallControlImplementationListener>(
                callControlListeners);
        final CallControlImplementationEvent event = new CallControlImplementationEvent(this,
                CallControlImplementationEvent.PLAY_STOPPED);
        for (CallControlImplementationListener listener : tmp) {
            listener.telephonyMediaEvent(event);
        }
    }

    /**
     * Inform the {@link CallControlImplementationListener} about a record started event.
     */
    protected void fireRecordStartedEvent() {
        final Collection<CallControlImplementationListener> tmp = new java.util.ArrayList<CallControlImplementationListener>(
                callControlListeners);
        final CallControlImplementationEvent event = new CallControlImplementationEvent(this,
                CallControlImplementationEvent.RECORD_STARTED);
        for (CallControlImplementationListener listener : tmp) {
            listener.telephonyMediaEvent(event);
        }
    }

    /**
     * Inform the {@link CallControlImplementationListener} about a record stopped event.
     */
    protected void fireRecordStoppedEvent() {
        final Collection<CallControlImplementationListener> tmp = new java.util.ArrayList<CallControlImplementationListener>(
                callControlListeners);
        final CallControlImplementationEvent event = new CallControlImplementationEvent(this,
                CallControlImplementationEvent.RECORD_STOPPED);
        for (CallControlImplementationListener listener : tmp) {
            listener.telephonyMediaEvent(event);
        }
    }

    /**
     * Inform the {@link CallControlImplementationListener} about a hangup event.
     */
    protected void fireHangedUpEvent() {
        final Collection<CallControlImplementationListener> tmp = new java.util.ArrayList<CallControlImplementationListener>(
                callControlListeners);
        final CallControlImplementationEvent event = new CallControlImplementationEvent(this,
                CallControlImplementationEvent.HUNGUP);
        for (CallControlImplementationListener listener : tmp) {
            listener.telephonyCallHungup(event);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void activate() {
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void close() {
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getType() {
        return "jtapi";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void open() throws NoresourceError {
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void passivate() {
        callControlListeners.clear();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void connect(final ConnectionInformation info) throws IOException {
        final JtapiConnectionInformation jtapiInfo = (JtapiConnectionInformation) info;
        terminal = jtapiInfo.getTerminal();
        terminal.addListener(this);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void disconnect(final ConnectionInformation info) {
        final JtapiConnectionInformation jtapiInfo = (JtapiConnectionInformation) info;
        terminal = jtapiInfo.getTerminal();
        terminal.hangup();
        terminal.removeListener(this);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void stopPlay() throws NoresourceError {
        if (terminal == null) {
            throw new NoresourceError("No active telephony connection!");
        }

        terminal.stopPlay();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void transfer(final String dest) throws NoresourceError {
        if (terminal == null) {
            throw new NoresourceError("No active telephony connection!");
        }

        LOGGER.info("transferring to '" + dest + "'...");
        terminal.transfer(dest);
        fireTransferEvent(dest);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void hangup() {
        if (terminal == null) {
            return;
        }
        terminal.hangup();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isBusy() {
        if (terminal == null) {
            return false;
        }

        return terminal.isBusy();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void telephonyCallAnswered(final CallControlImplementationEvent event) {
        final Collection<CallControlImplementationListener> tmp = new java.util.ArrayList<CallControlImplementationListener>(
                callControlListeners);
        for (CallControlImplementationListener listener : tmp) {
            listener.telephonyCallAnswered(event);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void dtmfInput(char dtmf) {
        // TODO Auto-generated method stub
        
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void telephonyCallHungup(final CallControlImplementationEvent event) {
        final Collection<CallControlImplementationListener> tmp = new java.util.ArrayList<CallControlImplementationListener>(
                callControlListeners);
        for (CallControlImplementationListener listener : tmp) {
            listener.telephonyCallHungup(event);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void telephonyMediaEvent(final CallControlImplementationEvent event) {
        final Collection<CallControlImplementationListener> tmp = new java.util.ArrayList<CallControlImplementationListener>(
                callControlListeners);
        for (CallControlImplementationListener listener : tmp) {
            listener.telephonyCallAnswered(event);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void telephonyCallTransferred(final CallControlImplementationEvent event) {
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void telephonyError(final ErrorEvent error) {
        final Collection<CallControlImplementationListener> tmp = new java.util.ArrayList<CallControlImplementationListener>(
                callControlListeners);
        for (CallControlImplementationListener listener : tmp) {
            listener.telephonyError(error);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isActive() {
        // TODO add a real implementation
        return terminal.isBusy();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Collection<ModeType> getSupportedOutputModeTypes() {
        // TODO add a cache for the modes
        final Collection<ModeType> modes =
                new java.util.ArrayList<ModeType>();
        modes.add(ModeType.DTMF);
        return modes;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Collection<ModeType> getSupportedInputModeTypes() {
        // TODO add a cache for the modes
        final Collection<ModeType> modes =
                new java.util.ArrayList<ModeType>();
        modes.add(ModeType.VOICE);
        modes.add(ModeType.DTMF);
        return modes;
    }
}