/*
 * JVoiceXML - A free VoiceXML implementation.
 *
 * Copyright (C) 2012-2019 JVoiceXML group - http://jvoicexml.sourceforge.net
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

package org.jvoicexml.implementation.kinect;

import org.jvoicexml.event.error.NoresourceError;
import org.jvoicexml.implementation.ResourceFactory;
import org.jvoicexml.implementation.UserInputImplementation;
import org.jvoicexml.xml.srgs.ModeType;

/**
 * A resource factory to produce {@link KinectUserInputImplementation} objects.
 * @author Dirk Schnelle-Walka
 * @since 0.7.6
 *
 */
public final class KinectUserInputImplementationFactory implements ResourceFactory<UserInputImplementation> {
    /** Type of the created resources. */
    private String type;

    /**
     * Constructs a new object.
     */
    public KinectUserInputImplementationFactory() {
        type = "kinect";
    }

    /**
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
    public Class<UserInputImplementation> getResourceType() {
        return UserInputImplementation.class;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public UserInputImplementation createResource() throws NoresourceError {
        final KinectUserInputImplementation input = new KinectUserInputImplementation();
        input.setType(type);
        return input;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getInstances() {
        return 1;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getType() {
        return type;
    }

    /**
     * Sets the type.
     * @param value new value for the type.
     */
    public void setType(final String value) {
        type = value;
    }
}